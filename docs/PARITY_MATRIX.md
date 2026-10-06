# Android parity matrix

User decision 2026-10-06 allows immediate native cutovers with automated compatibility checks and defers comprehensive physical acceptance to the end. `implemented` means code exists. Only `accepted` means it passed the physical Android tablet cases.

| Domain | Current state | Remaining evidence |
|---|---|---|
| POS interface and business modules | implemented from iPad commit `44fbf37` | Screenshot and interaction comparison on multiple tablet sizes, aspect ratios and densities |
| Local keys and JSON records | compatible shapes retained; products/layout/posNavigation/employees/shifts/orders/parked/currentOrderSession/criticalStorageJournal source is now Kotlin/Room (040–048), other domains legacy | Restart, storage failure and large-data checks |
| Ordered transactional Room shadow | implemented, spec 035; raw/projection writes and deletes are atomic; bounded FIFO, stale diagnostic guards | Final physical rapid writes, process exit, large histories and restore parity; products authority already switched under user decision |
| Products, recipes and stock | web runtime implemented | Full physical sale/return matrix |
| Payments, receipts and shifts | web runtime and native shift PDF printing implemented | Cash/card/split, restart recovery and printed output |
| Purchasing, receiving and inventory | web runtime implemented | Weighted cost, draft restart and reports |
| Product photos | native implementation | Picker, rotation, large image, restart and backup |
| Android platform settings mirror | ordered disk-confirmed Kotlin mirror, spec 039; legacy settings remain authoritative | Change printer/notification settings, restart app, verify UI + native snapshot parity |
| Complete backup v13 | native implementation | iPad → Android and Android → clean Android restore |
| LAN ESC/POS | native raster implementation | 58/80 mm printers, routing, copies and timeouts |
| Warehouse PDF/XLSX | native implementation | Exact values and visual comparison with iPad |
| Purchase-order sharing | native PDF implementation | Android share sheet and multi-page document |
| Telegram text/shift/monthly reports | implemented; shift close is a native PNG receipt via sendPhoto (spec 034), matching the approved iPad format | Physical image/content comparison and Telegram group/topic delivery, offline failure isolation |
| WEB orders and availability | shared runtime implemented; Android availability retry policy in spec 033 | SSE reconnect, acceptance recovery, post-payment stock and failed-send retry only after next persisted payment |
| Release/update installation | pending | Stable signing key and `adb install -r` data retention |
| Native diagnostic report | implemented; manual metadata-only JSON export, spec 032 | Offline save, cancellation/recreation/provider failure, 200-event retention and private-data exclusion on a tablet |

The iPad application remains the production source of truth until every critical row is accepted. Record physical results using [native tablet acceptance scenarios](NATIVE_TABLET_ACCEPTANCE.md).

## Bounded backup input (spec 036)

Kotlin enforces the existing inclusive 500,000,000-byte limit while reading, rather than after unbounded allocation. Exact bytes, v13 parsing and restoration remain unchanged. Automated boundary/short-read/zero-read/provider-failure tests cover the input layer; physical document-provider and large-backup restore/restart acceptance remains pending.

## Backup image preparation (spec 037)

Native API 28 tests cover real PNG round-trip, untouched business/extension fields, export ID deduplication, existing per-product import IDs, missing images, corrupt-image and disk-full cleanup, oversized images and continuing cleanup after a deletion failure. Physical provider/import/cancel/finish/restart evidence remains pending; business restore is still shared-runtime owned.

## Kotlin SSE framing (spec 038)

Functional JVM tests cover exact whitespace/UTF-8 data, fragmented CR/LF/CRLF, BOM, empty and named messages, EOF discard, line/frame limits and interrupted reads. Native observation is still diagnostic-only; same-stream parity, reconnect and lifecycle acceptance remain physical gates.

## Ordered settings persistence (spec 039)

Native tests cover checked/delayed/failed commits, immutable commands, FIFO, corrupt snapshots, bounded backpressure and cancellation. Executed JS tests preserve completed local saves despite native failure. Physical process restart, low storage and printer/notification UI/native parity remain pending; native settings are not authoritative.

