# Native migration review

## Current boundary

The Android app already contains a native tablet shell and native printer, image, backup, PDF/XLSX/share and Telegram transport boundaries. The reviewed bundled POS remains responsible for payments, shifts, warehouse business rules and operational network triggers. Room projections and OkHttp SSE are comparison/diagnostic boundaries; implementations are not evidence of an accepted authority cutover.

Specs 002–030 establish platform settings, Room projections, networking and recovery groundwork. Spec 031 starts a 200-entry metadata breadcrumb trail. The parity matrix still requires physical recovery, printer and tablet interaction evidence. Proceed by completing a useful boundary, rather than replacing working business logic solely because Kotlin is available.

## Sectors and order

| Sector | Concrete Kotlin benefit | Conditions and preserved behavior | Decision |
|---|---|---|---|
| Diagnostic report/export (P5) | Inspect printer/storage/network failures after restart, sanitize report centrally, save through Android document permissions; no backend needed | No business data or secrets; diagnostics never gate sales; manual export | Implement spec 032 now |
| Local persistence authority (P2), one domain at a time | Room transactions, consistent writes and queries, recovery outside WebView lifecycle | Existing JSON/backup v13 compatibility, migration/rollback, physical restart and large-data evidence; keep current Room shadows non-authoritative until accepted | Highest reliability priority after acceptance evidence |
| SSE/transport (P3) | Kotlin owns connection cancellation and lifecycle, bounded reconnect, platform networking diagnostics | First compare the same stream and interruption scenarios physically; no duplicate business event delivery, no automatic catalog sync | Keep shadow observation until acceptance, then migrate transport only |
| Durable outbox/retry (P4) | Native persistence can retain operation state across WebView/process loss and coordinate idempotent sends | One retry authority, local commit before network, backend idempotency; WEB acceptance/ready, loyalty and replaceable availability have different semantics | Migrate one operation type after Room/transport acceptance |
| Update infrastructure (P5) | Android installer integration and explicit package/signature/version checks reduce accidental incompatible updates | Requires defined trusted artifact source and stable production signing; preserve installed POS data | Separate specification once release policy is defined |
| Cart/workspace UI (P6) | Native touch/long-press, adaptive tablet layout and fewer browser interaction quirks | State/domain contract must be stable first; totals, modifiers, discounts, held checks retain current semantics | Later, after data boundary |
| Payment, shifts, recipes/warehouse, loyalty (P7–P11) | Testable domain engines and transactional state can improve reliability | Characterize existing business behavior before extraction; payment/stock/loyalty side effects are coupled, so no broad rewrite | Separate domain specifications and compatibility suites |
| Analytics (P12) | Room aggregate queries avoid scanning whole JSON receipt histories | Native accepted source and exactly preserved totals, returns, time boundaries and rounding | Last |

## Implemented slice

Spec 032 completes native diagnostic export and hardens the existing breadcrumb recorder. Kotlin builds a sanitized, versioned JSON report, includes app/API metadata and only the most recent 200 events, and writes it on IO after a user-selected Android document destination. The Android adapter supplies a settings button and an export command; it does not calculate business state.

The report sanitizes existing saved records as well as new ones, dropping arbitrary fields and replacing unknown category/event text. This avoids exporting old accidental payloads. Recording failures are best-effort and cannot stop the existing printer/storage/network callbacks. Storage projection failures are visible in diagnostics without changing the callback or storage authority.

Rollback removes the export adapter/action and Kotlin report/export classes; no data migration or business rollback is required. Physical export/recreation/provider tests remain pending until a tablet is available.

Spec 035 completes the next P2 foundation: one bounded Kotlin FIFO processes native shadow commands, and Room commits raw JSON and structured projections in the same transaction. Forced SQLite insert/delete failures prove rollback; requested/committed versions prevent failed or rejected updates from producing a green catalog comparison. The authoritative local write remains independent. Room authority and physical recovery/backup/large-history acceptance remain pending.

Spec 036 moves document-provider byte ingestion into a tested bounded Kotlin reader. The existing 500 MB check now runs during reading, before accumulation exceeds the limit; v13 bytes and restoration rules are preserved. Interrupted input cannot return a partial backup. Accepted large-file memory usage and physical provider/restore acceptance remain unresolved technical limits, not new business decisions.

Spec 037 extracts existing native photo packaging/staging into a testable Kotlin recovery module. It preserves v13 business fields and image mapping, while failed preparation attempts to remove every successfully staged file and preserves the original error. The manager retains provider IO and shared JS confirmation/restoration. Existing export deduplication and separate per-product import files are retained; physical recovery acceptance is pending.

Spec 038 completes a bounded Kotlin SSE message reader for P3 diagnostics. It corrects payload whitespace and line/empty-frame handling to match the legacy message listener, with native-only resource bounds. No WEB business event, ACK, availability or synchronization trigger moves. Physical stream acceptance is still required.

## Business questions for a separate decision

These are unresolved policy questions, not changes made by this implementation.

1. **Availability retry — resolved by user.** After an unsuccessful send, retry only after the next successfully persisted payment. Start/online/foreground must not retry. Example: offline payment changes 10 portions to 9, publication fails, and the website keeps its previous snapshot until a subsequent local payment triggers a fresh snapshot. Spec 033 implements this Android policy while retaining the reviewed shared source. Normal pre-existing stock-change/manual-sync publications remain supported while no failure is pending, but cannot bypass the post-failure gate. No automatic catalog synchronization is added.
2. **Reservation settlement bound.** Availability snapshots include at most 100 settled WEB-order IDs, including IDs recovered from durable paid receipts. If more than 100 payments need settlement after an outage, does the backend reconcile the older reservations independently? Backend code/evidence is not present in this checkout, so this is an integration question, not a confirmed defect. Do not increase the bound or change retry semantics without confirming the backend contract.
3. **Shift report format — resolved by user.** Telegram must receive a receipt image as on iPad. Spec 034 implements native PNG generation and sendPhoto while retaining local close/notification triggers. The iPad receipt counts all receipts (including returned ones) while sales totals exclude returns; this existing distinction is preserved and flagged for a possible future refactor.

No payment, stock, catalog, backup or authorization calculations are refactored as part of diagnostics. Availability retry behavior is changed separately under the explicit user decision in spec 033.

## Automated validation

- `node --test tests/*.test.cjs`: 58 passed, no failures or skips. Includes reviewed source SHA-256 parity, executed adapter/request/shift-close tests and repeatable source-sync fixture checks.
- `./gradlew --no-daemon --max-workers=4 testDebugUnitTest lintDebug assembleDebug assembleRelease`: successful.
- JVM: 51 executed tests, no failures/errors/skips. Covers diagnostics sanitization/bounds/failure isolation, iPad shift receipt values, photo multipart and real Android-native graphics PNG generation at API 28, plus FIFO/backpressure and file-backed Room/SQLite transaction rollback and reopen tests, and bounded backup input/provider-failure tests, and native backup image round-trip/rollback tests, and bounded EventSource-compatible SSE framing tests.
- Android lint: no errors; 14 warnings (13 previous warnings plus a KTX style suggestion in the new image renderer). Debug APK and minified unsigned release APK produced. Production signing, actual Telegram delivery and physical tablet acceptance are not established by these checks.
