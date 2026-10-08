# Android parity matrix

> Детализация 109 (08.10.2026): **6/20 — 30,00%**, осталось 14. Общий детальный план: **113/129 — 87,60%**, осталось 16. Крупные этапы: **107/110 — 97,27%**. [Подэтапы, критерии и правила подсчёта](../specs/109-native-runtime/tasks.md). Проценты относятся к количеству задач; физическая приёмка 110 ещё не выполнена.


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


064: добавление обычной/модифицированной/ручной позиции и слияние количества
ожидают проверки Kotlin по Room. Нехватка сообщает ингредиент; устаревшие
ответы при изменении корзины/остатков/состава/отмене не добавляют позицию.
Проверка read-only, списание только при оплате. Степпер количества и прочие
canFulfillCart caller/preview пока JS. v13 сохранён; rollback:
MPosNativeStockPreflightEnabled=false. Спецификация:
`specs/064-native-cart-stock-preflight/spec.md`. Физические кейсы ожидаются.
Локальные APK не собираются, тесты и lint обязательны.


065: qty + delta, удаление по <=0 и проверка всей корзины выполняются
Kotlin/Room. Изменения количества и добавление идут в одной FIFO; оплата ждёт.
Просроченные/отклонённые ответы не меняют строки. Контекст заказа после
удаления последней строки степпером сохранён, как в исходнике; спорные
правила уменьшения/legacy ключей описаны в спецификации. v13 без изменений.
Rollback: MPosNativeCartQuantityEnabled=false.
`specs/065-native-cart-quantity/spec.md`; физические кейсы ожидаются.
Локальная APK не собирается; обязательны тесты и lint.


066: проверки остатков при открытии оплаты, подтверждении наличных/карты и
оплате части переведены на Kotlin/Room. Подарки и подтверждение карты сохраняют
порядок; отменённые/устаревшие ответы не запускают оплату. Проверка read-only,
финальное списание по-прежнему в атомарной транзакции. Backup v13 без изменений.
Rollback: MPosNativePaymentPreflightEnabled=false.
Спецификация: `specs/066-native-payment-preflight/spec.md`.
Автотесты и lint обязательны; локальная APK не собирается.


067: MPosCartTotalsEngine объединяет скидки/подарки/доставку в единый read-only
quote. Открытие и подтверждение оплаты/части ждут Kotlin после stock preflight;
исходные рендереры используют его только для совпадающего снимка. До quote и
после изменений общие render callers ещё JS; полная миграция UI не заявляется.
Суммы финальной транзакции проверяются теми же движками Kotlin. Backup v13
не меняется. Rollback: MPosNativeCartTotalsEnabled=false.
Спецификация: `specs/067-native-payment-totals/spec.md`.


068: подтверждение внешней оплаты картой (включая смешанную часть) теперь
нативный MPosCardConfirmationDialog. HTML этого окна не строится. Android
Views/AlertDialog, светлая/тёмная тема, отмена/Back, один callback на token.
Основной payment UI пока WebView; Compose не подключён. Запись оплаты и
внешние эффекты остаются на прежнем проверенном пути. Backup v13 без изменений.
Rollback: MPosNativeCardConfirmationEnabled=false.
`specs/068-native-card-confirmation/spec.md`; физические проверки ожидаются.


069: окно наличной части смешанной оплаты заменено MPosSplitCashDialog;
ввод/номиналы/preview/округление и проверка tender выполняются Kotlin локально,
без моста на каждое нажатие. Paid=true только после прежнего progress commit.
Backup v13 без изменений. Основной наличный keypad и экран оплаты пока WebView.
Rollback: MPosNativeSplitCashEnabled=false.
`specs/069-native-split-cash/spec.md`; физическая приёмка ожидается.


070: основной cash keypad открывает нативный редактор суммы (Done не оплачивает).
Tender и сдача обычной оплаты рассчитаны Kotlin в существующем cartTotalsRead,
без дополнительного запроса. Сохраняются zero total/epsilon 0.0001 и gift guard;
после обновления подарка выполняется свежий quote. Shell оплаты/inline quick
buttons пока WebView, Compose не подключён. Backup v13 без изменений.
Rollback: MPosNativeCashPaymentEnabled=false.
`specs/070-native-ordinary-cash/spec.md`; физическая приёмка ожидается.


071: по запросу дизайна подключены repo frontend-design и mpos-native-design
(AGENTS.md). MPosNativeTheme централизует палитру, offline Manrope, веса, кнопки
и окна. Переработаны текущая смена, история, выбор сотрудника; cash/card/close
forms получили тот же стиль. Presentation-only: финансовые/auth/storage/v13
контракты сохранены. `docs/NATIVE_DESIGN_SYSTEM.md` и
`specs/071-native-pos-design/spec.md`; физическая приёмка ожидается.

072: initial split plan — native engine/source fixtures, current payment
quote cutover for initial two parts. Editing/count/paid drafts remain reviewed
JS; rollback and stale quote fallback covered. Physical tablet acceptance pending.

073: split count redistribution — native read-only decision; source golden
fixtures, FIFO/stale/error/paid identity coverage. Amount editing and normalize
remain JS. v13 and settlement unchanged; physical acceptance pending.

074: split amount editing — authoritative native parsing/arithmetic; reviewed
145 source fixtures, shared FIFO/coalescing/count barrier, paid identity,
protocol/stale/rollback. Presentation keypad/gate and normalize remain JS;
physical acceptance pending, v13/settlement/effects unchanged.

075: native split normalization and restart draft validation — 145 goldens,
file-backed Room paid draft reopen, frozen/correlated storage reads, protocol/
stale/rollback. Reviewed validator fallback on failed/mismatched preparation;
raw v13/session and progress/settlement unchanged. Physical pending.

076: matching native cart preview refresh for financial input changes, DOM
identity/scroll and stale/failure/rollback coverage; source pending preview
retained. Physical response/performance pending, v13 unchanged.

