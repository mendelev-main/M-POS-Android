# 05b.2 — справочник сотрудников и кассир смены

Статус: in progress; EF01–EF05 и ED01–ED06 ci-verified/API36; общий05b остаётся in progress. Не объявляет SA01–SA06 или полный05b завершёнными.

Источник: pinned Web1682c883 employees.js saveEmployee/employeeDeletionAllowed/employeeDisplayName, shifts.js openShiftModal/submitOpenShift, android-admin.js и regression source-product-stock.test.cjs (employee CRUD, administration session, cashier identity). Общие утверждённые решения — STAFF-ACCESS-05B.md.

## Принятая основа

Employee: стабильный ID<=100 символов без окружающих пробелов/управляющих знаков, name, phone, role employee/admin, revision>=1, deleted marker. ФИО обязательно, trim, до200 Unicode code points; телефон необязателен, trim, до64 символов; управляющие знаки запрещены. Международный/белорусский шаблон телефона не навязывается: как в Web, это контактное поле, не credential. Внутренние пробелы имени не переписываются. Повторяющиеся имена допустимы: личность задаёт ID.

Администратор и удалённый сотрудник не выбираются кассиром. EmployeeCommand save/delete связывает actor/id/revision/поля fingerprint namespace employee.*.v1. Внешний commandId отдельно; replay должен возвращать прежний результат, не создавать дубль. Mutation/result/audit сохраняются атомарно; административная сессия и installation owner проверяются внутри команды.

EmployeeCodec — payload1 для будущего workspace document employees. Roundtrip сохраняет ID/revision/role/deleted. Tombstone резервирует старый ID; молчаливого повторного использования не планируется. Corrupt/unknown/coerced payload и duplicate IDs отказываются без пустого fallback. Документ читается и записывается через EmployeeRepository, SQLschema8 прежняя. При отсутствии документа справочник пуст; чтение не создаёт запись владельца и не пишет пустой документ. Старые смены/чеки не переписываются.

Короткое отображаемое имя следует employeeDisplayName, включая составные имена через дефис; это только presentation. Полное имя должно сохраняться в историческом снимке смены, не вычисляться из текущего справочника задним числом.

## Решения владельца2026-10-29 и переход

1. Перед любой новой сменой обязательно создать сотрудника. Автоматического bootstrap «Локального владельца» нет. Отсутствующий документ означает пустой справочник; администраторы и удалённые записи не допускаются к открытию. Если кассиров несколько, выбор обязателен; если один — он выбран в подтверждении, которое всё равно требует действия пользователя.
2. Любые изменения кассира открытой смены запрещены до её закрытия: ФИО, телефон, роль, удаление и даже сохранение тех же значений. Других сотрудников можно менять. Guard берёт active actor из БД в той же транзакции, что mutation; открытие и правка/удаление сериализованы InstallationGate/Room.

Legacy смена с ownerId продолжает продажи/наличные/возвраты и закрывается прежним автором без employee record. После закрытия новая команда открытия от owner без сотрудника отклоняется. Старый committed shift.open.v1 replay возвращает исходную смену без поиска текущего сотрудника. Старые shifts.actorId/actorName и receipt.checkout.actorId сохраняются; имя чеков определяется исторической сменой, а не текущим справочником. Переименование после закрытия применяется только к следующим сменам. Вход/выход администратора кассира не меняет.

## Хранение, команды и UI

EmployeeRepository: InstallationGate → authorization → Room. Save/Delete используют stable ID, revision CAS, tombstone; документ, operation_result и audit одной транзакцией. Fingerprint не содержит commandId и связывает actor+payload. Любой write boundary/SQL abort откатывает все части. Повтор committed команды не редактирует повторно; другая нагрузка под тем же commandId отклоняется. Lost response восстанавливается outcome той же команды. Tombstone не позволяет переиспользовать ID. Удаление администратора запрещено как в Web; сначала сохранить роль employee вне активной смены. Роль directory admin не авторизует privileged mutation: нужен отдельный runtime вход.

Настройки → Сотрудники: список, ФИО, необязательный телефон, переключатель «Администратор», создание/редактирование и подтверждение удаления. Общие MposDialog/Field/Button/Theme; старое расположение полей, max width700dp и прокрутка формы. Shared back/close/discard, CAS errors и неизвестный результат без нового command ID. Logout закрывает обычную несохранённую форму; неизвестный результат остаётся доступен для проверки, но не для редактирования. Re-login не восстанавливает закрытую форму. Действующий кассир помечен и недоступен для редактирования в списке; repository guard обязателен независимо от UI.

