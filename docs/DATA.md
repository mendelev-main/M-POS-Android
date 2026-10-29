# Данные и инварианты

Логические сущности/связи и раздельные версии: [DOMAIN-MODEL.md](DOMAIN-MODEL.md). Миграции/активация backup: [RESILIENCE.md](RESILIENCE.md).

## Будущая native модель

Идентификаторы — стабильные UUID/строки. Product: name/category/type (simple/composite), selling price/cost, stock base/display units, noStockTracking, allowNegativeStock, recipe yield/components, modifier groups/options, photo ID, POS layout, web flags. Флаги сайта храним при необходимости раньше 09, но не включаем сеть. Category, LayoutFolder/Tile, CartSession/Line, Receipt/LineSnapshot, StockMovement, Shift, Outbox вводятся по этапам, не целиком в bootstrap.

Деньги в расчётах — decimal (`BigDecimal`), итоговые суммы — minor units `Long`. BYN для первого каталога подтверждена пользователем2026-10-24; input/storage defaults01a в [FOUNDATION-CONTRACTS.md](FOUNDATION-CONTRACTS.md). Часть финансовой policy подтверждена25.10.2026: net line HALF_UP2/итог из округлённых строк, полный refund/cross-shift cash. [MONEY-03](MONEY-03.md) отделяет утверждённое от pending allocations; до закрытия применимых вопросов не выпускать финансовые операции. Валюта не определяется locale устройства; price input01a ограничен2 знаками, что не задаёт rounding политики будущей продажи. Проценты и дробные количества умножать decimal, округлять на определённых границах, не после каждого промежуточного действия. `Double` и UI-строка не являются источником сохранённой суммы.

Склад — decimal quantity с явной базовой единицей; отображаемую точность хранить отдельно. Запрещено округлять движения до 0.001. Масса кг↔г и объём л↔мл совместимы; масса↔объём и шт↔кг без отдельного контракта запрещены. Цена продажи не пересчитывается при смене display unit. Исторические движения не переписываются. Precision неконечного деления recipeYield (например,1/3) закреплена в [контракте01b](CATALOG-01B.md): точные промежуточные дроби, итоговый decimal scale12 HALF_EVEN.

Native решения для первоначального каталога и opening movement: [ADR-003](DECISIONS.md), [contracts](IMPLEMENTATION-CONTRACTS.md). Уточнение пользователя2026-10-24: остаток редактируется в карточке без обязательной причины. Внутри создаётся delta movement source=product_card и проверяется expectedStockRevision; прямое изменение UI не обходит ledger/transaction.

## Подтверждённые правила Web для переноса

- `noStockTracking` применяется к простому товару, исключая только его из ограничений/списания даже внутри вложенной рецептуры. Другие ингредиенты ограничивают доступность.
- `allowNegativeStock` по умолчанию false, разрешает дефицит только конкретного простого товара. Запас реально уходит в минус, не обнуляется; отключение флага минус не исправляет. Состав проверяет каждый ингредиент отдельно. Доступность сайта не превращается в бесконечность.
- Composite не получает собственный складской остаток; расход компонентов нормируется на recipeYield. Вложенность и модификаторы участвуют в расчёте, циклы запрещены.
- Возврат использует `stockConsumption` из продажи, включая прежние рецептуры и флаги, и защищён от повторения.
- Резервы текущей/отложенных корзин уменьшают видимую доступность, но не являются повторным фактическим списанием при оплате.
- Запись финансовой операции предшествует UI успеха/печати/сети. Ошибка записи — отказ; повтор должен быть идемпотентен.
- Инвентаризация сверяет version baseline; продажа+возврат с тем же числовым остатком всё равно инвалидирует baseline. Отмена черновика сохраняет уже сделанные корректировки и их историю.

## Перенос Web backup (этап 07)

Не читать sandbox соседнего package. Пользователь выбирает файл через Storage Access Framework. Сначала parse/validate/version/relations/units и preview, затем атомарный импорт в БД с staging для фото. Ошибка не оставляет наполовину импортированные продажи. Администраторская сессия не импортируется. Неизвестные поля/форматы — отчёт, а не молчаливое удаление. Проверить round-trip на синтетических fixtures v13; реальный backup в Git не хранить.


Уточнение01b: precision для recipeYield закреплена в [CATALOG-01B.md](CATALOG-01B.md): exact rational intermediates, aggregate per ingredient, final scale12 HALF_EVEN; underflow — явная ошибка. Ядро подключено к Room schema2/редактору; результат CI отражён в build-log.

