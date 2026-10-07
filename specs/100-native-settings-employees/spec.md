# 100 — Native settings and employee surfaces

## Approved boundary

Native settings/network hubs, employee list/editor/delete confirmation, printer manager/editor, notification forms, company/delivery/discount forms and the settings entry forms for backend/Telegram/backup. Domain-specific loyalty/inventory/warehouse pages keep their separate planned UI boundaries. Native Android views use MPosNativeTheme/Manrope, light/dark palettes, scroll and 48 dp actions. The Web navigation shell and reviewed business/auth handlers remain compatibility boundaries until 109/110; presenting a form natively does not copy or move its password verifier.

Printer/notification settings become authoritative in the existing native preferences store with a one-time marker. Initialization imports the current reviewed snapshot, never an old asynchronous mirror. Each native write commits the full compatible snapshot/marker atomically, compares expected state and confirms disk success. Frozen cached settings support existing synchronous snapshot/v13 APIs. Compatibility storage reads use the native cache; scoped original setters stage writes, memory and UI/test-print effects, then publish after native acknowledgement. Failed writes preserve the previous state, uncertain status blocks another write. Delayed legacy mirrors cannot overwrite native authority. Backup restores both settings together and old JSON/defaults/extensions remain compatible.

## Business rules

Employee ordinary create/edit is available without an open shift. Changing administrator role requires the existing private verifier. Deletion also needs an open shift, cannot delete the current employee or an administrator, and preserves shift/receipt history. Native forms delegate to those reviewed handlers and the existing Room employee commands. The verifier is neither read/copied into new code nor exported to native models. Entered passwords and tokens stay ephemeral and are cleared at form close.

Company/discount/delivery edit affordances and administrative network panel remain gated by the reviewed active admin shift. Existing network/manual catalogue, Telegram and backup handlers retain their trigger/validation rules; no startup/reconnect catalogue sync, availability publication or automatic printer retry. Native UI uses structured field/button tokens with known mounted reviewed DOM actions; arbitrary JS snippets are not evaluated.

## Compatibility and verification

MPosNativeSettingsUiEnabled=false restores reviewed presentation; settings authority remains native. Printer/notification source methods and compatible keys/JSON/defaults are retained behind the staging facade. Native-hub requests are visible-tab/geometry/generation gated. Modal cancel, pending saves, wrong password, role-change visibility, native errors, font scale, keyboard and landscape are covered by automated/native tests where supported and physical acceptance stays pending. Full JS/JVM/lint required, no product APK assembly. Engineering counter is updated only after implementation, passing checks and main publication.

## Implementation and boundary limits

`MPosSettingsStore` now owns printers/notifications through FIFO status/initialize/read/CAS-write operations. Native authority imports current reviewed defaults once, never adopts the older mirror, atomically commits snapshot and marker, preserves extensions, ignores delayed mirror/clear/read operations and requires restart after an uncertain disk result. The JS adapter supplies isolated cached synchronous reads, stages reviewed setters and test-print/UI effects, and waits for native acknowledgment. v13 application explicitly awaits printer restoration; the larger business-data import and preferences commit are separate existing boundaries, not one cross-store transaction. On a settings-stage failure, business data may already be imported: repeat the import after recovery rather than reporting full success.

`MPosSettingsScreenController` owns native settings/network hubs and the named settings forms. Manrope, both palettes, cards, compact wrapping actions, capped scrolling, credential clearing, busy/blocked/token checks and resize-aware geometry use the shared design. Known mounted DOM buttons/field tokens delegate to opaque reviewed handlers without reading/copying/evaluating their verifier. Ordinary fields and role visibility keep the mounted compatibility DOM. Current source company/delivery/discount handlers still have their original fire-and-forget persistence semantics; this stage changes their presentation, not that business boundary. Suppliers/loyalty/inventory/warehouse forms retain their own planned UI scope.

The Web navigation shell, normalization, full reviewed DOM and authentication runtime are still present. This stage does not remove WebView or establish independent native password authorization. The final authentication replacement must be resolved before 109 can remove the reviewed runtime. No speed/battery/physical-acceptance claim follows from the task counter.

Native cancellation reuses the original mounted cancel/back button; no duplicated cancel button. Original password/token fields are ephemeral, never logged/persisted by the presentation controller and cleared with dismissal. Startup happens after all adapters are installed; reviewed source hashes and refresh preservation remain checked.

Verification 100: 453 JS tests and 379 JVM tests passed; 0 failed/skipped. Android lint: 0 errors / 15 existing warnings. No product APK assembled. Synthetic light/dark native previews inspected; physical tablet/printing acceptance pending.
