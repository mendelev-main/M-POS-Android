# Контракты реализации и приёмочные примеры

Проектные контракты для будущего кода, не существующие Kotlin API/Room tables. Имена ниже иллюстрируют границы; окончательные сигнатуры вводятся в соответствующем этапе.

Системные уточнения: [модель](DOMAIN-MODEL.md), [жизненные циклы](STATE-MACHINES.md), [commit/recovery](RESILIENCE.md), [сквозные проверки](SYSTEM-ACCEPTANCE.md). Повтор commandId с другим fingerprint отклоняется; не возвращать успех прежней команды для другого намерения.

## Команды, версии, сохранение

| Команда | Вход и атомарная запись | Результат / отказ |
|---|---|---|
| SaveProduct | commandId, draftId/productId, expectedRevision, validated fields; product + components/modifiers + opening movement при создании | ID/revision; Validation, Conflict, StorageFailure |
| SetProductStock (в SaveProduct) | productId, targetQuantity, expectedStockRevision, commandId; delta/balance/revision/result/audit вместе с изменённой карточкой; причина не требуется | новый stock; Conflict/Validation/StorageFailure |
| RenameCategory | categoryId, expectedRevision, новое имя; категория/согласованные ссылки layout | прежний ID, новая revision; Duplicate/Conflict |
| ChangeCart | sessionId, expectedRevision, actionId, кандидат строк/параметров; session/reservation projection | новая сессия/preview; OutOfStock/StaleCatalog |
| ParkCurrent / ResumeParked | commandId, current/parked revisions; перенос и очистка/активация в одной transaction | один владелец строк/резерва; NonEmptyCurrent/Conflict |
| CommitSale | commandId, session/catalog/stock revisions, payment allocations; receipt, lines, movements, session, outbox | receiptId; отказ без внешних эффектов |
| RefundReceipt | commandId, originalReceiptId, доступный объём возврата; refund snapshot + reverse movements + outbox | refundId; AlreadyRefunded/ExceedsOriginal |

UI передаёт намерение, domain проверяет поля/права, repository внутри transaction повторно проверяет revision и уникальные ключи. Ошибки типизированы для сообщения/повтора; текст исключения БД/секрет не выводится пользователю. Commit result возвращается после durable записи. Повтор commandId читает сохранённый результат, не пересчитывает новую продажу по изменённому каталогу.

Room хранит операционные данные; сумма UI — projection, а не отдельный независимо редактируемый источник истины. Нельзя сохранять резерв и корзину разными необязательными вызовами. Вычисляемый reserve допустим из актуальных sessions; материализованная версия обязана меняться в той же transaction. Domain не зависит от Compose/Android.

## Минимальные связи данных по этапам

01: Category, Product(revision/type/unit/price), RecipeComponent, ModifierGroup/Option, PhotoRef, opening StockMovement. 02: LayoutFolder/Tile, OrderSession(revision/status), OrderLine(config snapshot/revision), параметры заказа. 03: Receipt/LineSnapshot, PaymentAllocation, RefundLink, StockMovement/document references, OperationResult(commandId), durable Outbox. Это логические сущности; не требование заранее создать пустые таблицы 04–09.

Суммы/количества сериализуются без потери decimal; `Double`, округление до display precision и неявный SQLite REAL для точных денег не допускаются. Способ хранения decimal/scale закрепляется migration fixtures до 01. Foreign keys/indexes/unique constraints дополняют domain validation. Cascade delete не удаляет историю. Миграция bootstrap→первая Room БД не теряет служебные данные обновления APK.

## Регрессионные examples для реализации

| Fixture | Given / When / Then | IDs / уровень |
|---|---|---|
| CAT-01 | имя «  Чай  », сохранить → «Чай»; пустое имя/«abc» в цене → ошибка у поля, draft остаётся | P01, JVM/UI |
| CAT-02 | категория «Напитки», переименовать при injected write failure → ни частичного имени, ни потерянных ссылок | P03/P10, Room |
| UNIT-01 | 1 кг → display г →1000 г, затем обратно →1 кг; продажная цена и ledger неизменны | P04, JVM/Room |
| RECIPE-01 | выход10 шт/1 кг муки, qty2 → расход0.2 кг; A→B→A → reject | P05, JVM |
| MOD-01 | min2/max1 или min2 при1 option → reject; два одинаковых productId в группе → reject | P06, JVM |
| PHOTO-01 | есть фотоA, pickerB завершился ошибкой/БД отказала → карточка всё ещё сA после restart | P08/P10, Room/device |
| STOCK-01 | 1 кг минус1000×0.0004 кг →0.6 кг без промежуточного округления | P04/W03/S01, JVM |
| STOCK-02 | A без учёта, B с запасом10, расходB=0.1 → доступно100; B=0 без минуса → reject | P07/W03, JVM |
| CART-01 | те же modifiers в обратном порядке → одна строка qty2; иная ручная цена → отдельная строка | W02, JVM/Room |
| PARK-01 | current требует2, parked требует3, запас10 → доступно5; park current → по-прежнему5 | W03/W04, Room |
| PARK-02 | ошибка переноса current→parked → current сохранён, нового parked нет, резерв прежний | W04/W05, Room |
| DRAFT-01 | изменить рецепт с открытым draft → revalidation; restart/rotation не снимает барьер | W06/S01, Room/UI |
| SALE-01 | commit прошёл, ответ UI потерян; повтор commandId → тот же receipt и одно списание | S01/S02, Room |
| RETURN-01 | продать по рецептуR1, заменить наR2, вернуть → обратить R1 один раз | S03, JVM/Room |
| INVENTORY-01 | baseline v1, продажа+возврат вернули баланс, apply count(v1) → StaleBaseline | I02, Room |
| UPDATE-01 | открыт draft, upgrade APK с migration → draft/history сохранены; installer во время commit запрещён | S01/P10, emulator/device |

Финансовые examples S04 дополняются утверждёнными суммами/rounding перед кодом 03. Это каталог будущих тестов, не список уже выполненных проверок. Fault injection проверяет реальный repository/transaction boundary; тест, возвращающий заранее придуманную ошибку вместо записи, не доказывает rollback Room.

## Готовность инкремента и передача агенту

В запросе на код указать этап/IDs, эти contracts и конкретный fixture. Агент читает pinned handlers и adapters по [SOURCE.md](SOURCE.md), фиксирует неизвестности в context, добавляет нужные JVM/Room/UI tests и только затем меняет parity. Каждая PR содержит наблюдаемое поведение, evidence CI, миграцию при изменении схемы и physical pending. Источник не переписывается и pin не обновляется автоматически.

CI по коду: preflight/Python → JVM/lint/build → emulator UI/Room/migration и актуальные update regressions → подписанный APK; публикация stable только явным workflow. Документационная итерация: preflight, проверка ссылок/согласованности и diff-check; не выпускать APK без необходимости.