03b.2b вводит Room7: receipts (unique command/session/shift+number) и historical receipt_products FK RESTRICT. Receipt payload1 хранит cart2/checkout1, округлённые суммы/платежи/сдачу/доставку, historical ingredient graph/flags/costs/category и exact consumption. Current/checkout очищаются только той же sale transaction, result/audit и stock ledger входят в commit. Доставка хранится в receipt.deliveryCashOut один раз; shift currentCash выводится из opening + receipt.cash − deliveryCashOut. Закрытие сохраняет expected/count/difference. UI оплаты/истории ещё отдельно; статус evidence в SALE-03B/build-log.

03b.3a добавляет split-progress workspace payload1 без Room migration: SavedCheckout +confirmed indices, монотоннаяrevision/tombstone, deterministic part operation keys иaudit. Receipt/stock/cash postings толькоfinal Sale, progress consumption атомаренсними. PendingCash=sum(amount) подтверждённыхcash частей (неgiven); currentCash считает завершённые продажи, closeShift приactiveprogress запрещён. Матрицаinitialparts послеfirstconfirm immutable доfuture unpaid-editor API; UI покане создаёт progress. Future warehouse/import writers должны использовать тежеguards/резервы, анеобходитьихчерезDAO.

03b.3b.2: split-progress2 хранит ordered partIds/nextPartId и confirmed indices в checkout snapshot. Переход1→2 только в транзакции новой команды; stable-ID aliases сохраняют прежние operation results. Ключ новой части =checkout commandId+part ID, fingerprint включает frozen context+target payment без mutable unpaid matrix/revision. Edit атомарно повышает checkout/progress revisions и пишет result/audit; null given допустим у unpaid, никогда у confirmed cash. Schema7/checkout1/receipt1 не меняются. Подробности и ограничения — SALE-03B.

03b.3c: CheckoutRepository.saveSplit сохраняет nullable given как unpaid input, без подстановки принятой наличности. Строгие normal save/final payments сохранены. До first confirm checkout-only строки имеют предварительные ID; после него IDs/nextId —durable progress2. SaleUi читает checkout/progress/work/shift/result одной транзакцией; несовпадение progress и checkout не превращается в новую оплату.


03c.2a Room8 adds immutable refunds, unique original receipt/command and foreign keys to receipt/refund shift. Refund snapshot1 retains original receipt, exact restored quantities/balance revisions and cash outcome. Active cash includes deliveryCashCredit−refund.cash in refund shift only; original sale postings remain. Full refund transaction advances register revision and each restored balance revision; no generic duplicate withdrawal. Evidence/scope: REFUND-03C/build-log.

04a.1: Supplier registry payload1 в Room workspace_documents namespace `suppliers`, SQLschema8 без изменений; supplier revisions/tombstones/simple refs + operation_result/audit в одной transaction. Формат и guard каталога — [SUPPLIERS-04A](SUPPLIERS-04A.md). Registry включать в будущий backup scope, не считать его временным workspace cache.

04a.2: persistent `purchase-draft` payload1 в workspace_documents/Room8: raw decimal fields + supplier/product snapshots + CAS document revision. Save/Clear+result+audit атомарны, stock/cost/defaults не меняются; [контракт](PURCHASE-DRAFT-04A.md). Включать в будущий backup scope; не cache.

04a.3: persistent `purchase-orders` payload1/Room8: immutable request snapshots, raw packaging +expected base/null, order/requestVersion1/pending; append+catalog purchase defaults revisions+draft clear+result/audit atomарны. Stock/cost не меняются. Historical product/supplier deletion guards и [контракт](PURCHASE-ORDERS-04A.md); включать в будущий backup.

04a.4: `purchase-orders` payload2 reads payload1/SQLschema8 unchanged. Separate order revision/requestVersion, previous request snapshots and cancelled actor/time; mutation+result+audit atomic, independent purchase-draft CAS untouched. Guards include historical removed lines. [Contract](PURCHASE-ORDER-CHANGES-04A.md).

04a.5: persistent `receiving-drafts` payload1/Room8: per-order draft revision/tombstone, order snapshot +raw invoice facts +product/stock/cost baseline. Registry/result/audit atomic; Save allows stale economic baseline, preview refuses it until explicit rebase. No stock/cost/order writes. [Contract](RECEIVING-DRAFT-04A.md); include in future backup.

