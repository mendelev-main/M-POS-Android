# 05b — сотрудники и административный доступ

Статус на2026-10-30: 05b.1 A01–A04,05b.2 ED01–ED06 и05b.3 AR01–AR05 ci-verified/API36. Заключительная сверка05b.4 выполняется; новый matrix test ещё не имеет CI evidence. Полный этап05 включает будущие отчёты и не закрыт.

## Источник и решения владельца

Pinned Web1682c883: features/employees.js, features/shifts.js, android-admin.js и tests/source-product-stock.test.cjs (closing administration blocks stale deletion confirmations). История исследования и проверок — harness/context/05-shifts.md и harness/build-log.md. Инструкции разработки источника не применяются к native автоматически.

Две группы: employee и admin. Сотрудника выбирают для новой смены без личного пароля; admin не может стать кассиром новой смены. Перед новой сменой обязательно создать сотрудника: «Локальный владелец» не создаётся скрыто в справочнике. Старая открытая смена сохраняет прежний ID/автора, закрывается по прежнему контракту; новая требует действующего employee. Любые изменения кассира открытой смены запрещены до закрытия, включая имя/телефон/роль/удаление. Имена в сменах и чеках — исторические снимки.

Администрирование — отдельная runtime session, не роль текущего кассира. Вход не меняет кассира/кассу/историю. Background и recreation сохраняют вход; logout/process death сбрасывают. Автоматический timeout не вводился.

Владелец выбрал заданный общий пароль через встроенный salted PBKDF2-HMAC-SHA256/600000/256bit verifier; plaintext не хранится в source/resources/Room/backup/log. Masked ввод только в памяти, без rememberSaveable, очищается при submit/close. Constant-time digest comparison, PBKDF2 вне UI thread. После5 ошибок runtime limiter блокирует вход30секунд; попытки сериализованы. Process restart сбрасывает limiter. Общий verifier в APK допускает offline подбор и не защищает от модификации APK/root. Смена credential — новая сборка; экран установки/смены/восстановления пароля и скрытый reset не предусмотрены. Это принятое владельцем отличие от прежнего предложения собственного пароля при первом запуске.

## Реализованные блоки

- 05b.1: проверка credential, runtime session, indicator/login/logout, guards repository. A01–A04: [CI40859068370](https://github.com/mendelev-main/M-POS-Android/actions/runs/40859068370).
- 05b.2: справочник, атомарные audit/result/CAS, employee selector, trusted cashier resolution при commit, историческая совместимость. ED01–ED06: [CI40887920523](https://github.com/mendelev-main/M-POS-Android/actions/runs/40887920523), [контракт](EMPLOYEES-05B2.md).
- 05b.3: отзыв transient форм и исходной authority очереди; known outcome можно читать, отсутствующий нельзя отправлять под новым входом. AR01–AR05: [CI41028916124](https://github.com/mendelev-main/M-POS-Android/actions/runs/41028916124), [контракт](ADMIN-FORMS-05B3.md).
- 05b.4: сверка всех текущих команд и UI, матрица прав, acceptance mapping и итоговый API36/actual APK upgrade. [Матрица](CAPABILITY-MATRIX-05B4.md).

## Граница авторизации

AdminAuthorization содержит process-only ссылку на session и generation. Смена epoch не меняет fingerprints/Room/backup. AdminTransaction проверяет session identity/generation/active под mutex на всё время commit. Порядок mutex: InstallationGate → authorization → Room. Не вызывать adminTransaction из внешней Room transaction. Каталог/layout сохраняют прежнюю границу InstallationGate; этот блок её не расширяет.

Logout, выигравший mutex, запрещает старую запись; уже допущенная транзакция заканчивается перед logout. Старые формы/подтверждения закрываются и не возвращаются после relogin. Durable drafts, история, stock и результаты команд сохраняются. Outcome читается отдельно; прямой повтор административного commit после logout не является обходом проверки даже для известного commandId.

ActorId административного audit остаётся локальным ownerId: общий вход не идентифицирует конкретного администратора. Role из payload/UI и знание ownerId не дают доступа. Операционные actor/employee/shift checks читают актуальный справочник и открытую смену в транзакции. Admin login не выдаёт разрешение продать без действующей смены.

Test runner имеет отдельный фиктивный verifier только в androidTest APK. Target debug/release начинается locked; Intent/BuildConfig bypass отсутствует. Test helper явным действием создаёт кассира, production bootstrap этого не делает.

## Приёмка SA01–SA06

| ID | Действующее требование | Evidence / граница |
|---|---|---|
| SA01 | Кассир выполняет разрешённые операции своей смены; прямой admin commit без входа не пишет данные; role/owner payload не повышает права | AdminAccessTest, EmployeeRepositoryTest; полная матрица дополнена05b.4, CI pending |
| SA02 | Logout закрывает формы; очередь не использует новый login; граница logout/commit определена | AdminFormsTest, AdminFormRepositoryTest, AdminFormViewModelTest, AdminRevocationUiTest; AR01–AR05 ci-verified |
| SA03 | Неверный credential не меняет кассира/кассу; exact verifier/limiter/очистка ввода | A01–A04 ci-verified; изменение/recovery через UI исключено решением владельца |
| SA04 | Employee save/delete + audit/result атомарны; CAS/rollback/replay; исторический автор неизменен; активный кассир неизменяем | ED01–ED06 ci-verified, EmployeeRepositoryTest/EmployeeUiTest/EmployeeUpgradeTest |
| SA05 | Recreation сохраняет runtime; новый процесс locked; authority/credential не сериализуются | A/AR ci-verified и actual update; background следует process-memory lifecycle; physical tablet pending; пользовательского backup пока нет |
| SA06 | Actual update сохраняет старые смены/чеки/кассу/журнал/employee directory; новый процесс locked; replay не дублирует эффект | CI41028916124: ADMIN SESSION/EMPLOYEE/SHIFT CASH/SALE/REFUND и остальные UPDATE PASSED |

H03/H04 относятся только к существующим возможностям. Будущие reports/export/backup/integration получают guards и свои тесты при реализации; их нельзя объявить проверенными сейчас. ACC-04/05/07 и SYS-08/09/10 в полном backup/integration объёме остаются отдельными gates. Physical tablet/dark/large-font не объявляются завершёнными.
