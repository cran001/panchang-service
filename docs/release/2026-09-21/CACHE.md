# Persistent calculation cache

`publication/.../CalculationCache.kt` stores the full calculated year in an operator-owned
local directory. API and publisher executables opt in with `PANCHANG_CALC_CACHE_DIR`.
Without it, they calculate directly. Both paths use the same current publication policy.

The SHA-256 key covers exact latitude/longitude/elevation/IANA zone, year, tradition, cache
format/integrity revision, cross-year policy, compiled calculation/core/rule-catalog/ephemeris
artifacts, coefficient and Delta-T data, wire/gazetteer/publication artifacts, Kotlin and both
serialization runtime artifacts, JVM identity and that zone's tzdb version. Display names,
query spelling and nearby place labels cannot change the calculation point. Request-specific
location provenance is reconstructed for each caller, not borrowed from a prior request.

Each entry contains typed year results, computation time, full-year result fingerprint,
calculation versions and explicit previous-December/following-January context. Hits verify the
expected key, payload checksum, typed point/year/tradition, context and recalculated result
fingerprint. No approved response, review journal snapshot or permission bit is stored.

Misses coordinate through a bounded set of JVM locks plus a per-key OS file lock. The miss is
rechecked inside the lock. Complete bytes are forced to a sibling temporary file and atomically
renamed; there is no non-atomic fallback. Readers never use `.pending` files. Corrupt entries,
unsupported locking/rename, inaccessible storage and oversize entries fail without returning
guidance. An absent entry is recalculated; a corrupted entry requires investigation or removal
by the operator while the cache is offline. Old version entries cannot match a new key.

The checksums detect accidental corruption; they do not authenticate a hostile storage writer
who can replace both payload and checksum. Use a service-owned directory outside the web root.
The implementation targets cooperating processes on one local filesystem. Multi-host network
filesystems, object stores and distributed locking are not validated adapters. No such resources
were provisioned. Capacity/retention must be managed operationally; do not delete lock files or
perform retention while writers are active. Losing calculation cache files requires recalculation,
not permission recovery. Production approval storage needs stronger independent controls.

Every HTTP read and file-export preparation calls current policy; HTTP rechecks before returning
and the publisher rechecks before writing and before atomic completion. Approval-store exceptions
become `INVALID_RECORDS` with null guidance. Revoke/supersede/changed disputes cannot inherit a
warm cache's previous permission. Test identities stay in isolated test directories, never in
production artifacts. Ordinary file hosting is not a revocation-aware public read adapter.

Verification is recorded separately:

- `CalculationCacheTest`: restart reuse, request provenance, January carryover, concurrent
  instances, all key dimensions/versions, changed-point miss, corruption, interrupted work,
  current revocation/supersession/disputes/outage, approved-empty versus withheld, disabled activation.
- `PublicationControlsApiTest`: policy-controlled HTTP and files, including warm-cache revocation
  and refusing an export prepared before revocation. Existing signed-journal tests remain intact.
- `smoke/checks.json`: separate JVM restarts, four simultaneous JVMs with one computation,
  actual artifact-byte version invalidation in an isolated JAR copy, corrupt-entry refusal,
  real Netty HTTP restart and API/publisher cache sharing. Cold/warm/concurrent timings are
  observed local measurements, not throughput guarantees or cross-host benchmarks.

Public responses stay `no-store`. Server revocation does not retract downloaded files.
