# JPL Horizons reference sample — retrieved 2026-08-01

Apparent geocentric ecliptic longitude of the Moon and the Sun, 365 daily samples for each
of three epochs (1950, 2026, 2100). Columns: `jd_ut,longitude_deg`.

| | |
|---|---|
| Source | `https://ssd.jpl.nasa.gov/api/horizons.api` |
| Retrieved | 2026-08-01, HTTP 200 |
| Center | `500@399` (geocentric) |
| Target | `301` (Moon), `10` (Sun) |
| Quantity | `31` — `ObsEcLon`/`ObsEcLat` |
| Time argument | `CAL_FORMAT='JD'` → column header `Date_________JDUT`, i.e. **UT**, not TT |

Horizons defines quantity 31 as:

> Observer-centered **IAU76/80 ecliptic-of-date** longitude and latitude of the target
> centers' **apparent** position, with light-time, gravitational deflection of light, and
> stellar aberrations.

That is exactly the convention `Ephemeris` specifies — apparent, of date, IAU 1980 nutation,
which is the same nutation model as Meeus Table 22.A. The two are therefore directly
comparable with no frame conversion.

## Regenerating

```bash
curl -sS -G "https://ssd.jpl.nasa.gov/api/horizons.api" \
  --data-urlencode "format=text"      --data-urlencode "COMMAND='301'" \
  --data-urlencode "OBJ_DATA='NO'"    --data-urlencode "MAKE_EPHEM='YES'" \
  --data-urlencode "EPHEM_TYPE='OBSERVER'" --data-urlencode "CENTER='500@399'" \
  --data-urlencode "START_TIME='2026-01-01 00:00'" \
  --data-urlencode "STOP_TIME='2026-12-31 00:00'" \
  --data-urlencode "STEP_SIZE='1d'"   --data-urlencode "QUANTITIES='31'" \
  --data-urlencode "CAL_FORMAT='JD'"
# then take the block between $$SOE and $$EOE, columns 1 and 2.
# COMMAND='10' for the Sun.
```

These files are committed rather than left as instructions because a measured accuracy claim
that cannot be re-checked byte-for-byte is not a measurement. See `docs/accuracy-baseline.md`
for what was measured against them.
