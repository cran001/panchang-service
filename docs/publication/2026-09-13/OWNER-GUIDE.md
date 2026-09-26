# Panchang approval and publication controls

Implementation began 13 September 2026. No actual human review, owner approval, production
identity, deployment, commit or push was created. Production approval reads and writes are
disabled by default. Local signatures authorize only the local review workflow.

## What the public service now does

The day and year routes use one `PublicationService` and `PublicationPolicy`. They calculate a
whole year, including the repaired January carryover, before selecting a day. Approval cannot
change the calculation or substitute a different calendar. An approval must match the exact
point, elevation, IANA timezone, tradition, year, results, calculation/data/rendering versions,
reference policy, dispute-registry revision, date coverage and fields.

There are separate facts for calculation, evidence outcomes, qualified review and the owner's
publication decision. Existing `VERIFIED`, `CONFIRMED` and `INFERRED` classifications remain
diagnostic classifications. They neither create a review nor confer publishing authority.

Each public root has `schemaVersion: 2`, `publication.state`, `publication.guidance` and
`absenceMeaning`. States include `PENDING_REVIEW`, `DISPUTED`, `MISSING_EVIDENCE`, `UNSUPPORTED`,
`APPROVED`, `REVOKED`, `SUPERSEDED`, `REJECTED` and `INVALID_RECORDS`. Only `APPROVED` releases
the matching field group. An unavailable `observances` or `events` value is **null**, never an
empty array. It means guidance was withheld. Only an approved empty array indicates that the
calculation found no event in that scope; it is not a claim of exhaustive religious knowledge.
Calculation `unresolved` entries remain present when the event group is approved.

The field groups are `EVENTS` and `OBSERVANCES`. An unresolved issue in one group withholds
that whole group for the requested date range. This intentionally trades availability for
clear, conservative behavior. A day unaffected by a known dispute can be approved separately
from a disputed yearly group. `DAILY_ASTRONOMY` is recognized in the dispute registry but is
not supported for publication by this event-focused result schema.

Public HTTP responses use `Cache-Control: no-store`. No review, approval or admin HTTP route
exists. Body fields, query parameters, bearer strings and reviewer names cannot enable one.

## Compatibility and static files

This is a breaking contract change at the existing `/v1/day` and `/v1/calendar` routes and the
existing `v1/<site>/...json` file paths. The roots explicitly identify schema 2. Consumers must
inspect publication state and handle null guidance before rendering a calendar or setting an
alarm. They must not turn null into an empty calendar or fall back to another source. Android
and Content Hub have not been changed or tested against this new contract.

The public publisher writes identical policy-controlled roots at the existing structured paths
and a full envelope at `v2/<site>/calendar.json`. `publication-status.json` counts approved and
withheld sites. In the older manifest fields, `published` counts structured response documents,
including withholding documents; it does **not** count human-approved calendars.

All **public legacy publication is refused**, including otherwise approved scopes. Its existing
array format and reader cannot express approval, withholding, revocation or partial guidance.
This is an explicit additional format restriction, not a different religious decision. No
`legacy/` files or synthetic UTC fallback are produced. The old renderer is named
`ReviewFeedPublisher`; it can write only inside a marked `review/` directory. Its rounding,
January attachment and daylight regressions remain tested as diagnostic behavior.

Public output must be a fresh directory. The writer checks current approval before writing and
again before completion, stages into a `.publication-pending-*` sibling, and atomically moves the
finished directory with a `COMPLETE` marker. It refuses tampered in-memory content and refuses
to reuse directories containing old files. Incomplete staging directories are not release
artifacts. This does not withdraw already copied or deployed files. Serving immutable files
with effective revocation requires a separate revocation-aware deployment/serving policy.

`panchang-calc` is now explicitly a development/review CLI: JSON and human output are marked
calculated diagnostics, not approved guidance. It is not a public export command.

## Maintained disputes and review questions

`publication/src/main/resources/org/panchang/publication/disputes.json` contains 47 cases
transcribed from the preserved audit, each with exact coordinates, elevation, timezone, dates,
affected field details, observed revision and hashed evidence links. They preserve Vrindavan,
Auckland October/April, separate Moscow/Sydney classifications, Ahmedabad's bound tie, the
three open-ended/reference-only windows, daily-label disagreements, stale-DST exclusions and
the separate Reykjavik sunrise and multiple-moonset issues. DELIVERY-01/02 are repaired
software defects and are not misrepresented as unresolved religious findings.