## Catalog authority (spec 040)

Kotlin/Room now owns the compatible products document. One-time migration imports current legacy data over stale shadows; marker/document/indexes are atomic. Reads use full JSON, preserving unknown fields/order; writes acknowledge native commit before the secondary cache. Actual JS v13 import and critical journal recovery are automated. Layout/navigation/employees/shifts are also native (041–043); remaining domains stay legacy. Physical acceptance is deferred, not claimed complete.

## Workspace authority (041)

Kotlin/Room owns category layout and navigation configuration. Independent migration markers, full JSON/order preservation, native SQL rollback/reopen and actual v13/journal replay are automated. Existing rendering, normalization and permission checks stay unchanged; final tablet acceptance is pending.

## Employee and shift ownership (042–043)

Employees and shifts (including cash movements) now use authoritative Kotlin/Room persistence, alongside products/layout/posNavigation/employees/shifts/orders/parked/currentOrderSession/criticalStorageJournal. Full compatible documents remain the source; typed indexes are supporting structures. Atomic migrations and failed-write rollback, stale shadow protection, restart, actual v13 import and journal recovery are covered automatically. Employee authorization and shift financial engines remain reviewed JS. Other business documents remain on legacy storage; final physical checks remain pending.

## Paid receipt ownership (044)

Orders now use Kotlin/Room document authority with transactional receipt/line/payment indexes. Original sale, split payments, historical consumption and full-return/loyalty fields remain compatible. Actual JS payment-journal recovery, v13 import and full return are tested. Financial engines remain reviewed JS; full-array writes/index refresh remain a performance limitation. See [receipt storage model](RECEIPT_STORAGE_RU.md) for the proposed per-receipt repository and return ledger, which are not yet implemented. Final physical acceptance remains pending.

## Parked-order ownership (045)

Parked orders now use authoritative Kotlin/Room persistence. Complete delivery/customer/modifier/WEB/printing JSON is retained; document and header/line indexes commit atomically. Actual park/resume flows, delayed acknowledgement and failed-resume journal replay are automated. CurrentOrderSession and recovery journal remain legacy at this boundary; no single cross-document SQL transaction is claimed. Business commands and physical printing semantics are unchanged. Full-array refresh remains a limit.

Final tablet cases: park a delivery/WEB order with modifiers/customer and kitchen-printed items, force-stop, resume and compare fields; resume must disappear from parked history without losing the current cart after restart; verify delete and v13 round-trip. Final physical evidence remains pending.

## Session and critical recovery ownership (046)

CurrentOrderSession and criticalStorageJournal now use authoritative Kotlin/Room persistence and atomic singleton indexes. Legacy data is imported only once; secondary cache cannot override native recovery. Journal read failure propagates so existing JS recovery blocks new critical operations. Failed journal clear retains pending snapshots. Existing payment/return/park/resume replay and v13 tests now run through this boundary, including absent or failed caches. Replay orchestration and business commands remain JS; cross-document transactions are still journal-coordinated.

Final tablet cases: restart with unfinished cart, delivery/modifiers/WEB and printing state; restart after interrupted payment/park/resume, verify receipt/stock/shift/cart consistency and no duplicate operation. Native storage failure must prevent critical saves; backup v13 must preserve session. Physical results remain pending.

## Incremental receipt rows and history (047)

Canonical receipt archives now use complete per-receipt JSON in existing header rows; positions/payments are related rows. Snapshot reconciliation touches changed/deleted receipts only. Native full-array compatibility read/export is retained; noncanonical archives retain full-document mode. Secondary full archive cache is invalidated. Lazy migration/rollback, revision-checked individual upsert and SQL pagination are automated. Android history uses pages of 50 beyond the previous 300-receipt limit, with original detail/print/return renderer and stale-response protection.

Limits: shared business runtime still loads/submits full arrays, native reconciliation scans them and critical journal still stores full snapshots. No claim of constant-cost complete payment or reduced startup memory. Final tablet checks include 325+ receipts, tied dates, page controls, returns/print, new sales on history tab, restart and v13 export/import. Noncanonical fallback retains prior history presentation.

