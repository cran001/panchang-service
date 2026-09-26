# Versioned contract and Android handoff

This task changes only the service. Android and Content Hub were not changed or tested.

Use `GET /v2/day/iskcon/YYYY-MM-DD` and `GET /v2/calendar/iskcon/YYYY` with the existing
`lat`, `lon`, `tz`, optional elevation, or place selectors. Location resolution is unchanged.
`/v2/health`, `/v2/meta`, `/v2/places`, and `/v2/places/nearest` expose discovery.
The v1 discovery routes remain available; v1 day/calendar now return **410 Gone** with
`API_VERSION_RETIRED`, `guidance: WITHHELD`, a replacement route template, and no calendar.
There is no redirect that could feed schema 2 into an unaware v1 consumer.

The public envelope and each guidance root explicitly say `schemaVersion: 2`. The separate
`wireSchemaVersion` in meta describes the internal diagnostic DTOs, not public publication.
All JSON HTTP responses, including refusals/errors, send `Cache-Control: no-store`.

Consumer requirements:

1. Inspect `ekadashiYear.publication` and `yearResolution.publication` separately.
   Display only the field whose state is `APPROVED` and guidance is `AVAILABLE`.
2. Preserve `observances: null` / `events: null` as withheld. An approved `[]` means only
   no calculated event in that scope. Never convert null to an empty calendar or substitute dates.
3. Preserve fasting date and Parana date separately. January 1 can contain the preceding
   December 31 fast. Use the supplied offset time and exact point/elevation/IANA zone.
4. Treat 410 as a required client migration, 422 as unsupported scope, and an error/outage
   as unavailable guidance. Clear any locally scheduled advice whose current approval is absent.
5. Recheck approval before presenting time-sensitive advice or scheduling notifications.
   Server revocation cannot retract a downloaded file or guarantee a disconnected client acts on it.

Public file export writes `v2/<key>/calendar.json`, `year-resolution.json`, and
`ekadashi-year.json`. Old `v1/<key>/...json` files contain migration refusals only.
Legacy array export remains refused. The separate review-only publisher preserves its diagnostic
`review/v1/` layout and delivery regression checks; it is not a public compatibility adapter.
An HTTP host for files must map migration documents to 410, enforce no-store, and evaluate
CURRENT approval before every read. Merely hosting the generated files is insufficient for
revocation. No static-serving production adapter was activated here.

`PANCHANG_CALC_CACHE_DIR` enables shared persistent yearly calculation reuse in the actual API
and publisher executables. Both can use one directory on a filesystem with reliable local
locks/atomic rename. `production.env.example` is an inactive template. With the variable unset,
calculations run directly; approval checks are identical in both modes. No application exposes
an environment switch, HTTP route, or cached record that grants production approval.

Production approval identity, durable journal and external monotonic rollback protection are
still absent. The local signed journal remains a local workflow. Choosing those providers and
trusted identities is an owner/operator decision under the existing policy, not something a
cache implementation or AI-assisted report can authorize.
