# 058 — Native shift opening / employee input

Authorized continuation after 057. Native Android dialog renders employee selection, conditional password input and opening carryover. Existing administrator verifier is retained; its existing constant is not inspected/copied into new code. No authentication policy change or native credential store.

## Room form model

MPosShiftOpeningRepository reads authoritative shifts/employees/recovery in one transaction through storage FIFO. It does not depend on receipt archive initialization or reconstruct receipts. Missing/null shifts/employees have empty-list semantics. Existing open shift, pending critical recovery or invalid previous counted cash reject the model.

Most recent closed shift supplies counted cash (or zero). Shared MPosJsonNumbers scalar conversion is used by both model and lifecycle command to match reviewed Number(value||0), including numeric strings, booleans/whitespace/radix strings. Invalid/nonfinite/negative carryover remains rejected. Records/indexes/keys are not rewritten.

Response contains only authority metadata, opening cash and sorted ID/name/admin-or-cashier presentation roles. Source employee JSON/extensions/order are preserved; phones/private attributes/credentials are not included. Names use the platform Russian collator. Ambiguous duplicate IDs or malformed IDs/names reject native presentation instead of dropping records. UI shows full names with administrator suffix; no business identity change.

## Native input / privacy

MPosShiftOpenDialog has loading/read retry/explicit legacy fallback, a placeholder employee picker and opening-cash display. Empty staff disables confirmation and explains where to create employees. Administrator selection shows a password field; cashier selection hides/clears it. Native UI checks only nonempty admin input; existing JS verifier decides acceptance.

Password is transient UI input, view state saving disabled; it is cleared on selection changes, submission, known rejection/cancel/replacement/destruction. Only the entered input crosses the trusted bridge to existing verification, then the compatibility field is cleared in finally. It is never added to Room command, recovery journal, backup or diagnostics. No password/verifier constant is added to Kotlin, fixtures or docs.

Picker/input/confirm/cancel lock pending acknowledgement; native token guards obsolete replies, busy guard prevents duplicate submission. Known failure permits a corrected attempt, uncertain status requires reload before another critical operation.

## Retained commit and rollback

Adapter opens the real original form, hides its overlay and uses its real employee/password fields as compatibility inputs. Before calling original submitOpenShift it checks token, loaded/busy state, no current shift and employee existence/select compatibility. Original verifier remains unchanged. Existing native lifecycle validates employee/shifts snapshots and carryover, commits once and applies exact replay handling. State/Telegram opening/monthly report run after native acknowledgement as before. No password is forwarded to lifecycle.

Cash/close/report wrappers coexist; dismissal is deferred during save then completed once, replacement/cancel invalidate tokens. MPosNativeOpenFormEnabled=false restores original forms for the session; native loading offers explicit fallback. Removing only this adapter restores original entrypoints without converting saved data. P8 remains partial: original authentication and submission/orchestration still run in shared JS. No claim that they migrated into Kotlin.

JSON/schema/v13, package/signing policy, manual catalogue sync and payment-only availability retry gate stay unchanged. No manufacturer, resolution or chipset assumptions.

## Verification / next boundary

Native SQLite tests cover absent/null/empty staff/shifts, zero/no read-time source changes, sorted minimal staff, preserved extensions, shared scalar carryover and actual lifecycle commit, existing-open/recovery/invalid cash/duplicate staff rejection. Robolectric runs real picker/password controls, selection guards, required admin input, transient clearing/no view-state save, acknowledgement lock, empty staff, retry/fallback and stale replies.

JS adapter tests cover delegating submission once, success/rejection/uncertainty, no-current-shift/employee/busy/type/token guards, field clearing, cancellation/replacement/session fallback/load order. Actual original submit handler/storage adapter test covers cashier opening after ack, unchanged administrator rejection before any native commit and no password on command. Source hash/sync, full JS/JVM, lint, debug and minified unsigned release required.

Physical current admin password, keyboard, long names/Russian collation, rotation/Back/fonts, restart/import and opening notifications remain pending. Remaining shared auth/orchestration stays documented; next high-value candidate is P7 native pricing/discount/cart totals after reviewing its parity fixtures, not a declaration of complete P8.