## Atomic local payment command (048)

Actual payment finalization now uses Kotlin validation/stock deduction/delivery cash checks and one Room transaction for catalog, shifts, one receipt, cleared session and idempotency marker. No full order archive is sent or copied to a payment journal. Exact retry is locally idempotent; stale/conflicting commands fail. Pricing, discounts/rewards and recipe expansion remain reviewed JS; external effects run after native acknowledgement. Nonpayment journal recovery remains unchanged. This partially implements P7, not the whole financial engine. Physical cash/card/split/delivery/reward/force-stop and v13 cases remain pending.

Business issue for separate refactor: existing cross-shift refund drawer calculation (100 opening minus 20 refunded from prior shift reports 100 instead of physical 80) is reproduced and retained, including native delivery cash parity. Return attribution and historical reports need a coordinated business fix.

## Shift accounting (049)

Approved business correction: cross-shift refunds reduce the return shift's cash/card revenue and preserve the closed sale shift's totals. Kotlin native delivery guard and Android JS UI/report/return guards share synthetic fixtures. Backup v13 and existing records unchanged. Older records without returnedShiftId use their original shift; no inferred repair. Physical same/cross-shift cash/card/split return, closing report image and print acceptance remain pending.

## Atomic full return (050)

Historical receipts use Kotlin transactional return with original stockConsumption, original payment parts, current return shift and preserved JSON extensions/position. Unknown acknowledgement retries the identical command once; unresolved status requires reload. Legacy no-consumption receipts retain current-recipe restoration and journal. No new backend/availability effects; loyalty starts after local acknowledgement. SQLite rollback/reopen/restored-backup and actual JS workflow tests are automated; tablet/terminal/Telegram/print checks remain pending.

## Manual cash movements (051)

Native deposits/withdrawals preserve current positive-amount rules, unrounded decimal values, nonnegative-drawer requirement for both types and 0.0001 withdrawal tolerance. JS applies state after ack; native errors roll back full shift/projection/marker writes. 049 cross-shift refunds limit available cash. Source JSON/v13 unchanged. Native reopening/replay/restored-backup and actual handler tests automated; physical acceptance pending.

## Shift opening / closing (052)

Native lifecycle persistence validates most-recent counted carryover, employee snapshots, current shift and closing drawer/receipt count. Full shift JSON/extensions/movements survive; counted cash/difference remain unrounded and mismatch allowed. Reviewed administrator check unchanged; no credential copied. Telegram/monthly/print run only after ack. SQLite rollback/replay/restore and actual JS handler tests automated; image/printer/tablet acceptance pending. Unpaid cart/session and parked orders remain unchanged.

## Native shift report read model (053)

Compatible report payload now reads native persisted shifts/receipts atomically; legacy duplicate-ID documents/null archives retained. Active PNG Telegram, LAN receipt, PDF and report modal share native financial data, not caller snapshots. Pending output reads coalesce; failure never sends stale fallback. Goldens cover 14 scenarios; actual runtime tests cover close → read → outputs, copies/routing, fresh reads and cancellation. Main dashboard remains JS mirror and physical output evidence remains pending.

## Native shift screen (054)

Kotlin renders authoritative active totals, item discounts, movements and latest 20 closed shifts. Read-only source/v13 preserved. Existing forms/actions remain with whitelist/current-shift/modal guards. Geometry/visibility, stale-result cancellation and explicit rollback covered by JS/Robolectric tests; native SQLite checks finance/history/pending recovery. Physical navigation/rotation/font size/keyboard/performance acceptance pending.

## First-run empty shift document (055)

Missing/null native shift documents now retain reviewed empty-list semantics for lifecycle and derived screens/commands. No read-time writes/schema/v13 changes. Reproduced failing native regression before fix; opening/replay/closure for both roles and no-mutation empty-screen checks added. Android warning now reports operation failure without Safari/blanket loss claim; source hashes preserved. Physical updated-APK acceptance pending.

