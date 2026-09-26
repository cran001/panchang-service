# Vendored source data

These are the published coefficient tables for the two analytical theories this service
uses for high-precision Sun and Moon positions, plus the GeoNames archives the place
gazetteer is derived from. They are committed rather than fetched at build time on purpose:
the service must still build in 2040, and CDS and GeoNames being reachable then is not
something this project can control. The app this service replaces already learned that
lesson the expensive way — it resolved places through a third-party host that is now dead,
which is precisely why nothing here is fetched at runtime.

`MANIFEST.sha256` lists a SHA-256 and byte size for every file. Regenerate and diff it if
you ever suspect a file has been touched — these tables are input data, and a single
mistyped coefficient is exactly the kind of error that produces plausible-looking output.

Verify it from this directory with:

```bash
grep -v '^#' MANIFEST.sha256 | awk 'NF==3 {print $1"  "$3}' | sha256sum -c
```

The `awk` is not optional. The manifest carries three columns (hash, size, path) so that a
truncated file is caught even in the astronomically unlikely event of a hash collision, and
that third column means **`sha256sum -c MANIFEST.sha256` does not work** — it reads
`size path` as one filename and reports all 43 files FAILED, which looks like catastrophic
corruption and is not. Expect `43 OK, 0 FAILED`.

**Nothing in this directory may be edited.** If a table is wrong, it is wrong upstream and
the fix is a new retrieval with an updated manifest, not a local patch.

---

## VSOP87D — Earth heliocentric position

| | |
|---|---|
| Source | `https://cdsarc.cds.unistra.fr/ftp/VI/81/` (CDS VizieR catalogue **VI/81**) |
| Theory | VSOP87, Bretagnon P., Francou G., *Astron. Astrophys.* **202**, 309 (1988) |
| Retrieved | 2026-08-01T12:51:34Z, HTTP 200 |
| Files | `vsop87_VSOP87D.ear`, `vsop87_vsop87.chk`, `vsop87_vsop87.txt`, `vsop87_ReadMe` |

Variant **D** is heliocentric spherical (L, B, R) referred to the **mean equinox of date**,
which is the variant that needs the least post-processing for our purposes. The Sun's
geocentric longitude is the Earth's heliocentric longitude + 180° (then corrected to
*apparent* by applying nutation and aberration — see `Ephemeris`' contract).

`VSOP87D.ear` covers L, B and R, each as series in powers of T (T^0 … T^5). The header line
of each block states the variable, the power of T, and the term count, e.g.

```
 VSOP87 VERSION D4    EARTH     VARIABLE 1 (LBR)       *T**0    559 TERMS ...
```

Each term line ends with the three numbers that matter — **A, B, C** — evaluated as
`A · cos(B + C·T)`. Parse by fixed columns, not by whitespace splitting: some fields run
together in the wider files.

### Validation

`vsop87_vsop87.chk` contains the **authors' own published check values**, including 10
blocks for `VSOP87D  EARTH` at 100-year intervals. For example, at JD 2451545.0 (J2000):

```
 l   1.7519238681 rad       b   -.0000039656 rad       r    .9833276819  au
```

Assert against these directly. They are independent published ground truth — not
self-consistency — and they are the reason this implementation can be checked without any
network access at all. They complement, and are checked separately from, the JPL Horizons
differential tests.

---

## ELP 2000-82B — Lunar theory

| | |
|---|---|
| Source | `https://cdsarc.cds.unistra.fr/ftp/VI/79/` (CDS VizieR catalogue **VI/79**) |
| Theory | Chapront-Touzé M., Chapront J., *Astron. Astrophys.* **190**, 342 (1988); **124**, 50 (1983) |
| Retrieved | 2026-08-01T12:51:34Z, HTTP 200 |
| Files | `elp2000/ELP1` … `elp2000/ELP36`, `elp2000_ReadMe` |

Per the catalogue ReadMe, the series are ELP2000-82 with constants **fitted to the JPL
DE200/LE200 numerical integration** and arguments from ELP 2000-85. That fit is why this is
worth implementing in full rather than settling for the truncated Meeus Ch. 47 series: the
truncation is what puts the current Android engine at roughly 0.05° — about six minutes of
tithi timing error, which is a material fraction of a parana window.

The 36 files are grouped by perturbation class; the first line of each names it:

- `ELP1`–`ELP3` — main problem, longitude / latitude / distance
- `ELP4`–`ELP9` — Earth figure perturbations
- `ELP10`–`ELP15` — planetary perturbations
- `ELP16`–`ELP21` — tidal, Moon figure, relativistic
- `ELP22`–`ELP36` — solar eccentricity and higher-order terms