077: delivery check/select/type source fixtures, FIFO continuity/external stale,
quote-integrated gate and pending-payment protocol; strict zero tariff preserved.
Rate CRUD and backend source, v13 unchanged; physical pending.

### 078: локальное сохранение настроек заказа

ECMAScript trim в Kotlin, 31 fixture + source parity; customer object/id/extensions,
бонусы, orderComment и WEB/split metadata сохранены. Form/queue stale guards,
rollback, bridge failure, double save проверены автоматически. Физически pending.

### 079 — отложенные заказы

Actual source hold/resume/delete flow сохранён вокруг native commit. Atomic
rollback второго документа, stale expected/journal/print patch, full metadata,
ack-before-print/loyalty, failed hold, rollback и uncertain state проверены.
Physical acceptance pending; UI/receipt builder ещё WebView/source.

## Этап 080

080: 45 actual category source fixture, history/returned/units/dependency и Room authority, object references/save order/stale/protocol/failure проверены. Physical pending, product form/media и category write plumbing остаются source.

## Этап 081

081: 37 source fixtures, generated IDs/yield, authoritative Room catalogue, save decision order/cache bypass, source rollback/Infinity compatibility и stale/protocol/failure проверены автоматически. Physical pending; полный editor/native UI не заявлен.

## Этап 082

082: 52 reviewed folder/root command fixtures, stable order/20-limit/nearest cells, strict normalize/UTF-16/bridge escaping, FIFO/ack/failure/stale/cancel/rollback проверены. Physical pending; renderer/query runtime ещё source.

## Этап 083 — команды сотрудников

Создание, редактирование и удаление проходят через Kotlin/Room: проверка ожидаемого списка, разрешённых изменений и актуальной смены, атомарная запись JSON и индексов. Обработчики обновляют экран только после подтверждения сохранения. Импорт v13 и откат сохранены. Проверка пароля остаётся в исходном обработчике; маркер шлюза не является самостоятельной нативной авторизацией. Нативные формы сотрудников реализованы в 100; независимая нативная авторизация остаётся обязательной границей перед удалением runtime в 109.

Сохранены правила: можно снять права с последнего администратора; сохранение сотрудника не требует смены; удаление другого обычного сотрудника требует открытой смены и исходного пароля. Физическая приёмка ожидается. Следующий этап — 084, команды клиентов. См. [спецификацию 083](../specs/083-native-employee-commands/spec.md).

## Этап 084 — клиент текущего заказа

Выбор/удаление/применение профиля рассчитываются Kotlin, сессия сохраняется через Room до обновления памяти и запроса бонусов. Серверный справочник клиентов не дублируется новой таблицей; создание/поиск пока используют действующий backend путь. Совместимость v13, WEB metadata и оплаченных split частей сохранена. 333 JS и 266 JVM тестов прошли; lint 0 ошибок, 15 прежних предупреждений. Физическая приёмка pending.

Сохранено исходное правило: выбор нового клиента оставляет прежний адрес/дополнительные поля, удаление клиента оставляет адрес, остальные поля очищает. Следующий этап — 085, нативная онлайн-проверка подарка и действующая offline policy. [Спецификация](../specs/084-native-customer-context/spec.md).

## Этап 085 — онлайн-проверка подарка

Профиль бонусов читается через OkHttp: HTTPS, deadline 5 секунд, отмена и отсутствие автоматического retry. Kotlin сравнивает запрос подарков с программами; поздний ответ для изменённого заказа/клиента не разрешает оплату. Исходный offline диалог сохраняет отмену или явное продолжение без подарка. Другие API-маршруты и запись оплаты не изменены. 343 JS / 271 JVM тестов прошли; lint 0 ошибок / 15 прежних предупреждений. Следующий 086 — начисление/отмена и durable retry. [Спецификация](../specs/085-native-loyalty-eligibility/spec.md).

Бизнес-вопрос: проверка не резервирует подарок на сервере; нечисловое truthy rewards в исходном сравнении NaN не приводит к отказу. Сохранено для parity, строгая backend схема/атомарная резервация требует отдельного решения. Физическая приёмка pending.

## Этап 086 — начисление/отмена и журнал лояльности

Kotlin строит payload и управляет claim/finish/recover; Room подтверждает sending до POST и итог до изменения памяти. OkHttp отправляет sale/reversal с deadline/cancel и без автоматического повтора. Частичное обновление отдельного чека сохраняет текущий возврат, финансовые поля и остальные чеки; для каждого send не сериализуется вся история. Четыре worker ограничивают параллельную нагрузку. Живые попытки защищены от reconnect recovery; прерванные sending становятся durable pending.

Существующие триггеры лояльности и backend idempotency сохранены. Они не меняют правило доступности 033: остатки повторяются только после следующей сохранённой оплаты. Backup v13 и статусы внутри чеков сохранены. 353 JS / 278 JVM тестов прошли; lint 0 ошибок / 15 прежних предупреждений. Физическая приёмка pending. Следующая граница — 087 WEB acceptance/ready журнал и ACK. [Спецификация](../specs/086-native-loyalty-journal/spec.md).

Сохранённый бизнес-вопрос: прямой вызов начисления может повторно отправить уже synced чек, тогда как reversal synced пропускается; обычный retry выбирает только разрешённые состояния. Не меняем эту политику и серверную идемпотентность молча. JS ещё отвечает за исходные триггеры/оркестрацию до native UI/runtime cutover.

## Этап 087 — WEB журнал и подтверждения

webOrderAcceptances/webOrderReadyJournal стали основными документами Room, с одноразовым импортом и защитой от устаревших shadow writes. Kotlin проверяет durable local/pending перед ACK и сетевое подтверждение перед confirmed; изменения разных записей объединяются без потери соседних. Ready статус сессии и pending intent сохраняются одной транзакцией. ACK идёт через OkHttp с отменой/ограничением ожидания, без автоматического transport retry.