## Native cash forms (056)

Native amount/comment/confirm/cancel with finite-positive parsing, comma/fraction preservation, acknowledgement lock and token cancellation. Existing native drawer/atomic/replay validator and original JS submit flow retained. JS actual-handler integration and Robolectric dialog controls verify deposit/withdrawal, errors/retry/unknown status, rollback and no premature close/state. User confirms first opening and backup import after 055; comprehensive/new-form device cases remain pending.

## Native closing form (057)

Room-derived expected cash, prefill and native counted input; blank/negative/nonfinite reject, zero/fraction/shortage/surplus allowed. Original lifecycle transaction and post-ack Telegram PNG/print preserved. JS actual-handler integration, SQLite fresh/cross-refund/closed/recovery cases and Robolectric loading/cancellation/fallback/locks automated. Physical form/output checks pending.

## Native opening form (058)

Kotlin staff picker/conditional password input and Room carryover, no new auth decision or password constant. Original admin verifier rejects before transaction; state/opening notifications after ack only. Empty/null/read-error/ambiguous staff/recovery, scalar carryover, password clearing/no saved View state, token/busy/rollback and actual-handler/native command paths tested. Original shared source hashes retained. Current real admin password/device keyboard/long names/rotation/notifications are pending physical checks.


059: исправлено оформление нативного экрана смены: палитра POS, карточки,
акценты и переключение light/dark. Бизнес-логика не изменена.
Спецификация: `specs/059-native-shift-theme/spec.md`. На планшете проверить
обе темы, читаемость и кнопки смены; физическая проверка пока ожидается.


060: Kotlin пересчитывает цены позиций, товарные скидки и итог перед
сохранением оплаты; расхождение откатывает транзакцию без внешних действий.
Отображение корзины, формирование цены модификаторов и распределение подарков
пока JS. Совместимость v13 сохранена; rollback: MPosNativePricingEnabled=false.
Спецификация и наблюдения по бизнес-правилам: `specs/060-native-settlement-pricing/spec.md`.
На планшете ожидаются скидки/доставка/подарки/смешанная оплата/импорт.


061: распределение подарков и снимок лояльности пересчитываются Kotlin
в транзакции оплаты перед проверкой итоговой цены. Порядок программ,
самый дешёвый товар, целые единицы и правила округления сохранены.
Сетевой guard/публикация/возвраты и UI пока прежние; v13 без изменений.
Откат: MPosNativeLoyaltyRewardsEnabled=false. Спецификация:
`specs/061-native-loyalty-reward-allocation/spec.md`.
Физические проверки пересекающихся программ, подарка со скидкой/доставкой,
отсутствия сети, возврата и импорта ожидаются.


062: цена новой позиции (база, доплаты модификаторов, ручной ввод/округление)
формируется Kotlin до добавления в корзину. Оплата проверяет базовый снимок
и доплаты; старые позиции без basePrice не переоцениваются. При ожидании
ответа добавления последовательны, оплата ждёт; отменённые/устаревшие ответы
не меняют корзину. Формы выбора/preview, merge orchestration и рецептуры ещё JS.
v13 без изменений. Откат: MPosNativeConfiguredPricesEnabled=false.
Спецификация: `specs/062-native-configured-unit-prices/spec.md`; физические
кейсы ручной цены, модификаторов, повторных нажатий/отмены/остатков/импорта ожидаются.


063: Kotlin разворачивает рецептуры/модификаторы по каталогу Room внутри
транзакции оплаты, проверяет исторический снимок и использует свой расчёт
при списании. Общие ингредиенты/дроби/порядок/допуск/циклы сохранены.
Предварительные проверки корзины, доступность и себестоимость пока JS.
v13 без изменений; rollback: MPosNativeRecipeConsumptionEnabled=false.
Спецификация: `specs/063-native-recipe-consumption/spec.md`.
Проверки на планшете ожидаются. Локальные APK не собираются по указанию
пользователя; сборку после коммита выполняет GitHub.
