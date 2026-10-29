# 05b.2 — справочник сотрудников и кассир смены

Статус: in progress; подготовлена независимая модель/codec, runtime integration ещё planned. Не объявляет SA01–SA06 или полный05b завершёнными.

Источник: pinned Web1682c883 employees.js saveEmployee/employeeDeletionAllowed/employeeDisplayName, shifts.js openShiftModal/submitOpenShift, android-admin.js и regression source-product-stock.test.cjs (employee CRUD, administration session, cashier identity). Общие утверждённые решения — STAFF-ACCESS-05B.md.

## Принятая основа

Employee: стабильный ID<=100 символов без окружающих пробелов/управляющих знаков, name, phone, role employee/admin, revision>=1, deleted marker. ФИО обязательно, trim, до200 Unicode code points; телефон необязателен, trim, до64 символов; управляющие знаки запрещены. Международный/белорусский шаблон телефона не навязывается: как в Web, это контактное поле, не credential. Внутренние пробелы имени не переписываются. Повторяющиеся имена допустимы: личность задаёт ID.

Администратор и удалённый сотрудник не выбираются кассиром. EmployeeCommand save/delete связывает actor/id/revision/поля fingerprint namespace employee.*.v1. Внешний commandId отдельно; replay должен возвращать прежний результат, не создавать дубль. Реальные mutation/result/audit guards ещё не подключены.

EmployeeCodec — payload1 для будущего workspace document employees. Roundtrip сохраняет ID/revision/role/deleted. Tombstone резервирует старый ID; молчаливого повторного использования не планируется. Corrupt/unknown/coerced payload и duplicate IDs отказываются без пустого fallback. Пока нет runtime чтения/записи этого документа: существующие БД/смены/чеки не изменены, SQLschema8 прежняя. Политику отсутствующего документа определяет решение о переходе ниже, codec её не подменяет.

Короткое отображаемое имя следует employeeDisplayName, включая составные имена через дефис; это только presentation. Полное имя должно сохраняться в историческом снимке смены, не вычисляться из текущего справочника задним числом.

## Два решения, ожидающие владельца

1. Переход: предложено оставить «Локального владельца» первым кассиром с прежним ID; альтернатива — после завершения legacy смены обязательно создать сотрудника перед новой. Старые документы не переименовываются и не назначаются новому человеку в обоих вариантах. Решение не предполагается по истечении времени.
2. Активная смена: предложено разрешить имя/телефон с сохранением historical snapshot, блокировать удаление/перевод в admin до закрытия; альтернатива — запретить любые изменения до закрытия. До ответа спорные mutation guards не реализуются.

После ответов: directory repository (InstallationGate → admin authorization → Room, CAS/journal/result/audit, rollback/replay); общая UI карточка/список и выбор кассира; команды open/close/cash/checkout/split/sale/refund должны принимать реальную личность кассира согласованно. Сегодня их owner-only guards остаются прежними. Вход администратора не меняет кассира.

## Независимая приёмка EF01–EF05

EF01 — ФИО/необязательный телефон/точные роли и selectable. EF02 — fingerprint связывает actor/id/revision/name/phone/role/action. EF03 — Unicode и display name не меняют identity. EF04 — roundtrip сохраняет tombstones/ревизии, duplicate IDs отказ. EF05 — повреждённые/неизвестные/приведённые типы не сбрасываются. Проверяются JVM без Android/Room и без реальных контактных данных.

Приёмка repository/UI/upgrade будет добавлена после фиксации двух правил. На этой стадии настройки по-прежнему отмечают сотрудников «Будет реализовано», приложение не обещает выбор кассира.

Локальная проверка EF01–EF05:146 JVM tests,0failures/0errors/0skipped (5 новых);14Python, harness preflight и diff check passed. API36/upgrade для runtime-интеграции пока не запускались; ci-verified статус не присваивается.
Debug APK сборка и lint прошли локально. Этап остаётся in progress.