Исправлен failure-window очистки: готовность удаляется только при confirmed в Room; события приёма очищаются по свежему Room журналу, а не по изменённому лишь в памяти объекту после failed save. V13/исходные printing/estimate/stock rules и retry triggers сохранены. Приём WEB items, кухня, UI и расписание восстановления ещё используют reviewed runtime; SSE/event ownership — следующий 088. 364 JS / 287 JVM тестов прошли; lint 0 ошибок / 15 прежних предупреждений. Физическая приёмка pending. [Спецификация](../specs/087-native-web-journals/spec.md).

Сохранённый бизнес-вопрос: кухня печатает до backend ACK; падение после физической печати до сохранения printed snapshot может повторить печать. Это отдельная внешняя граница 099, Room сам не гарантирует exactly-once принтер.

## 088 — primary WEB events transport

| Boundary | Automated evidence | Physical status |
|---|---|---|
| Native stream → reviewed orders/owner report handler | Actual-source JS tests; named events/rollback/late sessions | Pending |
| Framing, backpressure, Last-Event-ID, MIME/HTTP stop, lifecycle | JVM primary-reader and injected HTTP tests | Pending network transitions |

No automatic catalogue/availability sends; existing WEB normalization/merge retained. See [088](../specs/088-native-web-sse/spec.md).

## 089 — manual catalogue/media

| Boundary | Automated evidence | Physical status |
|---|---|---|
| Manual menu POST and unchanged payload/status | Actual source sync/payload; JVM bytes/routes/no retry | Pending |
| Local product save then native media upload | Actual source success/413, pending local photo; abort/timeout tests | Pending |

No catalogue/media scheduling or backup format change. Native menu deadline 60s, media remains 10s. See [089](../specs/089-native-catalog-transport/spec.md).

## 090 — availability publication

| Boundary | Automated evidence | Physical status |
|---|---|---|
| Native stock/recipe availability | Actual-source fixtures checked in JS/JVM: unlimited, invalid, duplicates, nested/tolerance | Pending |
| One attempt per persisted receipt | Real Room missing/duplicate/token/body/rollback and file-backed reopen | Pending |
| Payment-only network and native cancellation | Post-commit source trigger, JS lifecycle/no-op/failure/coalescing, HTTP route/deadline/no retry | Pending |

Current AGENTS 7 supersedes 033 ordinary-success sends; failed retry remains next-payment only. Transport rollback retains native gate. See [090](../specs/090-native-payment-availability/spec.md).


## 091 — supplier boundary

Create/edit/delete and bindings: actual source + Room tests; raw JSON/unknown fields/null/import/late shadows/reopen: native authority tests. No history cascade; user confirms existing rights. Physical acceptance pending. [091](../specs/091-native-supplier-commands/spec.md).


## 092 — purchase orders

Actual-source quantity/unit/packing fixtures + complete create/delete candidates checked by JVM. JS commit-before-state/rollback/errors and Room stale data/roles/received/atomic failed projection tests. Supply ownership/reopen expanded to purchaseOrders/receivings. No stock increase at order creation. Physical pending. [092](../specs/092-native-purchase-commands/spec.md).


## 093 — receiving confirmation

Actual-source fixtures checked independently in JS/JVM: unit packing, weighted and untracked costs, duplicate lines, negative stock, zero lines, shortage tolerance. Source tests exercise stock changing after preview, commit-before-state, failure/double tap/received guards and rollback. Room checks reject stale/tampered/duplicate commands and inject projection/final draft write failures to prove atomic rollback. Supply authority draft import/obsolete mirrors/file-backed reopen verified. [093](../specs/093-native-receiving-command/spec.md). Physical pending.


## 094 — receiving drafts

Actual-source restoration/save fixtures checked in JVM and JS, including legacy/saved/null/blank/package/cart cases. Native protocol snapshot test, ack-before-render, failed save/double tap/uncertainty/rollback, Room stale/closed orders, no-write reopen, partial save, injected SQL failure/recovery guard and file-backed reopen. Stock/cost unchanged. Physical pending. [094](../specs/094-native-receiving-drafts/spec.md).


## 095 — inventory commands

Actual-source candidates independently tested in JS/JVM: current-stock fixation, loss, scheduled/adhoc completion and later sales. Room stale/tampered/input/closed-row guards, config loader/unsaved-frequency compatibility, unfinished/duplicate refusal, and injected final-write rollback. File-backed native FIFO ownership covers all three inventory documents and obsolete shadows. Source memory follows native ack; rollback remains. Physical pending. Cancellation ambiguity retains existing behavior while question is pending. [095](../specs/095-native-inventory-commands/spec.md).


## 096 — warehouse reports

Full actual-source model fixtures checked in JS/JVM: bounds, local timezone/DST/leap days, units, prices, supplier snapshots, incomplete/invalid/legacy history, deleted/untracked products, nulls and RU collation. Room proves authoritative sources and unchanged business documents. Native protocol period capture, stale/close handling, no fallback, section/format selection freeze, PDF/monthly/admin/rollback tested. Physical pending. [096](../specs/096-native-warehouse-reports/spec.md).


## 097 — sales analytics

Actual-source fixtures compare every aggregate and date bound in JS/JVM: mixed/empty/legacy payments, later returns, names/current category, missing fields, stock valuation, numeric group ties, default/Moscow/DST/reversed/invalid/future dates. Room fixture import/projection/read verifies raw fields, authority/read-only and refund-after-period policy. Native protocol omits archives; UI tests cover hidden work, stale response/tab, retry/no fallback, date blur/scroll, non-admin rendering and rollback. Physical pending. [097](../specs/097-native-sales-analytics/spec.md).


## 098 — hall and bookings

