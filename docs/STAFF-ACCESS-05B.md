# 05b — сотрудники и административный доступ

Статус: in progress; 05b.1 административная сессия и guards implemented, CI pending. Решения владельца получены2026-10-29. Справочник сотрудников/выбор кассира05b.2 ещё planned. Это контракт подготовки, не evidence реализации H03/H04 или ACC-01–ACC-07.

## Источник и текущая граница

Pinned Web1682c883: `Web/js/features/employees.js`, `shifts.js`, `docs/ADMINISTRATION_SESSION_RU.md`; regression `tests/source-product-stock.test.cjs` (administration session, stale confirmation, employee deletion). Документация источника содержит собственные инструкции разработки; они не применяются к native репозиторию.

Web: две группы employee/admin; администраторы исключены из открытия кассовой смены. Администрирование — отдельная runtime session с indicator и logout, не выбор кассира. Background сохраняет session; новый runtime требует входа. Logout отзывает доступ старых форм. Employee name/phone/role сохраняются; удаление кассира активной смены/администратора запрещается, история сохраняется. По явному решению владельца native использует тот же заданный общий пароль через salted verifier; открытая строка не хранится.

Native сейчас: ShiftRules.open/close и ShiftCashRepository, SaleRepository, RefundRepository используют ownerId; actorName открытия фиксирован как «Локальный владелец». CatalogRepository пишет audit от ownerId. Поэтому добавление employee selector само по себе не даёт разграничения доступа. Исторические документы/operation results уже выпущены и должны оставаться читаемыми и воспроизводимыми без переписывания автора.

## Решения перед исполнением

1. Подтвердить две группы и полномочия кассира, включая возврат, скидку/ручную цену, внесение/изъятие. Базовый Web-контур: каталог, склад, сотрудники, настройки — admin; продажа и cash — активная смена.
2. Выбрать credential: предложен собственный пароль при первоначальной настройке; альтернатива PIN/личный вход сотрудников. Общий встроенный credential запрещён действующим native контрактом.
3. Подтвердить runtime lifecycle: предложено Web-поведение (background сохраняет, logout/process death сбрасывают).
4. После выбора credential определить смену/восстановление доступа без стирания кассы, первичную настройку существующей установки и ограничения попыток входа. Не реализовывать скрытый универсальный пароль или автоматический сброс вместе с бизнес-БД.

## Порядок реализации после решений

- 05b.1: схема employee directory, стабильные IDs/ревизии, historical compatibility; bootstrap/credential verifier и runtime session. Подготовить явный переход существующего owner, не назначать старую историю новому сотруднику автоматически.
- 05b.2: trusted actor context и capability checks в изменяющих командах. Проверять session/revocation при commit, а не только при открытии формы. Сериализовать смену авторизации и защищённую запись так, чтобы logout до commit запрещал её, а committed результат оставался фактом. Actor audit и кассир чека/смены различаются.
- 05b.3: shared UI управления сотрудниками и входа/выхода, индикатор сессии, выбор кассира при открытии смены. Запретить опасное изменение сотрудника активной смены; зафиксировать поведение деактивации/смены роли до реализации.
- 05b.4: полная проверка команд, UI и реального обновления APK; только после неё статус ci-verified. Последующие отчёты 05c/05d не входят автоматически.

Детали credential storage и криптографические параметры выбрать по актуальной официальной Android/криптографической документации после определения типа входа. Plaintext credential не хранить в Room/preferences/backup/log/audit. Runtime token не сериализовать. Backup/restore не восстанавливает активный вход.

## Приёмка SA01–SA06 (planned)

- SA01: кассир успешно продаёт в своей разрешённой смене, но прямой вызов admin-команды отклоняется без записи. Роль из UI payload не повышает права.
- SA02: открыта форма товара → logout → Save отклонён; новая runtime session не делает старую форму автоматически доверенной. Revocation/commit race имеет определённую границу.
- SA03: неверный credential не меняет смену, кассира, историю или права; проверены установка/смена credential, ограничение попыток и согласованное восстановление доступа.
- SA04: employee create/edit/deactivate + audit/result атомарны; отказ записи сохраняет старые данные, повтор команды не дублирует. Исторический автор не меняется при переименовании/деактивации.
- SA05: recreation сохраняет разрешённый runtime context, новый процесс не получает admin session; background соответствует выбранной политике. Нет credential/session fixtures в backup/log/audit.
- SA06: actual APK update сохраняет старые смены/чеки/кассу/журнал и employee directory; admin login отсутствует в новом процессе. Legacy replay не создаёт повторных денежных эффектов и не обходит guard для новой команды.

Физический планшет остаётся отдельной приёмкой. До завершения применения guards приложение нельзя описывать как защищённое от действий кассира.

## Решения владельца и блок 05b.1

Подтверждены две группы employee/admin, отдельный вход администратора без смены кассира, background сохраняет сессию, logout/process death сбрасывают. Владелец явно выбрал заданный общий пароль вместо первоначальной настройки собственного; это переопределяет прежнее проектное предложение. Salted PBKDF2-HMAC-SHA256/600000/256bit, constant-time digest comparison; salt/verifier в APK не являются секретами. HashCode/быстрый SHA не используются. Общий credential можно подбирать/проверять вне приложения; встроенный verifier не защищает от владельца/root или модификации APK. Plaintext не входит в source/resources/DB/backup; UI input только в памяти, masked, без rememberSaveable, очищается при submit/close. Сменить общий credential сейчас можно только новой сборкой; скрытого reset/backdoor нет.

Session process-only StateFlow, monotonic runtime throttling: после5 ошибок блок входа30 секунд, параллельные проверки сериализованы; process restart сбрасывает limiter, это не защита от offline brute force. Проверка PBKDF2 вне UI thread. Authorization mutex охватывает Room commit; logout, выигравший mutex, запрещает запись, допущенная ранее запись завершается до logout. При нескольких mutex порядок InstallationGate → authorization → Room; каталог/layout не получают InstallationGate в этом блоке (прежняя граница не менялась). Не вызывать adminTransaction из внешней Room transaction. Новая сессия не меняет ownerId, actorId открытой смены и исторические snapshots. Назначение employee actor — следующий блок.

Guards на всех уже существующих изменяющих admin entry points: product/category create/update/delete/reorder, product draft save, home/layout, supplier, purchase draft/order, receiving draft/post, inventory configure/start/count/fix/finish/cancel. Чтение/initialization и операционная корзина/оплата/возврат/shift/cash остаются доступны по прежним single-owner contracts. app.update пока без роли по прежнему договору. Каталог читается без admin; кнопки добавления/карточки недоступны. Другие admin формы могут открываться, но commit после logout отказывается в repository. Полное закрытие всех старых форм по logout и индивидуальные employee identities не объявлены готовыми.

A01–A04 для этого блока: точный slow verifier/wrong password/limiter/очистка ввода; locked direct catalog write и stale confirm без изменения object/result/audit; serialized logout/commit и новый locked runtime; settings login/logout/recreation не меняют кассира/кассу. H03 частично: guards существующих admin commands; H04 runtime token не хранится. SA01–SA06 целиком остаются planned до employee directory, полного UI revoke и upgrade evidence.

Тестовый runner использует явный привилегированный adapter с отдельным фиктивным verifier только в androidTest APK для прежних business fixtures. Default Administration в debug/release всегда locked; нет Intent/BuildConfig bypass. Access tests явно выходят из fixture session и проверяют настоящий repository guard. После реального APK replacement самостоятельный процесс снова locked; updater и чтение каталога остаются работоспособны.

Технические источники: [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Android lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle). Обязательное физическое тестирование отложено владельцем.
