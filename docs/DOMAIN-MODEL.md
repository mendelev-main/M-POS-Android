# Модель данных и границы хранения

Статус: проектирование, 2026-10-24. Это логическая модель будущего Kotlin/Room приложения, не существующая схема БД. Реализованный updater описан отдельно в [UPDATES.md](UPDATES.md). Связанные требования: [DATA.md](DATA.md), [контракты](IMPLEMENTATION-CONTRACTS.md), [каталог/корзина](CATALOG-WORKSPACE-SPEC.md).

## Источники истины

| Данные | Владелец | Чего не делать |
|---|---|---|
| Каталог, рабочие заказы, продажи, движения, смены, задания эффектов | Room, после подтверждённой transaction | хранить вторую независимо изменяемую копию в preferences/UI |
| Результат расчёта текущей корзины | domain projection из согласованной версии каталога/сессии | сохранять UI-строку суммы как финансовый источник |
| Фото | private files + PhotoRef в Room | считать временный URI picker долговременным файлом |
| Неоперационные предпочтения | DataStore по этапу | помещать туда баланс склада, оплату, резерв |
| Авторизация администратора | runtime-сессия; проверка полномочий в команде | восстанавливать активный вход из backup/preferences |
| Секрет привязки устройства | отдельный защищённый adapter | помещать в receipt, outbox payload, экспорт или log |
| Updater | существующие private files/AtomicFile и служебные preferences | переносить его в Room без задачи/миграции |

Room и файлы не образуют общую transaction. Для фото/backup нужен протокол подготовки и активации из [RESILIENCE.md](RESILIENCE.md). Результат чтения из cache всегда имеет revision; cache не разрешает запись поверх более свежей записи БД.

## Идентичность и версии

UUID/стабильная строка — технический ID. Название, номер чека, позиция плитки и телефон не являются первичным ключом. Отображаемый номер может иметь отдельную последовательность с определённым scope; правила нумерации до 03 закрепляются тестами.

`revision` — монотонная версия редактируемого объекта, меняется при принятой команде; сравнивается с expectedRevision. `stockRevision` — версия складского состояния для baseline, изменяется при каждом относящемся движении даже при возврате к тому же балансу. `schemaVersion` — версия Room, `backupFormatVersion` — формат обмена, `versionCode` — APK, `catalogRevision`/`pricingPolicyVersion` — предметные версии. Эти значения не взаимозаменяемы.

Повторная команда: scope + commandId уникальны; сохраняется fingerprint нормализованного намерения и результат. Тот же ID с другим payload — Conflict, а не возврат чужого успешного результата. Внутренний ID retry внешнего эффекта стабилен, attemptId меняется для наблюдения попыток.

## Логические сущности по этапам

| Этап / сущность | Основные поля и связи | Инварианты |
|---|---|---|
| 01 Category | id, name, normalizedKey, sortOrder, color/symbol, revision | уникальный ключ, переименование сохраняет ID |
| 01 Product | id, categoryId?, name, type, sellPriceDecimal, revision, units, stock flags, photoId? | тип simple/composite; каталог не владеет исторической ценой receipt |
| 01 RecipeComponent | ownerProductId, componentProductId, quantity, unit, order | qty>0, выход владельца>0, граф без цикла |
| 01 ModifierGroup/Option | стабильные IDs, productId, min/max, option productId/qty/delta | исходные ограничения валидируются до нормализации |
| 01 PhotoRef | id, relativePath, mediaType, byteSize, checksum, state | path внутри своего хранилища; активная ссылка указывает на готовый файл |
| 01 StockMovement | id, productId simple, delta, baseUnit, source, reason optional, documentId, commandId, timestamp | opening создаётся с новым товаром атомарно; историческое движение неизменно |
| 02 LayoutFolder/Tile | id, parentId?, tileType, targetId, order, revision | category/folder не одно понятие; нет циклов папок |
| 02 OrderSession/Line | id, revision, current/parked, lineId, config snapshot, qty, catalog versions, параметры | один current на локальную кассу; один владелец каждой строки/резерва |
| 03 Receipt/LineSnapshot | id, sourceSessionId, commandId, итог/валюта/policyVersion, снимки строк/расхода | после commit неизменны; возврат отдельным документом |
| 03 PaymentAllocation | id, receiptId, method, amountMinor, currency, reference? | сумма подтверждённых частей соответствует денежной политике |
| 03 Refund/RefundAllocation | id, originalReceiptId/lineId, qty/amount, commandId | не превышает невозвращённую часть исходного snapshot |
| 03 OperationResult/Outbox | scope, commandId/fingerprint, resultId; effectId, payloadVersion, status, bindingId | операция и её эффекты фиксируются вместе, секрет не сериализуется в payload |
| 04 Inventory/Receiving/Purchase | header+lines, baseline, supplier snapshot, applied document references | закупка не есть приёмка; applied lines не отменяются удалением draft |
| 05 Shift/Employee/AuditEvent | ссылки/снимки автора, статус смены, период, action/result | автор истории сохраняется после удаления/отключения сотрудника |
| 06 CustomerRef/Consent | local/server IDs, минимальные поля, consent metadata | серверное право на подарок не подменяется локальным флагом |
| 09 RemoteOrderLink/SyncCheckpoint | bindingId, serverOrderId, localSessionId, confirmed status/version | уникальность в scope привязки, replay не создаёт второй заказ |

