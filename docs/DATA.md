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