04a.6: persistent `receivings` payload1/Room8 stores immutable invoice/order/economic baseline and posted changes/totalMinor/actor/time/command. Orders payload3 reads1/2, terminal received link/actor/time; receiving draft tombstone CAS advances atomically with product cost/revisions, tracked stock/ledger, result/audit. Policy-v1 final cost12 HALF_EVEN, positive economic underflow refused; line totals exact minor units. [Contract](RECEIVING-POST-04A.md). No cash movements.

Уточнение владельца2026-10-27: новая receiving cost policy2 —2fraction HALF_UP (3шт/10BYN→3.33; invoice total10.00 exact). Приходы payload2 reads1, per-record policy; прежние документы/result/replay/остатки неизменны. Quantity precision и другие операции не меняются. Подробности: RECEIVING-POST-04A.md, актуальная policy2 supersedes policy1 для новых приходов.

04a.7a: receiving-drafts payload2 reads1; ReceivingDraft.order nullable +optional ReceivingSupplier snapshot, reserved standalone slot `receiving:standalone`. Existing order fingerprints preserved; immutable history may contain many standalone receipts (unique invoice IDs), order uniqueness only for order refs. Existing receivings payload2 supports nullable order in nested invoice version2; no SQL migration. Reference guards cover standalone draft/history. See RECEIVING-STANDALONE-04A.md.

04a.7b: владелец подтвердил line quantity×unitPrice HALF_UP2, затем sum rounded lines (1.5×2.35→3.53; две строки7.06). Receiving draft payload3 reads1/2, priceMode+unitPrice raw; единственный источник суммы зависит от режима. Старые total fingerprints/replay неизменны, price fingerprint содержит режим/цену. Контракт RECEIVING-STANDALONE-04A.md.

04b.1: inventory payload1/SQLschema8, config+optional active draft scope/id/type/start timestamp/scheduled calendar date/product snapshots. CAS/result/audit atomic; old payload absence means disabled config. Snapshot on start informational, no physical count authority. Include durable document in future backup; no reset on corrupt/unknown version. Product references protected; stock/cost/cash unchanged. See INVENTORY-04B.md.

04b.2: inventory payload2 reads1, counts raw+stock baseline/process generation, immutable fixed product/actual/delta/actor/time/command/movement/stockRevisionAfter. Count3writes;Fix5writes, zero included, cost/cash unchanged. Tracking off/on now increases balance revision even when product-card stockEdited=false; no stock delta for tracking toggle. Legacy Configure/Start fingerprints retained. See INVENTORY-04B.md.

Inventory close04b.3: history payload3 reads1/2, loss policy1=negative delta × cost-at-Fix rounded HALF_UP2 per row then sum cents, surplus separate/unfixed excluded. Overflow=null/unavailable, never0. Close only persists history/marker/tombstone/result/audit, no stock/cost/cash writes. Scheduled marker actual local completion date; cancel/adhoc unchanged. See INVENTORY-04B.md.

04c.2: StockPeriod is transient UI state, inclusive local dates mapped to [start,next-day-start) using snapshot Clock.zone (DST safe); current balances unchanged by filtering. StockDocumentReader follows existing reason+source links to historical receipt/refund/receiving/inventory snapshots and verifies product/delta/command/time (inventory movementId too). Read-only, no new payload/schema. See STOCK-READ-04C.md, SD01–SD05.

05a: immutable manual cash journal `shift_cash` workspace payload1, SQLschema8 unchanged. Missing document means empty legacy journal; corrupt/unknown payload is refused without reset. Deposit/withdrawal stores exact positive minor units, optional normalized note, actor/shift/command/time and validated before/after. InstallationGate + Room commits journal/register revision/result/audit atomically. Current cash aggregates opening, finalized sales/refunds/delivery and manual movements exactly once; closed expected/count/difference remain stored, next opening carries counted cash without replaying the old journal. Original command replay returns its original movement even after closing/updating. Include this document in future backup. Contract: [SHIFT-CASH-05A](SHIFT-CASH-05A.md).

05b.1: admin session/attempt limiter process-memory only, not Room/workspace/preferences/SavedState/backup. Salted embedded PBKDF2 verifier is APK configuration chosen explicitly by owner, not plaintext/device credential. Admin authorization spans protected Room commit and serializes logout; operational shifts/receipts/cash actors remain unchanged until employee-directory block. SQLschema8 and durable payloads unchanged. See STAFF-ACCESS-05B.md.
