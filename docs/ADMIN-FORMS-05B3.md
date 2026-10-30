# 05b.3 — отзыв административных форм

Статус: implemented; локальные проверки выполняются, API36/actual APK upgrade pending. Не объявляет весь05b/H03/SA01–SA06 завершёнными.

Источник: pinned Web1682c883, shifts.js closeAdministrationSession/requireAdministration; Android adapter android-admin.js использует общий runtime допуск; source-product-stock.test.cjs «closing administration blocks stale deletion confirmations». Web закрывает modal, editMode и settings admin panel при logout; повторные handlers проверяют текущий вход. Native дополнительно связывает отложенную команду с конкретным входом, чтобы logout → новый login не дал старой форме новую authority.

## Контракт

AdminAuthorization — только process memory/coroutine context: ссылка на AdminSession и её generation. Не попадает в fingerprint, business command, JSON, Room, backup, пароль или SavedState. AdminForms держит epoch формы и исходную authority неизвестных команд. Background/recreation не меняют epoch, logout/успешный новый login меняют. Проверка epoch работает даже если StateFlow/Compose пропустили промежуточное active=false.

Запись: при действии UI фиксируется permit; при фактическом adminTransaction под authorization mutex повторно проверяются session identity/generation и active. Порядок InstallationGate → authorization → Room сохранён; permit.write не держит mutex вокруг всего repository, иначе возник бы deadlock при вложенном adminTransaction. Поэтому команда, уже допущенная к транзакции, заканчивается до logout; ожидавшая gate команда после logout/relogin отклоняется без записи. Fingerprints/result IDs/SQLschema8 прежние.

Каталог использует permit очереди, в том числе draft autosave/photo/import/category reorder/save/delete; исходный permit не обновляется при выполнении очереди. Отклонённые старые записи не удаляют сохранённый черновик. Стартовое чтение каталога не переписывает черновик и не требует входа. После нового locked runtime черновик не открывается; администратор явно нажимает «Открыть сохранённый черновик». При explicit resume defaults/session ID сохраняются до продолжения редактирования. Удаление черновика теперь тоже проходит adminTransaction.

Supplier/Employee/Purchase/Receiving/Inventory модели закрывают ephemeral form/edit/preview/count input при отзыве. Каждая поздняя публикация дополнительно проверяет authority, поэтому запоздавший read/commit callback не возвращает старую форму. Snapshot и устойчивые бизнес-черновики/история/результаты сохраняются. Storage blocked и canEdit различаются: отсутствие прав не объявляет БД повреждённой, не выключает чтение/напоминания. Новый вход требует свежего открытия/перечитывания формы. Несохранённый ввод не возобновляется автоматически; уже записанный бизнес-черновик может быть открытым заново действием администратора.

Supplier/Employee/Purchase/Receiving/Inventory/ReceivingPosting command retries: сначала outcome без привилегированной записи; если сохранённый результат найден, показывается факт. Если результата нет, исходный permit проверяется перед commit. После logout/relogin он недействителен: повторная отправка запрещается, unresolved снимается только после наблюдаемого отсутствия результата; ошибка чтения оставляет ту же unresolved команду и recovery lock. Новая операция требует нового явного действия/формы, а не замены command ID неизвестной операции.

Compose local category/delete/discard confirmations, warehouse confirmations, POS edit mode/folder/home management и settings admin panel связаны с session generation. Logout закрывает их; повторный login не возвращает подтверждение. Операционные формы кассира (корзина, количество, конфигурация продажи, split, наличные) не отзывает административный logout. Home/layout команды получают собственный permit до постановки в очередь. Чтение каталогов, складской истории и результатов сохраняется.

## Приёмка AR01–AR05

- AR01: rapid logout/login и другой runtime при равной generation не допускают старый permit; command retry сохраняет исходный permit.
- AR02: очередь перед InstallationGate → logout/login → отказ без document/result/audit; admitted transaction заканчивается перед logout, outcome читается после него.
- AR03: locked process читает сохранённый product draft без записи/фатальной ошибки/открытия редактора; explicit resume после входа сохраняет исходный ID/ввод.
- AR04: незаписанные формы всех6 моделей закрыты, snapshots и durable drafts/balances unchanged; product/category UI confirmations не возвращаются после login, POS/edit/settings/warehouse local state использует общий epoch.
- AR05: unknown result переживает logout; известный результат читается без дубля, отсутствие результата не отправляет старую команду под новым входом. Existing actual APK updater продолжает проверять новый locked runtime и сохранность каталога/склада/кассы/сотрудников.

Физический планшет/dark/large text остаются отдельной приёмкой. Полная capability matrix и заключительная сверка05b — следующий пункт, отчёты/аналитика не входят в этот блок.