Actual reviewed source fixtures compare native commands and Room persistence: numbering/shape/rotation, deletion/cancellation, touching/conflicting windows, cancelled/orphan edit, guest count, midnight/Moscow/DST and normalized/invalid dates. JS checks immutable form capture, acknowledgement before state, double tap/failure/uncertainty, blank edit, drag restoration and rollback. Room tests inject final-booking-write failure to prove atomic deletion, guard stale/journal/duplicate/conflict commands, preserve archived receipts/extensions, and reopen null/absent/native-owned documents. FIFO tests reject obsolete shadows. Full UI 108; physical pending. [098](../specs/098-native-hall-commands/spec.md).


## 099 — print job management

Independent actual-source fixtures compare every copy/payload for manual/automatic receipt/kitchen/shift prints, category/role/strict boolean flags, kitchenPrinted truthiness, legacy defaults, fractional/negative/zero copies and missing items/printers. JS captures one immutable intent, retains async receipt/shift APIs, handles admission/timeout/failure without retry and supports rollback. JVM queue tests cover endpoint FIFO, parallel endpoints, maximum four workers, whole-batch overflow, close and non-poisoning failures. Room verifies admission and sending persistence before TCP, read-only authoritative paid receipts, outcome/recovery transitions, bounded compact history and injected admission/sending/final-write failures. A loopback TCP test verifies exact retained test-page bytes and closed-transport refusal. Physical LAN/format/copies remain pending. [099](../specs/099-native-print-jobs/spec.md).

## 100 — settings and employee presentation

Original mounted handlers retain employee role/delete authorization, current shifts and compatible Room commands; native fields/passwords never introduce a second verifier. Stale action tokens, detached/disabled buttons, duplicate taps, role visibility, result acknowledgement, cancellation, credential clearing and blocked recovery are verified. Settings cutover imports current snapshot once, preserves full compatible JSON, CAS-checks before disk commit and ignores old mirrors; source validation/defaults/extensions and test-print timing remain. v13 full application now waits for native settings restoration. Larger import and preferences commits remain separate boundaries. Company/delivery/discount handlers keep their existing asynchronous persistence semantics. Source checksums/refresh and runtime rollback remain verified. Native light/dark previews are synthetic; physical tests pending. See [100](../specs/100-native-settings-employees/spec.md).

Verification 100: 453 JS tests and 379 JVM tests passed; 0 failed/skipped. Android lint: 0 errors / 15 existing warnings. No product APK assembled. Synthetic light/dark native previews inspected; physical tablet/printing acceptance pending.

## 101 — workspace presentation

Native normal-operation tiles retain mounted source order/grid spans, category/folder context, prices, stock descriptions and disabled state. Cart strings/totals use reviewed/native quote presentation; native renderer never parses or computes money. Buttons/tile/cart actions retain original handlers and persisted commands, including manual price/modifiers, shift/payment gates and cart-line removal identity. Pending promises are awaited; stale generations, detached targets, unrelated modal, edit mode and recovery block dispatch. Folder close/rollback restore original DOM styles. Layout drag/edit and other planned modal surfaces deliberately retain reviewed presentation; final removal belongs before 109. Source checksum/refresh parity remains verified. Physical acceptance pending. [101](../specs/101-native-workspace/spec.md).

101 verification: 463 JS / 384 full JVM passed; final workspace checks 5/5 after recovery-caption adjustment. Lint 0 errors / 15 existing warnings. No product APK assembled; physical acceptance pending.

## 102 — product-editor presentation

Native fields/actions use mounted reviewed renderers and input/change/click handlers, not executable strings. Actual-renderer tests cover complete drafts, litre/millilitre conversion without changing base units or persisted stock, readonly cost/admin rights, ingredient search/add/qty/remove/inferred yield, modifiers/product picker/deltas, dirty exit and save acknowledgement, stale identity, cross-modal pending/recovery, invalid qty and online toggle/rollback. Kotlin verifies stable dialog/field/cursor, discarded field clearing, pending lock, unsent cancel and intermediate numeric input. Preview reads are bounded/sampled/cancellable and never write media; backup remains 500 MB. 080/081 source fixtures remain authoritative. Existing synchronous deletion/saveKey and private auth/configuration helpers are not silently redesigned. Full UI ownership still has a DOM compatibility boundary until 109. Physical checks pending.

## 103 — payment presentation

Actual reviewed page/modal renderers and finalizePayment verify formatted receipt/tender/change, committed sale ordering, unchanged stock/cart/history on failure, no availability/print before commit, explicit card confirmation, current card cancel semantics, paid split readonly/back refusal, opaque stale identity and rollback. Known async payment commands are awaited and external cash-dialog progress locks underlying page until completion. Native hardware back cannot dismiss a refused paid split; uncertain persisted status disables actions and names recovery. Monetary UI reads formatted presentation; authoritative native settlement/split/delivery/availability/print engines remain. Receipt history/refunds remain 104, source runtime/formatting/DOM removal remains 109. Physical acceptance pending.

| 104 receipt history/details/returns UI | Native Kotlin shared renderer: existing 50-row Room page, selected receipt, metrics/customer/modifiers/delivery/tenders, manual print, full return confirmation/result | Source mounted handlers + existing native return authority; open-shift/duplicate/cash/stock checks, local-before-side-effects, v13/legacy fallback unchanged. Responsive panels/scroll/palettes verified automatically; physical pending. DOM runtime removal 109. |

| 105 parked/customer/loyalty UI | Kotlin shared native forms and administrator page; mounted park/resume/delete/customer/profile/gift/program/adjustment actions, live fields and known promise locks | Existing 079/084/085/086 authorities/rights/outbox, source cashier search guards, historical fields/address and print delta state preserved; admin search race recorded as future candidate. Keyboard geometry retains focus/cursor; v13 unchanged, rollback presentation only, physical pending. |

## 106 — native warehouse screens