`ELP1`–`ELP3` alone carry the bulk of the amplitude; the remainder are needed for the
arcsecond-level accuracy this project is targeting. Implement them in that order so partial
progress is still measurable.

---

## Licensing

Both theories are published scientific results distributed by CDS without a licence
requiring attribution, royalties, or source disclosure. This is the reason they were chosen
over Swiss Ephemeris: SwissEph is AGPL-3, and its network clause would oblige us to publish
the whole service's source — including the sampradaya rules, which are this product's actual
asset. The commercial SwissEph licence (~CHF 750) avoids that but makes the product
permanently dependent on a third party's future licensing decisions.

Citing Bretagnon & Francou and Chapront-Touzé & Chapront in any published accuracy claim is
the correct scholarly practice regardless of whether a licence compels it.

---

## GeoNames — place gazetteer

| | |
|---|---|
| Source | `https://download.geonames.org/export/dump/` |
| Files | `geonames/cities15000.zip`, `geonames/IN.zip` |
| Retrieved | 2026-08-01, HTTP 200 |
| Licence | **CC BY 4.0** — `https://creativecommons.org/licenses/by/4.0/` |
| Attribution | `Place data © GeoNames (https://www.geonames.org/), used under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).` |

`cities15000.zip` is every populated place on Earth with a population of 15 000 or more.
`IN.zip` is the full GeoNames country file for India; the gazetteer ingest keeps only the
rows where `feature_class == "A" && feature_code == "ADM2"`, which is India's districts.

**Why the districts come from the country file and not from `admin2Codes.txt`.** The
`admin2Codes.txt` table is the obvious-looking source for districts and is a hundredth the
size, but it carries *no coordinates* — only code, name and geonameid. A gazetteer whose
district rows have no position cannot answer the one question the module exists to answer.
So the 15.7 MB country file is downloaded and 99.9% of it is discarded. That is the correct
trade.

### The `elevation` and `dem` columns are deliberately NOT ingested

This is a decision. It is not an oversight, and it is not a TODO.

GeoNames ships an `elevation` column (metres, where surveyed) and a `dem` column (SRTM
digital elevation model). The gazetteer ingest reads neither, and the derived
`gazetteer-v1.tsv` therefore has no elevation column at all — there is nothing for later
code to reach for by accident.

The reason is that elevation is not a free accuracy improvement here. It enters the
horizon-dip term of sunrise and sunset, and sunrise is the instant against which tithi is
tested to decide **which civil day a fast is kept on**. Every reference calendar this
service is validated against — the published Gaudiya calendars, and the ground truth in
`verify/golden/` — is computed at sea level. Feeding a district's true altitude into dip
would move our sunrise away from those references by a knowable amount, at exactly the
high-altitude districts for which no reference data exists to tell us whether the move was
an improvement or an error. Dip grows as the square root of height, so the districts where
it would matter most are the ones we can check least.

An unvalidatable change to a religiously-consequential quantity is not an improvement. If
and when reference data for altitude sites exists, this becomes a real decision with real
evidence behind it; until then `Place.toGeoLocation()` defaults `elevationMeters` to zero
and a caller who has a defensible figure must pass it explicitly and knowingly.

`alternatenames` (column 4) is also dropped. It is more than half the input by volume, its
quality is very uneven, and fuzzy multilingual matching is not what a module designed to
*refuse* to guess should be doing.

### Why CC BY, specifically

Attribution-only, no share-alike, no network clause. The alternatives with copyleft that
reaches through a network service would have obliged us to publish the sampradaya rule set,
which is the same reason VSOP87/ELP2000 were chosen over Swiss Ephemeris above.

The whole cost of that choice is the attribution string, so the attribution string is
load-bearing: it is a constant in `Gazetteer.ATTRIBUTION`, it is written into the header of
the derived table, and `GazetteerProvenanceTest` fails if it stops being present in the
shipped artifact. Do not "tidy" it away.

### Derived artifact

`gazetteer/src/main/resources/org/panchang/gazetteer/gazetteer-v1.tsv` is generated from
these two archives by `./gradlew :gazetteer:ingest` and is committed. Its `#`-prefixed
header records the SHA-256 and byte size of both source archives, the exact filter applied
to each, and the kept/dropped row counts with a named reason for every drop.
`GazetteerProvenanceTest` asserts those hashes still equal the ones in `MANIFEST.sha256`,
so re-vendoring an archive without re-running the ingest is a build failure rather than a
silent inconsistency.
