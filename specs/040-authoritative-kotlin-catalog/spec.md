# Spec 040 — Authoritative Kotlin catalog persistence

## User decision and scope

On 2026-10-06 the user authorized immediate native migration/cutovers, stated that a backup was saved, and deferred comprehensive manual testing until the end. This supersedes historical per-stage physical gates in specs 002–039 and the roadmap. Automated compatibility checks remain required; physical behavior remains unverified until final acceptance.

Kotlin/Room becomes authoritative for the `products` document. This includes all fields in product cards, recipes, modifiers and stock values, but does not rewrite the business calculations that produce them. Category names remain in the native document/index; layout/category ordering/navigation remains legacy in this stage. Other storage domains retain their existing authority.

## Native contract

- `MPosCatalogStorage` owns catalogStatus/Initialize/Read/Write/Remove through the existing FIFO worker. No schema upgrade or destructive migration; Room version remains 12.
- A durable `mpos_catalog_authority_v1` metadata row records migration. First migration imports the current legacy document, overriding any older non-authoritative shadow. Document, indexes and marker commit in one transaction.
- Subsequent startup trusts Room, never reimports stale localStorage. Absence, stored null and empty arrays remain distinct; initial default seeding still runs in the shared POS.
- Full compatible JSON is authoritative; indexes do not reconstruct it. Unknown fields, original array order, whitespace IDs, missing/duplicate IDs and nullable fields are retained. Existing JS validation still controls business acceptance.
- Replace/remove atomically update the document and product/category indexes. Failed SQL/projection work rolls back the entire change. Previous committed data remains readable after a rejected write.
- Obsolete generic shadow writes/removes to products are explicitly ignored once ownership is established. Startup JS mirroring excludes products. Other domains are unaffected.
- Native writes acknowledge only successful transactions. The JS compatibility cache updates afterward; cache failures are tracked and cannot undo a native commit. Cache may be stale after failure and cannot be selected as authority at runtime.
- Reads do not silently fall back to stale legacy data. They retain the existing read-error callback/fallback contract, so the POS can mark storage broken. Snapshot/debug reads reject missing native authority.
- Bridge waiters are registered before posting (synchronous responses work). Timeout is 15 seconds and explicitly reports uncertain commit status; timeout never proves rollback. Existing critical journal can replay absolute catalog snapshots idempotently after a lost acknowledgement.
- Existing payment/backup/warehouse writers reach Kotlin through the same storage facade; business triggers/calculations and reviewed JS source remain unchanged. The existing JS critical journal stays authoritative for multi-domain transactions; no false cross-store atomicity claim.
- v13 import uses the existing validator/confirmation/journal, replacing the native catalog and updating the compatibility cache. Export keeps the existing representation. No automatic catalog sync or availability retry change.
- Room is the default catalog mode. Compare remains a diagnostic mode with Room as the active source; legacy mode requires an explicit code rollback/recovery from backup or a verified fresh cache.

## Automated evidence

Eight real Room/native SQLite API 28 tests: current-legacy migration over stale shadows, marker idempotency/reopen, FIFO, absence/null/remove, obsolete mirror protection, forced insert/delete rollback, malformed input and full-document preservation.

Twelve executed JS tests: cold/restarted/concurrent initialization, acknowledgement order and immutable payload, native/cache failures, read failure callback, absent/null/remove, initial mirror exclusion, actual shared payment journal recovery, lost acknowledgement/replay, and actual v13 validation/application to native catalog. Existing shared-source hashes and other tests must remain valid.

Physical restart, large data, backup, financial regression, printing and SSE cases remain in the final comprehensive checklist. Automated evidence does not claim physical acceptance.

## Rollback

Restore the former JS provider selection and native handler implementation only with a verified fresh compatibility cache or v13 backup. A cache whose last native-to-legacy write failed may be stale. Keep Room records/marker until a deliberate rollback or reset is complete; do not toggle to legacy at runtime.