- Kotlin presentation binds reviewed warehouse/supplier/purchase/receiving/inventory fields and mounted opaque actions; no financial formulas, permissions or v13 shapes changed.
- Date picker emits ISO date; warehouse tables retain source headers/rows and recycle visible native views. Dirty quantity/total fields flush before unit-triggered rerender; purchase keypad preserves its original conversion rules.
- Async save/confirm/back await source/native acknowledgement, duplicate gestures are suppressed, failed save retains cart/document. Source report generation sequence guards remain.
- Inventory fixation applies current stock; completion cannot overwrite later sales. Supplier rights and empty-binding filled-cart policy explicitly preserved. Cancellation stock/text ambiguity remains documented in 095/106.
- `MPosNativeWarehouseUiEnabled=false` restores reviewed Web presentation without rolling back native storage/commands. DOM compatibility 109 and physical acceptance 110 pending.

## 107 — native analytics UI

- 097 supplies sales aggregates; no finance/stock/receipt/refund/storage/v13 changes. Source formatting, admin financial visibility, category/product quantities and top-ten/order rules retained. Non-admin employee sums are omitted, payment percentages remain visible.
- Native dates/presets/period modal invoke original mounted handlers; empty/invalid period checks remain. User confirmed inline reversed-range state-before-warning behavior on 7 October 2026.
- Latest visible-period guards/error/retry/no-JS-fallback and original loyalty read/key/error rules remain. Native DatePicker locks async replacement until dismissal, with stale result rechecked after date selection; old pickers close on screen replacement.
- Recycled native chart rows and responsive cards preserve source labels/relative widths. UI flag MPosNativeAnalyticsUiEnabled=false restores presentation while retaining 097 authority. DOM 109 / physical 110 pending.

## 108 — native hall UI

- Native floor map binds reviewed table IDs via opaque mounted keys, relative placement/shape/rotation/selection and booked state. Normal mode cannot drag; editor uses original 098 pointer/native transaction handlers. Failed drag preserves persisted baseline; no double pointer-release/click effect.
- Table create/edit/rotation/delete and booking create/edit/cancel wait for acknowledged Room commands; uncertain outcome blocks retry. Table/bookings deletion remains atomic; archived receipts/parked snapshots are untouched.
- Time/duration/guest/name/phone/comment/date fields preserve source capture/defaults/stepper limits and all 098 overlap/status semantics. Direct cancellation does not add confirmation or also trigger editor. No new role/shift/capacity restriction.
- Preserve first-table side-card fallback without auto-selection: booking still needs actual selected ID. Presentation flag MPosNativeHallUiEnabled=false restores DOM without undoing 098 authority or v13. Physical 110 and active DOM/runtime removal 109 pending.

## 109: native runtime — in progress

Native Room snapshot, current-order restore, workspace routes, paired shift/employee bootstrap and native opening command are connected to production paths. WebView, DOM screen models and remaining JS orchestration are still active. See [spec](../specs/109-native-runtime/spec.md). Completion remains 107/110; physical acceptance pending.

109 session increment: Kotlin now projects restored current-order fields on production startup; existing split-draft validation and print marks preserved, no print retry. Reviewed rollback remains. WebView/navigation removal pending; 107/110 completed.

109 workspace increment: Kotlin category/folder/Back/edit decisions now active; FIFO and stale-view guards preserve current state. No business document writes. Search/tab/rendering and WebView removal still pending. Engineering completion: 107/110.

109 active-session bootstrap: production startup now reads owned shifts/employees together and uses Kotlin normalization. Native first-match active-session model prepared; synchronous JS role helpers still remain until native handlers replace them. Read-only, rollback and v13 retained. Overall: 107/110; WebView removal pending.

109 opening authority: default native form verifies the reviewed administrator
credential and creates the shift directly in Kotlin/Room through the bounded
FIFO. Credentials stay outside the WebView; expected live documents, carryover,
journal and transactional lifecycle checks remain. JS screen/post-commit effects
and other authentication remain until subsequent 109 increments. Explicit
MPosNativeShiftOpenCommandEnabled=false rollback; v13/keys unchanged. Physical
acceptance pending, engineering progress remains 107/110 (97.27%).

109 opening verification: 551 JS / 438 JVM passed, 0 failed/errors/skips; lint
0 errors / 22 warnings in unchanged files (7 are online dependency advisories).
No local product APK assembly; physical acceptance pending. Progress 107/110.


109.06 завершена: Kotlin выбирает текущую смену/сотрудника и задаёт последовательность recover → hydrate → activate → ready. Повторный запуск/импорт сериализованы; старые поколения и ответы отбрасываются. Изменение root-документов во время загрузки блокирует активацию, следующий запуск читает свежую сессию. После root-операций выбранные записи обновляются до acknowledgement, без повторной передачи истории. Foreground читает свежий root через FIFO; команды по-прежнему проверяют права в своих транзакциях. 6/20 внутри 109; 113/129 детально; 107/110 крупных этапов. Следующая задача — 109.07. JS-адаптеры исполнения recovery/hydration/effects остаются в 109.09–109.18; WebView удаляется в 109.19.

## Исправление обратной связи интеграционных тестов — 08.10.2026

[Telegram / WEB / LAN-print](CONNECTION_TEST_FIX_RU.md): corrected callback, correlated terminal result, bounded wait, duplicate/stale guards. 572/572 JS, 457/457 JVM; lint 0 ошибок / 22 предупреждения. Физическая проверка pending. Счётчики после независимого завершения 109.06: 107/110, 109 6/20, детальный план 113/129 — 87,60%; исправление подключений не закрывает новые подэтапы.

109.07 — нативная авторизация сотрудников подключена: JS authorization marker не разрешает смену роли/удаление; пароль вводится в MPosEmployeeAuthorizationDialog и проверяется в транзакции MPosEmployeeCommand. Ordinary create/edit, last-admin demotion, self/admin deletion guards и supplier permissions сохранены. Проверки: 575 JS / 464 JVM; lint 0 ошибок / 15 прежних предупреждений. Полные критерии 109.07 ещё не выполнены: editor/product/category delete и settings gates остаются. Прогресс без увеличения: 6/20 внутри 109, 113/129 детально; физические кейсы pending.


