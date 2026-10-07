# 105 — нативные отложенные заказы, клиенты и лояльность

Scope: Kotlin presentation of park label/list, resume/delete, customer picker/search/create/profile/reward selection, loyalty administrator program/client screens, program editor and manual adjustment. Existing 079/084/085/086 authorities, permissions, gift allocation and durable storage/outbox rules remain. No business formula, automatic sync/printing retry or v13 changes. Source handlers/DOM remain compatibility until 109; tablet acceptance 110 pending.

Use shared native theme/Manrope and form renderer, live stable fields, original mounted actions and accessibility labels. Search remains editable during reads and uses reviewed sequence/identity guards; mutations wait for acknowledgement and uncertainty blocks resubmission. No executable snippets, no copied auth constants, credentials wiped on close. Rollback flag changes presentation only.

Source business note: admin client searches currently lack the cashier picker's request-sequence guard. Example: admin types Anna, then Boris; Anna's slower response can replace Boris's results. Preserve this existing behavior in migration; record as a future refactor candidate, not silently change search policy. Existing admin adjustment permissions and backend password validation remain.

Status: implementation complete; final verification recorded below.

## Реализация

Shared native-settings bridge/controller provide both modal forms and loyalty
administrator page. Domain markers recognize reviewed mounted surfaces; live
fields keep stable IDs, source normalization/filter handlers, checked hidden
products and callback selection. Known mutations/navigation return promises are
installed idempotently at presentation/action time so late native command
replacements remain authoritative and awaitable. Read/search promises do not
lock typing; reviewed cashier sequence and customer identity guards remain.

Async Back waits for the original navigation result; failed navigation remains
open with its reviewed message. Done is displayed once and used for Back.
Field credentials are cleared by the shared Kotlin controller on dismissal.
Native page geometry is patched using real viewport/surface bounds instead of
dismissing the page when the keyboard resizes it; focus/cursor and live inputs
are reused. This also retains receipt panel bounds/scroll behavior from 104.

Rollback: MPosNativeCustomerUiEnabled=false restores customer/parked/loyalty
presentation, or disable the entire MPosNativeSettingsUiEnabled adapter. Native
parked/customer/local storage authorities are unchanged by these flags. Source
business files, permissions/password validation, financial formulas and v13
remain untouched. No new framework/dependency, background sync or print retry.

## Проверки

Actual reviewed parked/loyalty renderers and handlers execute with synthetic
orders/customers/programs and controlled storage/API responses. Cover park local
ack/failure/duplicate lock, resume/current-cart refusal/printed state, live phone
search/stale reply/filter/error, customer selection/address/profile/gift/removal,
program selected IDs/server refusal, admin entry/adjustment role checks, stale
token/rollback, failed async Back and late native command replacement. Existing
079/084/085/086 financial/native command suites remain. Shared Kotlin tests
cover stable phone/select/focus/palettes/Back and keyboard-resized page input
identity/cursor. Synthetic previews are not physical acceptance.

## Итоговая проверка

504 JS / 401 full JVM tests passed, 0 failures/errors/skips. Lint 0 errors /
15 existing warnings. Final suites include async failed Back, late native command
replacement, keyboard viewport focus/cursor preservation, hidden selected product
IDs and original new-customer payload/profile completion. Both synthetic palettes
inspected. No local product APK; physical acceptance remains pending.

After main publication: 104/110 engineering tasks (94.55%), 6 remaining. This is
an engineering task ratio, not fully native feature coverage or tablet acceptance.
Next 106 — warehouse/purchases/receiving/inventory UI.