Version applicability is explicitly `OBSERVED_AND_UNREVIEWED_SUCCESSORS`: changing code does
not silently make an unresolved audit case disappear. A changed registry invalidates prior
approval. Narrowing/removing a case requires maintained evidence and qualified review of its
successor scope. An issue at the fast date also follows that decision to its Parana day.

Seven broader qualified-review questions separately cover rare nakshatra qualifiers, Unmilani,
fast-ending anchors, catalog interpretations, high-latitude applicability, reference coverage
and regional catalogs. Their applicability is a whole field/tradition scope, not an invented
date-specific disagreement. A completed review must explicitly list the applicable question
IDs it addressed, with evidence and reasoning, including an explanation when a concern is not
applicable to the proposed scope. The 47 cases are not an exhaustive uncertainty list.

A known case remains blocked after a plain APPROVE record. A qualified completed review must
identify its dispute ID, `RESOLVED` decision, explanation and hashed evidence, and the owner
must cite that review in the exact bundle's approval. `WITHHOLD` is a valid reviewed exception
but keeps guidance withheld. It never becomes a timing override. This implementation makes
no religious decision and supports no AI/RAG approval.

## Local owner workflow

Run from `D:\projects\panchang-service` with Java 21 and the Gradle wrapper. The local command
requires operator-provided Ed25519 keys. It does not create keys, reviewers, accounts, signatures
or approvals on the owner's behalf. Nothing in `publication/examples` is an active approval.

### 1. Prepare a review bundle

Make a request JSON with `members` (canonical points, tradition, year, inclusive coverage and
field groups), a proposed `referencePolicy`, and `evidence`. Evidence entries contain a local
file link, SHA-256 and a stated outcome. Evidence outcomes describe validation, not approval.
The example deliberately contains synthetic evidence and must not be treated as a real policy.

```powershell
.\gradlew.bat :publication:run --args="prepare publication/examples/prepare.TEST-ONLY.json build/local-review-example" --console=plain
```

The new directory contains `bundle.json`, its fingerprint, calculated review-only documents and
the maintained dispute registry. The canonical point ID is derived from the exact coordinates,
elevation and zone; a submitted display name cannot redirect its approval to another point.
The coverage can select part of a year and specific field groups; the result digest deliberately
binds the full computed year. Changes elsewhere in that year conservatively require re-review.
Previous December and following January context are explicitly recorded, and relevant carryover
records are included in the calculated result. Multiple points may share one bundle decision.

### 2. Configure local trust and record qualified review

The owner must supply a protected local `trust.json` outside the repository. It has:

```json
{
  "environment": "LOCAL_REVIEW_ONLY",
  "ownerKeyId": "<owner-controlled-key-id>",
  "keys": [
    {"id": "<owner-controlled-key-id>", "publicKeyBase64": "<base64-X509-Ed25519-public-key>", "qualifiedTraditions": []},
    {"id": "<qualified-reviewer-key-id>", "publicKeyBase64": "<base64-X509-Ed25519-public-key>", "qualifiedTraditions": ["iskcon"]}
  ]
}
```

These placeholders are intentionally unusable. No key ID is trusted because it says “owner”,
“reviewer” or “moderator”. The pinned public key must verify the command's signature. The
owner does not automatically become a qualified reviewer; reviewers cannot publish. No
moderator role or permissions are defined.

Fill a copy of `publication/examples/review.template.json` with the real bundle path, signer
key ID, human decision, evidence, `reviewedQuestionIds` and any scoped dispute resolutions.
Use `PENDING` or `DISPUTED` until the reviewer has actually completed the review. A completed
review uses `COMPLETED`. Use local private-key files in DER PKCS#8 format; keep them outside
the repository and restrict their filesystem access.

```powershell
.\gradlew.bat :publication:run --args="record C:/private/trust.json C:/private/panchang-journal C:/private/review-command.json C:/private/reviewer.pk8" --console=plain
```

The command adds sequence, time and record ID, verifies the configured identity, archives
evidence by hash, checks permissions and appends the signed record. Evidence URLs must first
be independently saved locally; the command does not fetch references or infer their contents.
Copy the printed review record ID for the owner's decision.

### 3. Approve an exact local scope

Fill a copy of `approve.template.json`, replace all placeholders and synthetic evidence, and
cite completed qualified `reviewRecordIds`. The owner signs using their separate key:

```powershell
.\gradlew.bat :publication:run --args="record C:/private/trust.json C:/private/panchang-journal C:/private/approve-command.json C:/private/owner.pk8" --console=plain
.\gradlew.bat :publication:run --args="evaluate C:/private/trust.json C:/private/panchang-journal build/local-review-example/bundle.json" --console=plain
```

`evaluate` recomputes and checks the current result/version scope. A signed approval can still
be blocked by unresolved disputes, missing question review or changed inputs/evidence policy.
A reopened qualified review cannot be bypassed by citing an older completed review.
The local journal is **not** loaded by the production API or publisher. Production activation
requires the decisions below and a separately authorized implementation of the trusted adapter.

### 4. Revoke, supersede and inspect history

Use `revoke.template.json` with `targetRecordId` equal to the approval ID and the same bundle.
The owner signs it using the same `record` command. Revocation blocks the next policy read and
also blocks a previously prepared export at its write/completion checks. To supersede, append
a SUPERSEDE record pointing to the old approval, then prepare/review/approve the replacement
bundle. Supersession alone does not authorize a replacement. REJECT is also an owner action.

```powershell
.\gradlew.bat :publication:run --args="record C:/private/trust.json C:/private/panchang-journal C:/private/revoke-command.json C:/private/owner.pk8" --console=plain
.\gradlew.bat :publication:run --args="history C:/private/trust.json C:/private/panchang-journal" --console=plain
```

History is a sequence of signed, hash-linked immutable records; old approvals and evidence are
not overwritten. A checkpoint detects missing/torn tails. Writers lock the journal; partial
records, unknown files, invalid signatures, changed evidence or broken chains fail closed.
Inspect a damaged journal and recover only from known-good evidence; do not delete a broken
tail to “make it work.” No automatic repair or rollback operation is provided.

## Decisions still required before production

1. The owner must approve the real publishing identity, key custody/rotation/recovery and a
   production identity adapter. Local file possession is not a deployed authenticated account.
2. Choose durable approval/evidence storage, access controls, backups and an external monotonic
   checkpoint. A hostile rollback of both a local journal and its local checkpoint cannot be
   detected by this local prototype. Production reads and writes remain disabled for this reason.
3. Select qualified reviewers and their tradition scopes; approve actual reference policies,
   revisions, evidence and resolutions or withholding exceptions. No such decision was fabricated.
4. Define revocation delivery for any static hosting/CDN/client cache. `no-store` and fresh local
   files do not retract already deployed or downloaded artifacts. Legacy requires a contract and
   consumer change; this task does not implement Android changes.

Production release remains blocked wherever these requirements or qualified review are unmet.
Original audit/repair artifacts and calculation repairs are preserved. See `VERIFICATION.md`
for exact commands, XML counts, known failures and preservation evidence.

## Changed implementation files

| Area | Files | Purpose |
|---|---|---|
| New publication module | `Records.kt`, `LocalJournal.kt`, `Disputes.kt`, `PublicationPolicy.kt`, `Main.kt`, `disputes.json` | Scope/evidence schemas, signed local journal, maintained issues, common enforcement and offline workflow. |
| Build wiring | `settings.gradle.kts`, `publication/build.gradle.kts`, `api/build.gradle.kts`, `publish/build.gradle.kts` | Share the policy; keep generated test-only credentials/approvals in test fixtures. |
| Public HTTP | `api/.../Routes.kt`, `ApiFailure.kt` | Apply the common publication contract, distinguish unsupported guidance, disable HTTP caching. |
| Public files | `publish/.../PublicFeedPublisher.kt`, `Main.kt`, `PublishReport.kt` | Publish safe structured documents, refuse legacy, recheck revocation and stage fresh output. |
| Diagnostic legacy renderer | `publish/.../FeedPublisher.kt` now declares `ReviewFeedPublisher` | Preserve numeric rendering tests, confine writes to review output, remove synthetic UTC substitution. |
| Calculation CLI | `calc/.../CalcEngine.kt`, `HumanReport.kt` | Label calculated output as review diagnostics without changing calculations. |
| Verification | `publication/src/test`, `publication/src/testFixtures`, `api/.../PublicationControlsApiTest.kt`, adjusted existing API/publisher tests | Permanent approval and misuse tests; retain numerical delivery and byte comparisons under the explicit new contract. |
| Handoff | `publication/examples`, `docs/publication/2026-09-13` | Test-only preparation/templates, owner instructions and independent run evidence. |

`implementation-files.json` lists the exact paths changed from the preserved dirty starting
state. Existing unrelated changes shown by `git diff HEAD` are not attributed to this task.
