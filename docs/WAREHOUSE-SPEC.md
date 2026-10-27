# Поставщики, закупки, приёмка и инвентаризация

Паспорта M10/M11/M12, этап 04; planned. Расширяет [OPERATIONS-SPEC.md](OPERATIONS-SPEC.md), не добавляет автоматическую готовность. Pinned Web источники: `suppliers.js`, `purchase-orders.js`, `receiving-drafts.js`/`receiving.js`, `warehouse-reporting.js`, `android-inventory.js`. Средневзвешенная cost policy подтверждена владельцем2026-10-27; дополнительные расходы/сторно требуют отдельного контракта.

## M10: складское ядро до складских экранов

Opening movement01a, units/graph01b, cart availability/reservations02 и sale/refund03 используют один доменный stock contract. Экраны 04 только дают дополнительные команды/queries; второй алгоритм расхода для инвентаризации/сайта не вводится.

Вход calculation: согласованные catalog/config snapshots, base balances/revisions, остальные sessions и кандидат заказа. Выход: разложенная demand по simple, дефицит/доступность с причиной. Computation не меняет balances. Apply movements: command context+source document, expected revisions; ledger/balance/revision/result в transaction. Reversal ссылается на исходные movements.

## M11: поставщик

Поставщик: ID, имя, контактные поля по утверждённому списку, список productId, активность/revision. Экран списка → карточка → товары поставщика. Создание/изменение проверяет имя/ссылки. Список закупочных товаров не меняет состав рецептов и не включает composite как складскую единицу без отдельного контракта.

Web подтверждает административную проверку удаления и её повтор при confirm. Native: проверить capability в use case, запретить физическое удаление при исторических документах/активных закупках; отключение поставщика — отдельное действие. Старые документы хранят supplier snapshot. Это безопасное native правило, а не обещание одинакового delete Web.

## M11: заказ поставщику

CreatePurchaseDraft → выбрать поставщика → добавить simple строки → requestedQty/unit/pack coefficients → preview expected baseQty → Save/Submit по принятому workflow. Черновик не меняет stock. В Web встречается expectedQty=null, когда содержимое упаковки уточняется при приёмке; native должен явно показывать «Уточнить при приёмке», не считать null нулём.

После Submit snapshots заказа и ревизии отделяются от поступлений. До подтверждения приёмки можно редактировать ожидающий заказ с проверкой revision. Одно подтверждение закрывает заказ: requested/actual/shortage сохраняются отдельно;3из10 означает received с недопоставкой7 без дальнейшего открытого ожидания. Получение сверх requested требует явного подтверждения. Отмена ожидающего заказа не обращает другие проведённые приходы. Решение владельца2026-10-27 заменяет первоначальный план последовательных частичных приёмок.

Команды: SavePurchaseDraft, SubmitPurchase, CancelPendingPurchase, OpenReceivingFromPurchase. Повтор submit с тем же commandId возвращает прежний orderId, не вторую закупку. Active receiving draft привязан к revision закупки и сверяется перед commit.

## M11: приёмка

ReceivingDraft содержит supplier/order refs, строки productId, received quantity/unit/coefficients, price/cost data, revision. Вход из закупки подставляет requested, но оператор подтверждает фактический объём. Независимая приёмка допустима только если выбранный workflow разрешён явно.

Preview показывает изменения base balances и стоимости. CommitReceiving атомарно сохраняет receiving document/line snapshots, stock movements/balances/revisions, итог requested/actual/shortage закупки, result/audit и очищает draft. Ошибка любого шага оставляет прежние balances/статус заказа; повтор команды не получает товар снова. Изменение каталога/коэффициента упаковки после открытия требует revalidation, не меняет сохранённый receiving задним числом.

Неизвестная упаковка не подтверждается как 1 шт. Возврат поставщику/исправление подтверждённой приёмки требует связанного обратного документа; не реализуется удалением старого ledger. Нужность этого workflow и пределы отрицательного correction закрепляются перед D03, не считаются покрытыми ordinary receiving.

## M12: физическая инвентаризация

Начать draft → выбрать scope товаров → ввести actual с baseline → зафиксировать строку → продолжить/завершить/отменить остаток. Baseline: product/unit/tracking mode, stockRevision и session/generation подсчёта. Пустой ввод не равен 0. Конфликт сообщает какой товар пересчитать; actual не подставляется поверх новых движений.

Количество 0 — валидный подтверждённый пересчёт; отрицательное actual требует отдельной принятой stock policy. После process restart неприменённый physical baseline становится невалидным и запрашивается новый ввод. Applied строки имеют documentId и неизменны. CancelRemainder фиксирует историю cancelled и перечень applied/unapplied, не стирает движения.

## Queries и доступ

StockList/MovementHistory/LowStock/PurchaseStatus/ReceivingHistory/InventoryHistory — read models. Показывают единицы, применённый период, filter и текущую revision. Один товар в разных физических размерностях не суммируется. Правка stock в карточке без причины разрешена пользователем: общая SetProductStock команда пишет delta/revision/result, UI не обходит ledger. Произвольная таблица не пишет balance напрямую. Права supplier/manage, receiving/commit, inventory/apply проверяются отдельно.

## Перед D03 и приёмка

Средневзвешенная valuation/закрытие при недопоставке/явный over-receiving подтверждены ниже. Отдельно утвердить дополнительные расходы и отмену/коррекцию проведённой receiving. Собрать synthetic fixtures, не реальные накладные.

WH01:2×12×0.5 л=12 л; WH02:получить 3 из 10, повтор command →received3/remaining7; WH03:отменить remainder →stock полученных 3 сохранён; WH04:unknown pack →block commit; WH05:concurrent sale после count →StaleBaseline; WH06:cancel после applied →история и ledger остаются; WH07:write failure →receiving и stock не разошлись. Mapping I01–I04/S01; tests planned.

## Уточнение владельца2026-10-27: приёмка как Web

Подтверждено:10шт×2BYN на складе +10шт общей стоимостью40BYN →средневзвешенная себестоимость3BYN. Частичное фактическое получение3из10 **закрывает закупку с недопоставкой7**; ожидание7 автоматически не переносится, отдельного открытого remainder нет. Это заменяет прежние предположения этого документа о последовательных частичных приёмках. Получение12вместо10 допускается только с явным подтверждением превышения; stock+12, ожидание0.

WH02 уточнён: receive3/10 закрывает заказ, повтор той же команды не добавляет ещё3; новая приёмка по уже закрытому заказу запрещена. WH03 теперь означает отмену ещё не принятого заказа без обращения других уже проведённых приходов; редактор после частичной фиксации не нужен, поскольку заказ закрывается одним подтверждением. Исторический заказ хранит requested/actual/shortage и подтверждение over-receive. Сторно проведённой приёмки/дополнительные расходы остаются отдельными будущими контрактами, их код не включать в первый инкремент поставщиков.