Смена → открытие: пустой справочник показывает переход в настройки; радиокнопки только для employee. Имя/роль разрешаются из актуального документа в транзакции открытия, payload UI не задаёт имя. Snapshot смены сохраняет полный name. actor всех новых open/close/cash/checkout/split/sale/refund берётся из действующей смены. Installation owner остаётся автором административных mutation, его ID не переопределяется кассиром. Старые fingerprints финансовых команд сохранены; replay завершённых операций не зависит от текущего справочника/роли/наличия смены.

## Независимая приёмка EF01–EF05

EF01 — ФИО/необязательный телефон/точные роли и selectable. EF02 — fingerprint связывает actor/id/revision/name/phone/role/action. EF03 — Unicode и display name не меняют identity. EF04 — roundtrip сохраняет tombstones/ревизии, duplicate IDs отказ. EF05 — повреждённые/неизвестные/приведённые типы не сбрасываются. Проверяются JVM без Android/Room и без реальных контактных данных.

## Интеграционная приёмка ED01–ED06 (ci-verified/API36)

- ED01: empty install/new shift requires created employee; admin/deleted/missing ID refuses, no fallback. Legacy active shift continues and closes; original result replays, new owner open refuses.
- ED02: create/edit/delete + journal/audit/result rollback at3 write boundaries and real SQL refusal; restart/lost reply/replay/CAS/concurrent edit; corrupt document unchanged.
- ED03: all active employee edits refuse; open/delete race has exactly1 winner. Historical name preserved; next shift uses new name after rename.
- ED04: actual non-owner cashier opens/closes, cash deposit/split sale/refund without admin; other actor payload refuses. Existing cash/stock/rounding assertions retained.
- ED05: shared form optional phone, list/selection/empty setup/active lock/logout revocation; unknown-result editor lock/recovery, double click, known result with reload failure. Screenshots before marking visual QA.
- ED06: actual APK1→2 preserves directory role/phone/revision/tombstone, old/current shift names/IDs/cash, original create/rename/open replay without writes. Default new-process admin lock remains tested by existing updater scenario.

Полный05b/H03/SA01–SA06 не объявляется завершённым: общий UI revoke других разделов и оставшиеся сценарии прав/устройства отдельно. Physical tablet/dark/large text remain pending.

Локальная проверка EF01–EF05:146 JVM tests,0failures/0errors/0skipped (5 новых);14Python, harness preflight и diff check passed. API36/upgrade для runtime-интеграции пока не запускались; ci-verified статус не присваивается.
Debug APK сборка и lint прошли локально. Этап остаётся in progress.

## Наблюдаемый результат

2026-10-29: 05b.2 EF01–EF05/ED01–ED06 ci-verified/API36: [FullCI40887920523](https://github.com/mendelev-main/M-POS-Android/actions/runs/40887920523)/e63b87c SUCCESS.148JVM/228instrumentation/0failures/0skipped;14Python/harness/lint/debugAPK/testAPK.2workspace-scale/20UIseed/16upgrade fixtures и actual debugAPK1→2 passed. Empty directory refuses new owner shift; active employee any edit refused; role/admin/deleted/CAS/race/direct authorization/installation/corrupt payload guards passed.3write-boundary rollback/SQL abort/lost response/reopen/replay/tombstone tests passed. Non-owner cashier split/sale/refund/cash/close after admin logout passed; wrong operational actor refused. Legacy owner active shift continues/closes, old v1 command replay unchanged, next owner open refuses. Employee UI optional phone/create/active lock/select/logout and unknown-result/known-reload-failure scenarios passed. EMPLOYEE UPDATE PASSED: roles/contact/revisions/tombstones, immutable old/current cashier name/ID/cash and original create/rename/open replays retained without writes. Existing data/update/signature checks retained. Light component screenshots card/list/selection inspected: readable/shared controls/no overlap; actual main navigation covered by existing suite. Target APK excludes fixture credential. SQLschema8/main/signing unchanged; physical tablet/dark/large text/new process form recovery remain pending. Full05b/H03/SA01–SA06 and analytics not declared complete.

Локальная сборка e63b87c:148JVM/14Python/preflight/lint/debugAPK/testAPK passed. После исходного сбоя incremental Kotlin пересобрано с -Pkotlin.incremental=false; ранний lint запуск захватил новые ещё не скомпилированные test files (findFirCompiledSymbol), финальная сборка без изменения sources во время проверки и hosted CI прошли без подавления lint.