### 109.07: реквизиты организации — 08.10.2026

`MPosCompanyCommand` сохраняет реквизиты в одной Room-транзакции с проверкой
актуальной роли администратора, открытой смены и ожидаемого документа.
Понижение сотрудника, закрытие/смена смены и устаревшая форма отклоняют запись.
JS обновляет состояние только после подтверждения commit; неопределённый
результат по таймауту блокирует повтор до перезапуска, автоматического повтора нет.
Ключ `company` принадлежит Room через существующий workspace storage; старый
shadow не перезаписывает его. Импорт/экспорт v13, JSON null/отсутствие и неизвестные
поля сохраняются. Ручное редактирование по прежней логике оставляет четыре поля.
Флаг `MPosNativeCompanyCommandsEnabled=false` возвращает прежние обработчики,
сохраняя нативное хранилище. Внешний вид формы не менялся.

Добавлены JS/JVM проверки подтверждения записи, прав, конфликта данных,
таймаута и отката Room. **Локально не запускались** согласно AGENTS.md §17;
результат проверяется в GitHub Actions (см. `docs/CI_AND_APK_SIGNING_RU.md`).
Физическая приёмка ожидается. 109.07 остаётся в работе: следующие границы —
защищённые операции редактора/категорий и остальные настройки.
Прогресс: **107/110 крупных этапов; 6/20 внутри 109; 113/129 детальных задач**.


### 109.07: удаление каталога и разрешения редактора — 08.10.2026

Подключены `MPosCatalogDeleteCommand` и `MPosProductEditorCommand`.
Удаление товара/пустой категории, плиток и ссылок навигации атомарно: ошибка
любого сохранения откатывает всю операцию. Защита чеков для возврата,
использования в составе и непустых категорий читает актуальную Room.
Администратор открытой смены удаляет товар без пароля; остальные — с паролем;
категория всегда требует пароль, включая администратора. Смена не требуется
при удалении с правильным паролем — существующее разделение сохранено.

`MPosEditorAuthorization` принимает пароль только из нативного поля.
Разрешения stock/no-stock временные, привязаны к товару и исходному документу,
не входят в Room/backup и теряются после перезапуска. Поддельный JS-флаг не
разрешает запись. Сохранение карточки повторно проверяет роль, документы,
защищённые остатки/конфигурацию/WEB и политику смены типа внутри транзакции.
Разрешение погашается после commit; при известном откате сохраняется для
исправления формы. Таймаут запрещает повтор до перезапуска.

Сбор полей, рецептов/фото и последующие эффекты остаются адаптерами, относящимися
к 109.09/109.16–109.18. Новый точечный hook сохранения в product-persistence.js
обратим; sync проверяет границу до замены файлов, parity-тест восстанавливает
только точный hook/timeout guard перед сверкой исходного SHA. Импорт и backup
v13 используют существующее хранилище без editor grants. Явные флаги rollback:
MPosNativeCatalogDeleteEnabled, MPosNativeEditorAuthorizationEnabled,
MPosNativeProductEditorCommitEnabled (false возвращает прежнюю границу).

Написаны проверки native transaction rollback, смены роли, stale documents,
поддельных/устаревших grants, таймаутов и UI acknowledgement. **Не запускались
локально:** GitHub Actions выполняет JS/JVM/lint по AGENTS.md §17.
Планшетная приёмка pending. Остались settings gates, WEB toggle товара и
защищённые операции лояльности; 109.07 остаётся in_progress.
Счётчик не завышен: **107/110; 109 — 6/20; детально 113/129 (87,60%)**.


### 109.07: реализация завершена — 08.10.2026; CI pending

Оставшиеся настройки backend/Telegram и WEB toggle товара проверяют актуальную
роль нативно перед Room commit. Панель администратора, реквизиты и защищённые
проверки Telegram/WEB читают свежие права перед действием. Backend-test и ручной
catalogue sync ждут acknowledgement настроек; автоматической синхронизации нет.
Network/telegram сохраняют исходные JSON/v13 и неизвестные поля при импорте;
первый save network сохраняет уже созданный startup device key. Редактирование
Telegram по прежней логике заменяет поля и сохраняет lastMonthlyWarehouseSent.

Корректировка лояльности использует нативный ввод пароля и HTTPS, прежний endpoint,
поля и серверную проверку. Идентификатор/имя администратора и подключение берутся
из свежей Room, а не из JS. Пароль не входит в bridge, журналы и backup. HTTP
запрос выполняется после закрытия DB-транзакции вне storage FIFO; timeout 5 секунд,
автоматического повтора нет. Создание/редактирование поставщиков и незащищённые
действия сохраняют прежние права; права лояльности на сервере не ужесточены.

**Согласованное пользователем правило:** после неподтверждённой корректировки
повтор возможен только после проверки актуального баланса. Нативная отметка
mpos_loyalty_adjustment_verification_v1 создаётся до запроса, сохраняется при
обрыве/timeout/5xx и после перезапуска. Она отдельно от кассового recovery и не
блокирует продажи. Успешный ответ/явное отклонение снимают её; при неопределённом
результате только успешный нативный GET баланса конкретной программы позволяет
вернуться к ручному вводу. Форма показывает актуальный прогресс/подарки и сбрасывает
дельты; проверка баланса не отправляет корректировку. Старый token не снимает
отметку новой операции. Отметка не содержит пароль/токен Telegram/device key;
внутренний служебный ключ не меняет формат бизнес-данных backup v13.

Rollback flags: MPosNativeAdminAccessEnabled, MPosNativeAdminSettingsEnabled,
MPosNativeProductWebEnabled, MPosNativeLoyaltyAuthorizationEnabled. Прежние
обработчики доступны явно до приёмки. Подготовленная, но уже закрытая нативная
форма не запускает поздний финансовый HTTP запрос.