Поля не являются обязательством создать все таблицы в 01. Сначала реализуется нужный вертикальный сценарий. Имена/индексы/foreign keys фиксируются в schema review соответствующей PR; обязательные уникальности нельзя оставлять только проверкой UI.

## Типы чисел и единицы

Quantity = точное decimal значение + unit/dimension. Money calculation = BigDecimal + currency/policy; committed сумма = minor units Long + currency/scale по утверждённой политике. Переполнение Long или недопустимая точность — явный отказ до записи. Цена каталога тоже имеет явную денежную единицу; в 01 до финансового релиза временная политика должна быть названа/версионирована, а не угадана из locale устройства.

Для Room предлагается каноническая decimal string без exponent/locale (например `0.0004`) с единым converter; в SQL не выполнять SUM/сравнение этих строк как чисел. Агрегаты считаются domain либо отдельным представлением с доказанной точностью. Конкретный converter, предел длины/scale и индексы утверждаются перед первой схемой. `REAL`/Double для точных величин не допускаются.

Нормирование recipeYield может дать бесконечную десятичную дробь (1/3). Стратегия/fixtures01b закреплены в [CATALOG-01B.md](CATALOG-01B.md) и RecipeCalculatorTest: точные промежуточные дроби, агрегирование до decimal scale12. Display rounding не изменяет сохранённый расход. Денежные границы скидок/распределений закрываются до 03 отдельно.

## Остаток, резерв, история

Ledger — история неизменяемых движений; stock balance — сумма применимых delta. Если баланс материализован, ledger и balance/stockRevision меняются одной transaction. Сверка при тестах сравнивает их; обнаруженное расхождение не исправляется молча изменением истории.

Reservation — потребность current/parked, не StockMovement. Доступность кандидата рассчитывается после исключения прежнего резерва редактируемой сессии. Счётчик остатков composite не хранится как независимый stock; расход разворачивается до simple. Возврат восстанавливает сохранённый расход продажи, не текущий граф рецепта/флаги.

Физическое удаление запрещено при исторических/активных ссылках. Для UI отключение/архивирование требует отдельного контракта, не каскадного удаления receipts/movements. Фото истории включается в snapshot только если это действительно нужно; вечное хранение всех версий изображений не предполагается автоматически.

Для01a денежная единицаBYN подтверждена пользователем; candidate схемы, converter bounds и input limits в [FOUNDATION-CONTRACTS.md](FOUNDATION-CONTRACTS.md). До01b/03 остаются отдельные precision/rounding gates.

Stock правится в карточке без обязательной причины по решению пользователя. SetProductStock сравнивает expectedStockRevision и сохраняет delta в общем ledger; unchanged stock input не перезаписывает баланс при save других полей.

## Минимальные доказательства

P10: первая Room schema, export schema, round-trip decimal/IDs, failure transaction, restart. P03/P09: FK/удаление/rename. W03/W04: резерв без двойного расхода. S01–S03: unique commandId, fingerprint conflict, один receipt/ledger effect. I02: baseline invalidation независимо от числового баланса. B02/B05: раздельные версии и поддержанные миграционные пути. Это будущие проверки, пока planned.


Уточнение01b: precision для recipeYield закреплена в [CATALOG-01B.md](CATALOG-01B.md): exact rational intermediates, aggregate per ingredient, final scale12 HALF_EVEN; underflow — явная ошибка. Ядро подключено к Room schema2/редактору; результат CI отражён в build-log.

M17/Booking/table model исключён владельцем2026-10-28. В native-модели этапа06 остаются клиенты, согласия и лояльность.
