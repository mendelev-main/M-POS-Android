# 057 — Native shift closing form

Authorized next boundary after 056. Visible closing form moves to Kotlin Android dialogs without new dependencies. Opening/administrator verification remains reviewed UI; no credential copied.

## Native source and input

MPosShiftReportRepository.readCloseForm reads current open shift, compatible canonical/legacy archive and critical recovery state in a Room snapshot through the storage FIFO. It requires the requested ID to be the first current open shift, a cleared recovery journal and a finite nonnegative drawer. It returns only ID, expected cash, revision and authority metadata. WebView financial values are not inputs. No historical report construction/receipt-list serialization for the form; archive reconstruction remains internal for legacy compatibility.

MPosShiftCloseDialog shows cancellable loading, then reuses the native cash input component in closing mode: expected cash, prefilled exact expected value and one counted-cash field. Zero and arbitrary positive fractional amounts are accepted; blank/negative/nonfinite counted values reject. Decimal comma accepted without rounding stored input. Display follows the reviewed two-decimal comma style. Difference may have either sign; no cash matching restriction added.

Failed/stale/negative snapshot offers manual retry or explicit legacy form. Obsolete replies cannot reopen a cancelled/replaced generation. Cancel does not write. Controls/input/cancel lock during commit acknowledgement; known rejection allows editing, uncertain status requires reload before another operation.

## Preserved commit/output boundary

Android adapter opens the real original HTML form and hides its overlay while native presentation is active. Typed counted cash updates the real compatibility field and calls original submitCloseShift -> native-shift-lifecycle-command -> MPosShiftLifecycleCommand. Current shift ID, loaded/busy/token/type/finite counted input are checked before submission. JS command construction and output orchestration remain; this stage does not claim full P8 or native authentication.

Original submit recomputes current expected cash on confirmation, as before; a new payment while the form is open is not given a new business rejection rule. Existing native command validates current state/cash/receipt count and performs the atomic close/replay marker transaction. State/Telegram PNG/receipt print run only after acknowledgement. Closing retains unpaid cart and parked orders. Native wrapper defers legacy modal dismissal while busy and completes it once after success. Snapshot failure and commit rejection do not close the shift or emit reports.

Native close wrapper loads after 056 and coexists with cash forms/report modal wrappers. Cancel/replacement invalidate the token and native window. MPosNativeCloseFormEnabled=false restores original forms for the session; native loading has explicit fallback. Removing only this adapter restores original entrypoints without data conversion. Schema, JSON extensions/v13, original authentication, manual catalogue synchronization and payment-only availability publication stay unchanged.

## Verification

JS adapter tests cover zero/fractions, duplicate/obsolete/busy/invalid/current-shift guards, failure/uncertain status, cancel/replacement/fallback and load order. Original handler + storage adapter integration covers counted zero/shortage/surplus with native lifecycle payload and no state/Telegram/print before ack. Existing Room lifecycle rollback/reopen/replay/restore tests remain.

Native SQLite form query verifies 100 opening minus 20 prior-shift cash refund = 80, fresh new receipts, ignored caller finance, source preservation, closed target/recovery rejection and no receipt/history payload. Robolectric dialog tests exercise loading/model prefill, mismatch allowed, zero, stale replies, retry/fallback, unknown status locks and parser errors. Full JS/JVM, source hashes/sync, lint, debug and minified unsigned release required. Physical keyboard/Back/rotation/font scale/PNG/LAN printing/large archive remain pending.

## Compatible archive at closure

Closure now computes both receipt count and drawer from MPosOrderStorage's compatible archive, the same source as the native form/report. The old projection-only close read dropped duplicate-ID legacy entries and disagreed with imported archive length/balance. No receipt IDs/rows are rewritten to repair these archives. Canonical rows still reconstruct their archive; legacy document mode/null remains supported. Native regression verifies duplicate-ID 10+20 cash sales, opening 80 -> expected 110, counted 109 -> difference -1, source preservation and replay. This restores reviewed full-archive accounting without changing refund or cash policy.