Критерии реализации 109.07 закрыты; **новые проверки не запускались локально**,
результат GitHub Actions и физическая приёмка ещё не подтверждены. Это завершение
инженерной задачи, а не заявление об успешных тестах или полном уходе от WebView.
Прогресс: **107/110 крупных; 7/20 внутри 109; 114/129 детально (88,37%)**.
Далее 109.08 — нативное состояние навигации, выбора разделов, поиска и Back.

### 109.08 начата: владелец выбранного раздела

`MPosWorkspaceNavigationOwner` владеет tab и revision в StateFlow на время Activity.
Первое initialize принимает стартовый раздел; повторная инициализация не заменяет
нативный выбор. `setTab` отправляет прямую команду без DOM target, и только ответ
меняет совместимую JS-проекцию и вызывает render. При быстрых кликах старый ответ
не возвращает интерфейс к предыдущему разделу. Повторный выбор не создаёт историю
Back и не увеличивает revision; поиск/корзина/смена не меняются. Таблица разделов
не ужесточена: неизвестные строки сохраняются как в reviewed handler. Нестроковый
legacy input и явный MPosNativeWorkspaceNavigationEnabled=false используют rollback.

Проверки StateFlow, повторной инициализации, быстрых кликов и изоляции заказа
написаны, локально не запускались — выполняет Actions. 109.08 в работе:
поиск, выбор/Back, нативные controls/read models и остальные DOM route targets
ещё остаются. **114/129; 109 — 7/20; 107/110 крупных**, физическая приёмка pending.


### Исправление проверок и продолжение 109.08 — 08.10.2026

По запросу пользователя воспроизведены проверки Actions локально без сборки APK:
GitHub API вернул Forbidden, поэтому результат исходных удалённых запусков
не установлен. Найдены семь JS-сбоев тестовых окружений: отсутствовали
currentShiftEmployeeIsAdmin и criticalStorageRecoveryPending; тест ручного
menu sync не ожидал асинхронного сохранения сетевых настроек. Исправлены fixture
и ожидание создания HTTP-запроса, исходные assertions сохранены. После
исправления 626/626 JS и 2/2 Python проверок версий прошли.

В 109.08 добавлен нативный запрос поиска в MPosWorkspaceNavigationOwner:
первичная инициализация сохраняет query, повторная не заменяет его; изменение
только query увеличивает revision, повторное значение — нет. Таблицы и поиск
не изменяют корзину/смену/stock и не сохраняются в v13. JS сохраняет прежнее
String(value||'') и передаёт подтверждённый запрос reviewed-фильтру плиток.
Поздние ответы после нового ввода, смены раздела/категории/папки либо очистки
поиска не меняют экран. Explicit rollback сохраняет исходный onSearch.

Полный JS-набор после продолжения: **630/630**, JVM **509/509**, без ошибок
и пропусков; lint **0 ошибок / 15 предупреждений**, Gradle BUILD SUCCESSFUL.
Это локальное воспроизведение CI по прямому запросу пользователя; результат
нового удалённого запуска Actions ещё не подтверждён. APK не собиралась. DOM-фильтрация и переходы category/folder/Back ещё
сохраняются: это частичный шаг 109.08, а не завершение этапа.
Прогресс без увеличения: **114/129**, внутри 109 **7/20**, крупных **107/110**.
Физическая приёмка остаётся pending.


### 109.08 — владелец маршрутов workspace, 08.10.2026

Kotlin владеет category/folder/edit состоянием. Подготовка перехода не меняет
выбор; одноразовое подтверждение проверяет актуальность owner, а для папки —
согласованные текущие products/posNavigation из Room. Сохранены FIFO и порядок
Back (окно папки → inline folder → root), очищение поиска и edit effects.
Обычные переходы не перечитывают каталог; бизнес-документы и v13 не меняются.
Явные rollback-флаги сохранены. Новые проверки написаны для Actions, локально
не запускались. Предыдущий 98f5f73 подтверждён успешным Actions 37744808114.
Этап 109.08 ещё в работе: native controls/read models, Android Back, DOM
route targets и согласование нового runtime остаются. Поздний accept
отменяет только свой выбор, сохраняя более новые tab/query. **114/129**, 109 **7/20**.


### 109.08 — системный Back и restart/import, 08.10.2026

Прежний порядок системного Back теперь выбирает Kotlin: import/modal/warehouse/
receiving/background после native settings. Повторное нажатие и поздние ответы
после замены окна/pause/destroy не выполняют старые эффекты. Root startup
сбрасывает временного владельца навигации; только ready разрешает новый выбор,
старые tab/search/route ответы не возвращают предыдущий runtime.
DOM presence и экранные close handlers пока остаются переходными адаптерами.
Тестовый FIFO fixture исправлен; Actions 37747195877 для 3f87071 успешен
(tests/lint и APK). Новые Back/runtime проверки ожидают следующий Actions.
Этап 109.08 не закрыт; **114/129**, внутри 109 **7/20**.


