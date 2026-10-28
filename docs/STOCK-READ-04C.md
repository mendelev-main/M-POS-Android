# 04c.1 — остатки и история движений

Статус implemented, SR01–SR04; Android CI pending.

Вход через настройки «Остатки и движения». Список simple товаров: поиск по нормализованному имени, категория, минимум, фильтр низких остатков. Как pinned warehouse-reporting.js: low означает tracked quantity <= minimum, отсутствие minimum не равно0. Без учёта отображается отдельно и не входит в low. Composite не имеет самостоятельного физического остатка и в этот список не включается. Разные размерности не складываются.

Открытие товара читает текущий balance/revision и все его движения в одной Room read transaction; сортировка timestamp DESC/id DESC для стабильного порядка. Показаны точные decimal delta, время и источник; 0.0004 не округляется до0.001. «Перечитать» получает новый согласованный снимок. Старый снимок обозначен временем чтения. Ошибка чтения показывается явно, а не нулём/пустой историей. Back/search/category/low сохраняют контекст в Activity recreation. После cold start открывается свежий список.

Источник: pinned Web warehouse-reporting.js/warehouseReport, low filter <=minStock; Android adapter не переопределяет reporting handlers. source-product-stock.test.cjs cases1290+: read-only/no writes, historical snapshots и совместимые единицы. Native список читает ledger напрямую, включая product_card и inventory, а не реконструирует движения по текущей рецептуре. Запрос не создаёт товаров/настроек и ничего не записывает. Схема Room8 неизменна, добавлен только DAO query.

В текущем ledger отсутствует historical unit snapshot у движения. Поэтому delta показана без приписывания текущей единицы старому движению; текущая единица подписана только у текущего balance. Добавление snapshot unit/actor/document navigation требует отдельного контракта04c.2. Период, сводные beginning/end balances, связанный документ и стоимостный отчёт не входят в04c.1.

- SR01: exact current quantity/minimum/revision; <=minimum; noTracking не0/неlow; simple scope.
- SR02: stable ordered movement history, existing opening/card/sale/refund/receiving/inventory sources; read-only snapshot без повторных движений.
- SR03: недоступный товар/повреждённый balance/ошибка чтения не дают ложного успеха; повторное чтение восстанавливает экран.
- SR04: поиск/категория/low/detail/back/shared controls, навигация из настроек; удаление bookings из всех активных routes.

M17 исключён владельцем2026-10-28; это намеренное отступление от полного parity с Web. Клиенты/лояльность06 сохраняются. Физический планшет/dark/150% отдельно; следующий04c.2 — период и связанные документы после сверки source contract.
