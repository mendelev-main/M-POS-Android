# Spec 041 — Authoritative Kotlin workspace persistence

## Scope

Continue the user-authorized migration immediately after catalog authority (040). Kotlin/Room now owns `layout` and `posNavigation`: category order/colors/symbols/WEB flags, layout tiles and folder/navigation configuration. Existing JS normalization, rendering and business permission rules remain unchanged. This is persistence ownership, not a Compose screen rewrite.

## Contract

- Each key has its own `mpos_workspace_authority_v1:<key>` durable marker. First access atomically imports current legacy data over any stale shadow. Subsequent startup never reimports legacy cache for an owned key.
- Use existing schema 12 document rows; no destructive migration, new dependency or network action.
- Retain the full object JSON exactly: array order, Unicode, unknown fields, null/false/zero and all WEB/category configuration. Native code does not reinterpret layout or folder semantics.
- Missing document, stored null and empty object remain distinct. Shared runtime fallback/normalization still handles them.
- Whitelist only layout and posNavigation. Reject unsupported keys, malformed/trailing JSON and non-object/non-null workspace documents before replacing persisted state.
- Native document/marker writes are transactional. SQL failure leaves the previous committed document and other key intact. Public reads/writes/removes share the existing FIFO with catalog operations.
- Ignore obsolete generic shadow puts/removes to owned workspace keys; initial legacy mirroring excludes all three native keys (products/layout/posNavigation).
- The JS provider registers waiters before posting, initializes independently per key, captures write payload before awaiting, checks native acknowledgement and updates secondary legacy cache afterward. Cache errors do not undo native saves; track counters per key.
- Native failure rejects a write. Read errors retain the existing callback/fallback contract without silently selecting stale legacy authority. Lost acknowledgement remains an uncertain commit, recoverable through the existing absolute-snapshot journal.
- Existing v13 validator/confirmation/critical journal remains unchanged and now writes products/layout/navigation through Kotlin. Other domains remain on their existing provider; do not claim a single atomic transaction across native/legacy domains.
- No automatic sync, availability policy, financial calculation, authorization or UI behavior changes. No runtime rollback to potentially stale legacy cache.

## Validation

Six real Room/native SQLite API 28 tests cover both-key migration/idempotency/obsolete mirror protection, absence/null/remove, failed marker rollback, failed write/delete rollback, invalid keys/shapes and persistent reopen.

Six additional executed JS tests cover independent migration/restart, delayed acknowledgement/immutable payload, native/cache failure, missing/null/remove, actual v13 restoration of all native keys and replay after a failed workspace stage. Existing catalog, payment-journal, source-parity and native suites still apply.

The CI workflow now includes Kotlin unit tests and both APK builds rather than only lint/debug build. Local checks establish the result; modifying the workflow does not itself prove a GitHub runner completed it.

Final comprehensive physical checks (deferred by user) include ordering, folder navigation, UI flags, restart, backup restore, larger fixtures, low storage and business regression.

## Rollback

Restore the previous provider routing with a verified fresh compatibility cache or v13 backup. Keep native documents/markers until recovery is complete. No format conversion is required, but a failed cache write can leave the fallback data stale.