Проверка кода 8ca9dd6: [Actions 37748093813](https://github.com/mendelev-main/M-POS-Android/actions/runs/37748093813) успешно завершил job Tests and lint (JS, правила версий, Kotlin и lint). Подписанная APK собирается отдельным job. Локальные тесты/сборка не запускались; физическая приёмка pending.


### 109.08 — нативная панель workspace, 08.10.2026

Добавлены MPosWorkspaceToolbarModel и toolbarView: заголовок, «Назад»,
«Раскладка» и «Закрыть папку» формируются из Kotlin owner; имя папки — из
актуального Room posNavigation. Эти кнопки отправляют типизированную команду
непосредственно в Kotlin через FIFO хранилища, без нажатия скрытого HTML узла.
Revision и expected state защищают от старой панели и двойного перехода;
неприменённый ответ отменяет только свой принятый маршрут. Документы товаров,
корзины, оплат и смен не записываются. Категория без папки не читает Room;
модель панели кешируется между изменениями заказа, сбрасывается при навигации
и смене runtime. Сетка теперь исключает hidden плитки текущего JS фильтра.

Используется прежний MPosNativeTheme/Manrope, светлая/тёмная палитры и 48dp
кнопки. При ошибке модели остаётся reviewed presentation; явный rollback —
MPosNativeWorkspaceToolbarEnabled=false. Написаны проверки модели/Room,
типизированного нажатия, устаревшего ответа, оплаты во время чтения панели,
hidden плиток, кеширования и rollback. Локальные тесты и APK не запускались;
выполнение этого изменения ожидается в Actions, физическая приёмка pending.

109.08 остаётся in_progress: общие вкладки/поле поиска и controls редактора
раскладки ещё используют DOM, global Back ещё получает presence из адаптера.
Каталог/корзина read models относятся к 109.09. Прогресс не увеличен:
**114/129 (88,37%)**, 109 **7/20**, крупных **107/110**.

Проверка toolbar-кода `7bd7ecc`: [Actions 37750512318](https://github.com/mendelev-main/M-POS-Android/actions/runs/37750512318) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK собирается отдельным job; физическая приёмка pending.


### 109.08 — исправление подключения native UI, 08.10.2026

Проверка по сообщению с планшета выявила дефект: приложение объявляет `let state`,
а workspace/settings UI читали `window.state`. В браузере эти значения различны;
моки прежних тестов помещали state в window и пропускали дефект. Адаптеры workspace,
settings UI, platform settings и employee confirmation теперь выбирают lexical
state, сохраняя property fallback для совместимости. Regression fixtures используют
`let state` без window.state: включение экрана/кнопок, граница подтверждения принтера,
тема и подтверждение сотрудника. Это исправление интеграции, не новый закрытый этап.

Экран смены больше не исчезает при открытии нативного cash Dialog: фон остаётся
нативным, кнопки блокируются до закрытия формы, повторное нажатие не вызывает действие.
Hidden compatibility overlay отличает native Dialog от видимого HTML modal;
для видимого HTML modal native screen скрывается, чтобы не перекрывать окно.
Ошибка открытия возвращает управление; смена раздела/rollback/скрытие приложения
по-прежнему скрывают экран. Добавлены JS и native view проверки фонового слоя,
блокировки, возврата и stale replies. Формулы и сохранение cash movement не изменены.

Tests authored, GitHub Actions pending; локальные тесты/сборка APK не запускались.
Физическая проверка исправлений pending. Прогресс **114/129 (88,37%)**, 109 **7/20**;
109.08 остаётся in_progress, следующие controls — вкладки/поиск и DOM route targets.


### 109.08 — типизированный выбор категории с native плитки

Native category tile теперь передаёт openCategory + стабильный ID прямо в Kotlin
через ту же FIFO/revision проверку. Mounted HTML click handler не вызывается;
название карточки не подменяет ID. Прежние trim, очистка поиска/folder и выход из
editMode сохранены; бизнес-документы не записываются. Старый view token и повторное
нажатие отклоняются. Product/folder tiles и явный rollback пока используют reviewed
handlers; их read models ещё впереди. Написаны JS, Room и native view проверки,
выполнение следующего коммита в Actions pending. 109.08 in_progress; **114/129**.

Исправление `8855327`: [Actions 37752933255](https://github.com/mendelev-main/M-POS-Android/actions/runs/37752933255) — Tests and lint успешно (JS, Kotlin, lint); APK отдельным job, проверка на планшете pending.

Native category tile `a645d39`: [Actions 37753269784](https://github.com/mendelev-main/M-POS-Android/actions/runs/37753269784) — Tests and lint успешно. APK исправления `8855327` уже опубликована как Actions artifact M-POS-Android-release-0.1.173-1; категория собирается отдельным job. Физическая проверка pending.

## Стабильное обновление рабочей зоны — 08.10.2026

[Подробности и проверка](NATIVE_WORKSPACE_SMOOTHNESS_RU.md): обновления остатков/количества/итогов и строк корзины сохраняют Views; действие/token обновляются без пересоздания сетки. Cash/card/secondary/outline передаются в shared theme; busy блокирует повторы без серого мигания рабочих кнопок. Новые тесты выполняет Actions, физическая плавность pending. Новых завершённых migration IDs нет: 114/129 (88,37%), 109 — 7/20.


## Рабочая зона — визуальный паритет iPad, 08.10.2026

См. [перенос исходной раскладки](NATIVE_WORKSPACE_IPAD_PARITY_RU.md). Изменения ограничены рабочей зоной; CI и физическое сравнение pending. Новых завершённых этапов нет: 114/129 (88,37%), осталось 15; 109 — 7/20.


### 109.08 — открытие папки с native плитки (08.10.2026)

Folder tile передаёт openFolder + ID непосредственно в Kotlin FIFO, без вызова
mounted HTML click. Repository проверяет expected revision и актуальные products/
posNavigation из Room при подготовке и принятии маршрута; удалённая/неизвестная
папка отклоняется. Обычная hydration уже инициализирует оба owned документа;
навигация не создаёт authority markers и не перезаписывает документы из UI.

Ответ renderFolder проверяется до изменения JS projection: category должна
совпадать с выбранной, ID — строка. После принятия остаётся прежняя category,
поиск очищается, открывается folder modal; stale/некорректный ответ отменяет только
свой маршрут. Back закрывает папку без выхода из категории. Shared appearance и
retained Views актуального main сохранены. Product tile/renderer/read models пока
сохраняют compatibility path (109.09). Добавлены JS/Room/native view проверки;
Actions следующего коммита pending, физическая приёмка pending.

109.08 in_progress: native tabs/search controls, редактор раскладки и global Back
presence остаются. Прогресс **114/129 (88,37%)**, 109 **7/20**.

Проверка folder-кода `c7e976a`: [Actions 37764445102](https://github.com/mendelev-main/M-POS-Android/actions/runs/37764445102) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK отдельным job; физическая приёмка pending.
