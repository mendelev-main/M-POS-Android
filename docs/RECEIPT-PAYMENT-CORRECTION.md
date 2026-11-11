# Исправление оплаты и уточнения интерфейса

10.11.2026. Только локальная main, без commit/push. Текущие Web main файлы получены через GitHub connector по запросу владельца: receipts.js blob0fe0fdd48ea1ed21dabe71bd37909eaf9dcf091f, pos.html f7231bce1c52ab59ef7d5ad36d38b7f8f37cf188, receiving-ui.js b333c225b5c002f43290867ac1caa7c847a4c50f, web-orders.js3b574c4e5439afc8e52f9f68def4fbcb8bf9d27e. Глобальный reference config не изменён.

## Изменить оплату — RPC01–RPC05

Implemented locally: для native чеков три действия — Печать / Изменить оплату / Вернуть. Исправление переключает всю сумму cash ↔ card; возвращённые и split receipts исключены и на UI, и в repository. Кассир исправляет только чек текущей открытой смены; другую/закрытую смену — через отдельную административную сессию. Authority захватывается при открытии окна, logout не превращает старую admin-команду в cashier-команду.

Диалог повторяет Web: откуда → куда, номер и сумма, влияние на исходную смену, пояснение ручного терминала, Отмена / Изменить на карту(наличные). Pending сохраняет вид чисел и блокирует повторное нажатие. UUID команды неизменен до разрешения результата; после потерянного ответа читается outcome.

Одна транзакция меняет payment amounts/receipt payload, shift revision, result/audit; для закрытой смены — expectedCash/difference, сохраняя countedCash. Sale/stock/receipt total/товары/скидки/доставка/автор продажи не повторяются и не меняются. Последующие смены не переписываются. Не отправляются Telegram, печать, терминальный refund/payment или повторная loyalty operation.

ReceiptCodec2 хранит точный исходный ReceiptCodec1 JSON и последовательность исправлений. Checkout исходной продажи сохранён. После исправления банкнота и сдача неизвестны: given/change=null. Историческое expectedCashAfter внутри исходного payment snapshot сохраняется; актуальная касса/итоги смены считаются по исправленным cash/card. Refund использует исправленный способ только после проверки всей истории; общий режим приёма наличных без given не ослаблен. Native restore проверяет исходные деньги/неизменность goods и связь command results исправлений.

Native guard: исправление не может сделать текущий или закрытый расчётный остаток наличных отрицательным. Например, чек10наличными, потом все10изъяты: изменение на карту отклоняется, исходные данные остаются. Это сохранение native cash invariant; Web не имеет эквивалентного строгого запрета. Восстановленные Web historical receipts пока read-only: кнопка им не подключена; их перенос/просмотр сохраняются.

RPC04 implemented: окна печати/возврата компактные, единый footer48dp/radius16; возврат явно показывает наличные/терминал и возврат расхода курьеру. Print preview и защита от двойной отправки сохранены.
RPC05 validation: pure correction/proof/unknown tender/refund tests выполняются локально; Room fault/replay/rights/closed totals и UI fixtures только компилируются. Runtime/device acceptance pending.

## IV08 — инвентаризация

Implemented: topbar reminder приглушённо-жёлтый, active outline; shared PosControl.Warning с Web warning palette (#FFF4E9/#8A5A17/#F5DCB8 и dark #3A3022/#E9C989/#5A4A30). «Продолжить позже» warning; header actions44dp/radius16, текстовые кнопки по надписи, крестик44×44. Категория — отдельная Surface с названием/числом товаров и всеми строками; между Surface16dp. Бизнес-правила и административные ограничения прежние.

## RV08 — К приёмке

Implemented: одна колонка карточек по Web, gap18, radius16/padding20; без серой вложенной подложки у preview. «Принять» primary green, «Продолжить приёмку» Receiving yellow (#FFE08A/#6B5200/#E4BE45, dark #695015/#FFE9AA/#AA8434), высота48dp/radius16. Автосохранение черновика/приёмка не менялись.

## ON07 — входящее событие

Implemented: исходные940dp/zero padding frame, раздельные header/body/footer; products/details1.15:1 с outline sections16dp, comment red/semibold, muted delivery, ready buttons48dp/radius16 со selected semantics и стабильной appearance. Custom time отделено линией. Административная «Удалить заказ» рядом с Назад/Взять в работу, такая же высота; отсутствует в cashier UI, независима от выбора ETA. Существующий generation/target guard и silent PATCH status=cancelled сохранён; accept/ready/backend не изменялись. Реальные события ради проверки не удалялись.

Локальные evidence — harness/build-log.md. Visual/pixel acceptance владельцем в Android Studio pending.
