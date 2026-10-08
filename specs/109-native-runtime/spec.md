# 109 — Native runtime authority

Current status (08 October 2026): **109.01–109.08 complete; 8/20 inside 109, 115/129 expanded, 107/110 major tasks.** Parent 109 remains in progress. 109.09 in progress: native workspace read model, typed actions and cart-line editor/save verified; remaining order drafts/forms/product editor pending. 109.08 evidence: ../../docs/NATIVE_WORKSPACE_NAVIGATION_10908_RU.md. Physical acceptance pending 110; WebView removal 109.19.

Status: in_progress. Engineering completion remains 107/110.

## Scope and sequence

1. Read coherent Room documents without DOM, writes, initialization or invented defaults.
2. Replace JS session/bootstrap/navigation with native state and explicit domain authority checks.
3. Build screen models from domain repositories instead of mounted HTML.
4. Dispatch native actions directly to command repositories; preserve roles, recovery gates and persisted-before-effects ordering.
5. Replace post-commit orchestration for printing, loyalty, WEB and availability; availability retry only after the next saved payment, no automatic print retry.
6. Replace remaining settings/authentication, import/export and lifecycle handlers without changing backup v13 or exposing credentials.
7. Remove active WebView, JavascriptInterface and DOM adapters only after all preceding boundaries pass parity tests.

## Current increment

MPosRuntimeSnapshot reads an explicitly requested set of raw Room documents in one transaction. Missing documents stay absent; unknown fields and original bytes are preserved. It does not initialize authority or repair corrupt JSON. It is now used by the production native session restore repository; full JS runtime replacement is still pending.

## Acceptance

Automated: exact payload retention, missing versus stored JSON null, read-only behavior, invalid key rejection and multi-document retrieval. Concurrent atomic multi-document writers/readers are covered by a Room test. Subsequent native bootstrap must additionally verify per-domain authority and critical recovery journals before allowing mutations.

Physical acceptance stays in 110: offline startup, employee/shift restore, payment and refund, restart, import/export, all screens and performance. Do not mark 109 done while a hidden WebView still constructs models or dispatches actions.

## Verification of bootstrap reader increment

525/525 JS tests and 415/415 JVM tests passed (no failures/errors/skips); lint: 0 errors, 15 existing warnings. No APK assembly and no physical tablet acceptance. At that first increment (2d89c02), the reader was not connected to production startup; the following increment connects it for current-order restoration.


## Session restoration authority increment

The current-order load block now awaits MPosSessionRestoreEngine via the storage queue. Kotlin projects cart record filtering, customer defaults/extensions, delivery, labels/comments, WEB identity, loyalty metadata and prior kitchen-print marks. It is read-only and has no print/network/payment trigger. The existing split-draft validator runs afterward with the restored cart total, preserving paid parts and invalid-draft warnings.

Rollback: MPosNativeSessionRestoreEnabled=false or a failed/unsupported native read selects the byte-preserved reviewed restoration block. Unusual legacy coercions (non-finite fees, non-string customer identity) intentionally retain that path. No Room schema, v13 shape, authority initialization or write semantics change. The source hash check strips only the exact reviewed native hook and still verifies all other original HTML bytes.

Navigation and full bootstrap recovery remain pending. This increment removes session field projection from active JS authority for supported saved sessions; it does not remove WebView or JS lifecycle orchestration.


The production session read now uses MPosSessionRestoreRepository: it checks existing current-session authority and reads MPosRuntimeSnapshot within one transaction. A missing or changed document rejects native projection and retains the reviewed compatibility path; no authority is created implicitly. Tests cover owned/unowned documents, newer/removal conflicts, preservation of stored paid metadata and concurrent paired snapshot generations. Critical journal replay and navigation remain existing runtime responsibilities.


## Internal checklist (does not add engineering task IDs)

Detailed stable checklist: [109.01–109.20](tasks.md). Completed **5/20 (25.00%)**, remaining 15. Expanded whole-program progress **112/129 (86.82%)**; milestone progress **107/110 (97.27%)**. Parent 109 remains in progress until all children pass their acceptance criteria.

## Verification of production session increment

530/530 JS tests, 422/422 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. Shared fixtures exercise the actual reviewed restore block and Kotlin projection; paid split restart, invalid draft warnings, bridge rollback, owned Room data/conflicts and concurrent multi-document snapshots are covered. No local APK assembly; physical acceptance remains pending in 110.


## Workspace route authority increment

MPosWorkspaceRouteEngine now decides open category, open normalized folder, Back precedence and edit-mode toggles. It reuses the reviewed Kotlin folder normalization from 082. Production native-navigation routes these calls through the existing storage queue; native workspace captures category/folder/edit promises before releasing its action lock. No cart, payment, shift, stock or stored navigation writes occur.

Back preserves exact precedence: close the modal folder first; otherwise clear a legacy inline folder without leaving its category/edit mode; otherwise return to the root and reset search/edit mode. Folder opening does not invent IDs or permissions. Edit-mode entry retains the existing delayed drag initialization. Search filtering, tab navigation and rendering remain JS responsibilities for now.

View requests are FIFO. Each reply is checked against the current route, tab/payment page, query, folder modal, mounted modal identity and navigation data. Stale replies cannot override a newer view. A failed/unsupported pure read falls back to the reviewed function only if the view is still current. MPosNativeWorkspaceRouteEnabled=false provides independent rollback without changing native persisted navigation authority.

Shared fixtures compare actual reviewed navigation functions and the Kotlin transition engine. Integration tests cover delayed/stale replies, order-data retention, malformed replies, FIFO category/Back and explicit rollback. This is another part of 109, not an additional completed task or removal of WebView.

## Verification of workspace route increment

536/536 JS tests and 425/425 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. No APK assembly; physical route/keyboard/drag checks are documented for 110. Overall engineering completion remains 107/110.


## Paired active-session bootstrap increment

After the existing critical-journal recovery completes, loadAll uses MPosActiveSession.bootstrap. It initializes the existing shift/employee authority boundaries through their established one-time migration, then MPosActiveSessionRepository reads both owned documents in one Room transaction using MPosRuntimeSnapshot. Neither the repository nor projection writes or repairs data. Bridge/read failure or MPosNativeActiveSessionEnabled=false retains the original per-key reads.

MPosActiveSessionEngine preserves missing versus stored null, record filtering/warnings, unknown employee fields and exact admin-role normalization. It prepares first-open-shift and first-matching-employee indices using reviewed strict scalar ID equality (missing differs from null; object IDs do not match by JSON equality). Existing synchronous currentShift/currentShiftEmployeeIsAdmin remain active; prepared indices are a native bootstrap model for later native handlers, not an alternative cached authorization authority. No permission policy changes or role-cache serialization per UI action.

The production loadAll prefix and shared fixtures are exercised with both native and rollback paths. Repository tests cover missing/unowned authority, exact raw document retention, null warnings and concurrent paired generations. Source hash checks still verify original HTML after removing only exact reviewed native hooks. No automatic printing, payment, catalogue sync or availability action is added.

Internal progress: paired shift/employee normalization is active; complete native root session/authorization/navigation lifecycle and WebView removal remain pending.

## Verification of paired bootstrap increment

541/541 JS tests, 430/430 JVM tests passed; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings. No local APK assembly; tablet acceptance is pending in 110. Completed tasks remain 107/110.

## Native shift opening authority increment

Default production opening uses MPosShiftOpenCommand through a native-only
MPosStorageMirror.openShift entry on the existing bounded FIFO. The native dialog
retains the transient credential while JS reserves criticalOperationBusy and
returns its expected employees/shifts plus the existing UID/time. Kotlin checks
both owned documents in one transaction, verifies the selected live admin using
the exact reviewed credential policy, obtains carryover/recovery gates through
MPosShiftOpeningRepository and commits via MPosShiftLifecycleCommand. The
credential is a separate transient argument: it is never serialized into the
command, lifecycle hash/marker, backup, result or diagnostics. Credential policy
is unchanged; using a native digest is not a new authentication/security policy.

The transaction retains the full historical documents/extensions and cart,
rechecks employee/shift state and rejects concurrent changes, pending critical
journals, invalid carryover, existing open shift and projection/marker failures.
No print/network/availability/catalogue trigger runs in the command. Saved state
survives process restart; automatic retry is absent. A repeated gesture is locked
out; an already saved/stale opening must reload instead of creating another shift.

The compatibility adapter no longer invokes submitOpenShift or touches its DOM
password field in native-command mode. After a correlated successful result,
it verifies live expected state and result identity/history, applies the saved
shift, closes/renders and retains existing Telegram/monthly actions. Wrong
credentials allow correction; stale/uncertain outcomes block critical work.
Close or showModal cannot abandon an in-flight native operation. Ambiguous
dispatch failure is blocked; explicit non-dispatch rejection can retry safely.
The active credential is cleared after native dispatch/cancel/result/destruction.

Rollback before submission: MPosNativeShiftOpenCommandEnabled=false retains the
existing native form + reviewed JS verification/lifecycle path; form fallback
retains reviewed HTML. No schema/storage keys/v13 formats change. Existing
legacy password bytes are not duplicated in new code or fixtures. Other role,
employee-edit/delete, company/network authentication and full lifecycle/effect
authority remain pending. The hidden HTML form is still mounted; this is not
WebView removal or completed root authentication.

Automated scope: actual reviewed submitOpenShift fixtures (credential categories
only), exact/case/whitespace authentication, first/tied/missing carryover, normal
and admin staff; Room restart/stale data/pending journal/real SQLite rollback and
credential non-persistence; UI native/legacy mode, native-only credential handoff,
correlated/stale results; adapter ack ordering, duplicate locks, modal replacement,
wrong password, state conflict, dispatch ambiguity and external-effect failure.
Physical cases remain pending in 110.

Remaining work and milestone counting: [Russian runtime analysis](../../docs/NATIVE_RUNTIME_REMAINING_RU.md).

## Verification of native opening increment

551/551 JS tests and 438/438 JVM tests passed; failures/errors/skips: 0.
Android lint: 0 errors, 22 warnings in unchanged files (15 existing style/platform
warnings plus 7 dependency-version advisories from the online check). Full
testDebugUnitTest and lintDebug passed after the final callback-delivery guard.
The real SQLite/FIFO tests include an acknowledged database commit with failed
result delivery: saved shift remains, response requires reload, no resubmission.

Local JS validation used Node 24 with TZ=UTC and an available python command
for the existing source-sync fixture. Robolectric used a writable test-only
user.home outside the repository; no product APK assembled and no physical
tablet acceptance. The original reviewed source bytes/hash still pass.
Engineering completion remains **107/110 (97.27%)**; no task IDs or denominator
were added for internal 109 increments.


## 109.06 — fresh native root session context (in progress)

MPosRootSessionRepository reads owned shifts, employees and critical journal together in one Room transaction. It exposes native currentShift, selectedEmployee, isAdmin and recoveryPending plus raw compatible arrays for commands. It does not cache across role edits, database replacement or restart, initialize documents, replay a journal or emit effects. View records are detached copies; original stored extensions/roles remain unchanged.

Production ActiveSession.bootstrap now initializes established domain boundaries and requests rootSessionBootstrap after existing recovery. The paired compatibility bootstrap API remains for rollback/compatibility. Native opening form metadata and MPosShiftOpenCommand use the same root context; existing password policy, expected document conflicts, carryover validation and lifecycle transaction still apply. Opening checks recovery before mutation; reporting isAdmin does not authorize a blocked critical operation.

Tests cover root restart, owned record replacement/role changes, missing authority, detached records, pending journal without replay and concurrent three-document coherence. Existing opening parity and real SQLite tests remain required. Complete root startup ownership, synchronous JS currentShift/role helper replacement and import/lifecycle orchestration remain pending; 109.06 is not complete.

Opening carryover metadata reuses the command's existing root transaction snapshot; there is no second root document read/parse within that command. No tablet performance claim is made.

## Verification of 109.06 root-context increment

551/551 JS tests and 442/442 JVM tests passed after final snapshot reuse; failures/errors/skips: 0. Lint: 0 errors, 15 existing warnings in this environment. No APK assembly or physical acceptance. 109.06 remains in_progress; counters: 5/20 within 109, 112/129 expanded, 107/110 major milestones.


## 109.06 — Activity-owned live root session

MPosRootSessionOwner now starts with MainActivity, observes only the three root documents and their authority markers, rechecks them on foreground and closes with the Activity. Its states are Loading, AwaitingMigration, Ready, Failed and Closed. Room supplies all six rows in one query. Missing authority does not seed or erase documents; malformed data withdraws the previous Ready state. Recovery pending is exposed without replay or network/print effects. The observer continues after malformed data is replaced and suppresses publications from cancelled lifecycle generations.

Observed snapshots are detached from consumers: raw arrays, current shift/employee and bootstrap JSON cannot mutate the owner's retained model. Timestamp-only or unrelated table invalidations do not reproject root data. A foreground query that confirms unchanged payloads reuses the same snapshot/revision, so the shift surface is not reset unnecessarily. There is no measured tablet performance claim.

Production rootSessionBootstrap is delegated to the owner through the existing storage FIFO; it always reads a fresh repository transaction rather than trusting an observed snapshot. Native opening and other commands retain their own transaction checks. Activity collects root revisions and invalidates a visible native shift screen directly. Old controls are removed during reload, detached button callbacks and late query results are ignored, and hidden screens stay hidden. Layout/theme are the existing MPosNativeTheme presentation.

Rollback remains the existing ActiveSession and native shift-screen compatibility flags; storage ownership/schema/v13 are unchanged. The read-only observer does not dispatch payments, journal replay, availability, catalogue sync, print or notifications. Complete root startup orchestration and replacement of synchronous JS currentShift/role helpers still remain; 109.06 stays in_progress.

Automated cases: empty installation awaiting migration; owned replacements and role edits without browser notifications; journal pending without replay; malformed data/follow-up repair; authority removal; foreground unchanged snapshot reuse; live bootstrap despite observer timing; consumer isolation; close/refresh/bootstrap after destruction; native surface invalidation and stale controls/results.


## Verification of root lifecycle owner increment

Full suite: 551/551 JS and 449/449 JVM passed; no failures/errors/skips. After the final KTX-only visibility adjustment, the 5 shift-controller JVM tests and lint were rerun successfully; JS 551/551 was also rechecked. Final lint: 0 errors, 15 existing warnings. No APK assembly or physical tablet acceptance. Counters remain 5/20 for 109, 112/129 expanded and 107/110 major milestones; 109.06 stays in_progress.

## Completion of 109.06 — root startup and compatibility consumers

MPosRootStartup owns the recover → hydrate → activate → ready protocol in the Activity-owned FIFO storage service. begin creates a generation; advance requires the matching generation and current phase, rejects duplicate/out-of-order completions and cannot restart a completed generation. The root snapshot is read after recovery. Before activation, a fresh native snapshot must equal the hydration snapshot; an import/role/shift change cannot activate an old root. Restart/import loadAll calls are serialized and a failed attempt does not poison the next attempt. Pending journals are never implicitly replayed by this coordinator.

MPosCore.RootSession is a compatibility transport, not an alternative projection or authorization engine. Android synchronous currentShift/currentShiftEmployeeIsAdmin and selectedEmployee consume Kotlin's selected records/role instead of searching JS arrays. A missing/failed root clears the selections. The explicit MPosNativeActiveSessionEnabled=false flag restores the reviewed legacy startup/selectors. The original source is retained for rollback/reference until 109.19.

Root-changing commands deliver a fresh Room projection before acknowledgement, independent of observer scheduling. The response retains its original success/error result even if projecting the root fails after a durable commit: it carries a null root and the consumers deny stale access, rather than pretending the commit failed and inviting a duplicate operation. Revision guards reject late replies. Mutation responses contain only current shift, selected employee, admin and recovery flags; full history is read at startup only. Foreground refresh is ordered through the same FIFO and never replays printing/network/catalogue/availability effects.

The existing adapters still execute journal replay (109.15), domain hydration and saved-order application (109.09–109.14), and post-activation effects (109.16–109.18). Kotlin owns their root phase ordering, not these later domains. WebView, mounted UI actions and remaining edit/delete/settings authorization are still active and belong to 109.07–109.19. No business-role, supplier/cart, payment-cancel, print-retry or availability retry policy changes; keys, JSON documents and v13 remain compatible.

Automated cases cover startup order, duplicate acknowledgement, old generation after restart, import during hydration, restart after failure, durable writes despite a later root projection failure, role/shift replacement, detached selections, late revision rejection, compact mutation transport, root application before acknowledgement, explicit rollback, reference-source integrity and all existing session/recovery/shift parity cases. Physical acceptance is pending in 110.

Verification on 08 October 2026: **558/558 JS and 454/454 JVM tests passed**, zero failures/errors/skips. Lint: 0 errors, 15 existing warnings. No local APK assembly or physical acceptance. The reference-source hash check restores only the exact approved root hooks before comparison; unrelated reviewed source remains covered. 109.06 is done, parent 109 remains in_progress; progress is 6/20, 113/129 and 107/110.

## Integration-test feedback correction — 2026-10-08

[Correction record](../../docs/CONNECTION_TEST_FIX_RU.md): legacy Telegram callback restored; explicit connection tests now await bounded, request-correlated results in native settings presentation. Printer test keeps disk acknowledgement ordering and waits for terminal transport event; queue admission is not delivery. Duplicate and replaced-form results are guarded. Reviewed source hashes, domain semantics, schema/v13 and retry policies are preserved. This adapter correction does not complete native effects orchestration. After independent 109.06 completion in 9ad8c00, counters are 6/20 and expanded 113/129. Verification: 572 JS / 457 JVM, no failures/errors/skips; lint 0 errors / 22 existing-file warnings. No product APK assembled locally; real device tests pending.

## 109.07 — native employee authorization (in progress)

MPosEmployeeCommand no longer accepts authorization:"reviewed-handler" as permission to create an administrator, promote/demote staff or delete an employee. It verifies a transient credential with the established MPosAdministratorCredential verifier inside the same Room transaction as document conflict checks and persistence. No verifier or password-policy changes. Names/phones can still be edited and ordinary employees created without a password; changing an admin role requires a password even for the last administrator. Deletion requires the password and current first-open shift, protects self/admin records, retains history and checks the submitted shift against the live shift. A pending critical journal blocks changes without replaying it. The recovery gate parses JSON null (including whitespace) and fails closed on malformed/trailing journal content.

The default native-employee adapter replaces the save/delete authorization handlers and removes their legacy password inputs before the native settings adapter captures the form. Its payload contains only the employee command, request ID and theme; no password or claimed authorization marker. Role changes/deletion open MPosEmployeeAuthorizationDialog. The native EditText is excluded from saved view state and clears after dispatch/cancel/close. Only the native commit callback receives its transient credential. Replies contain fixed results/status and correlation IDs, never the password or command contents. Wrong credentials stay in the native dialog for retry before the commit deadline; cancellation changes no data and does not mark storage broken. Post-commit root state still arrives before acknowledgement.

The parent form remains busy during confirmation. The uncertain-commit deadline begins only when native dispatch starts, not while the user is typing; credential retry clears that deadline. Double taps, stale replies and late success after timeout cannot apply an old JS memory update. The native dialog also owns a 30-second dispatched-commit deadline: an unknown outcome blocks further submission, permits closing the window and ignores late credential failure/success instead of unlocking retry. A timeout retains the existing restart/recovery gate and never retries the write automatically. MPosNativeEmployeeCommandsEnabled=false restores the intact reviewed handlers/storage path; import still uses the existing owned storage boundary and v13.

Tests cover forged markers, protected role creation/change, unrestricted ordinary creation/rename, last-admin demotion, stale employees/shift, pending journal, Room rollback, self/admin deletion rules, native credential isolation/clearing, retry/cancel/destruction, busy/double-submit, late reply/timeout and rollback/import behavior. Existing supplier creation/edit and deletion permissions are untouched.

Verification: 575/575 JS and 464/464 JVM passed with zero failures/errors/skips; lint 0 errors, 15 existing warnings. No local APK assembly or physical acceptance. 109.07 remains in_progress: product/category edit/delete, protected stock/editor input and settings authorization are still pending. Counters remain **6/20 inside 109, 113/129 expanded, 107/110 major milestones**.

Final verification includes main commit 47646c0 (connection feedback fixes), retained during integration: 575 JS / 464 JVM, zero failures/errors/skips; lint 0 errors / 15 existing warnings in this cloud environment. These are automated checks; actual Telegram/WEB/LAN delivery remains pending physical acceptance.


### 109.07: реквизиты организации — 08.10.2026

`MPosCompanyCommand` сохраняет реквизиты в одной Room-транзакции с проверкой
актуальной роли администратора, открытой смены и ожидаемого документа.
Понижение сотрудника, закрытие/смена смены и устаревшая форма отклоняют запись.
JS обновляет состояние только после подтверждения commit; неопределённый
результат по таймауту блокирует повтор до перезапуска, автоматического повтора нет.
Ключ `company` принадлежит Room через существующий workspace storage; старый
shadow не перезаписывает его. Импорт/экспорт v13, JSON null/отсутствие и неизвестные
поля сохраняются. Ручное редактирование по прежней логике оставляет четыре поля.
Флаг `MPosNativeCompanyCommandsEnabled=false` возвращает прежние обработчики,
сохраняя нативное хранилище. Внешний вид формы не менялся.

Добавлены JS/JVM проверки подтверждения записи, прав, конфликта данных,
таймаута и отката Room. **Локально не запускались** согласно AGENTS.md §17;
результат проверяется в GitHub Actions (см. `docs/CI_AND_APK_SIGNING_RU.md`).
Физическая приёмка ожидается. 109.07 остаётся в работе: следующие границы —
защищённые операции редактора/категорий и остальные настройки.
Прогресс: **107/110 крупных этапов; 6/20 внутри 109; 113/129 детальных задач**.


### 109.07: удаление каталога и разрешения редактора — 08.10.2026

Подключены `MPosCatalogDeleteCommand` и `MPosProductEditorCommand`.
Удаление товара/пустой категории, плиток и ссылок навигации атомарно: ошибка
любого сохранения откатывает всю операцию. Защита чеков для возврата,
использования в составе и непустых категорий читает актуальную Room.
Администратор открытой смены удаляет товар без пароля; остальные — с паролем;
категория всегда требует пароль, включая администратора. Смена не требуется
при удалении с правильным паролем — существующее разделение сохранено.

`MPosEditorAuthorization` принимает пароль только из нативного поля.
Разрешения stock/no-stock временные, привязаны к товару и исходному документу,
не входят в Room/backup и теряются после перезапуска. Поддельный JS-флаг не
разрешает запись. Сохранение карточки повторно проверяет роль, документы,
защищённые остатки/конфигурацию/WEB и политику смены типа внутри транзакции.
Разрешение погашается после commit; при известном откате сохраняется для
исправления формы. Таймаут запрещает повтор до перезапуска.

Сбор полей, рецептов/фото и последующие эффекты остаются адаптерами, относящимися
к 109.09/109.16–109.18. Новый точечный hook сохранения в product-persistence.js
обратим; sync проверяет границу до замены файлов, parity-тест восстанавливает
только точный hook/timeout guard перед сверкой исходного SHA. Импорт и backup
v13 используют существующее хранилище без editor grants. Явные флаги rollback:
MPosNativeCatalogDeleteEnabled, MPosNativeEditorAuthorizationEnabled,
MPosNativeProductEditorCommitEnabled (false возвращает прежнюю границу).

Написаны проверки native transaction rollback, смены роли, stale documents,
поддельных/устаревших grants, таймаутов и UI acknowledgement. **Не запускались
локально:** GitHub Actions выполняет JS/JVM/lint по AGENTS.md §17.
Планшетная приёмка pending. Остались settings gates, WEB toggle товара и
защищённые операции лояльности; 109.07 остаётся in_progress.
Счётчик не завышен: **107/110; 109 — 6/20; детально 113/129 (87,60%)**.


### 109.07: реализация завершена — 08.10.2026; CI pending

Оставшиеся настройки backend/Telegram и WEB toggle товара проверяют актуальную
роль нативно перед Room commit. Панель администратора, реквизиты и защищённые
проверки Telegram/WEB читают свежие права перед действием. Backend-test и ручной
catalogue sync ждут acknowledgement настроек; автоматической синхронизации нет.
Network/telegram сохраняют исходные JSON/v13 и неизвестные поля при импорте;
первый save network сохраняет уже созданный startup device key. Редактирование
Telegram по прежней логике заменяет поля и сохраняет lastMonthlyWarehouseSent.

Корректировка лояльности использует нативный ввод пароля и HTTPS, прежний endpoint,
поля и серверную проверку. Идентификатор/имя администратора и подключение берутся
из свежей Room, а не из JS. Пароль не входит в bridge, журналы и backup. HTTP
запрос выполняется после закрытия DB-транзакции вне storage FIFO; timeout 5 секунд,
автоматического повтора нет. Создание/редактирование поставщиков и незащищённые
действия сохраняют прежние права; права лояльности на сервере не ужесточены.

**Согласованное пользователем правило:** после неподтверждённой корректировки
повтор возможен только после проверки актуального баланса. Нативная отметка
mpos_loyalty_adjustment_verification_v1 создаётся до запроса, сохраняется при
обрыве/timeout/5xx и после перезапуска. Она отдельно от кассового recovery и не
блокирует продажи. Успешный ответ/явное отклонение снимают её; при неопределённом
результате только успешный нативный GET баланса конкретной программы позволяет
вернуться к ручному вводу. Форма показывает актуальный прогресс/подарки и сбрасывает
дельты; проверка баланса не отправляет корректировку. Старый token не снимает
отметку новой операции. Отметка не содержит пароль/токен Telegram/device key;
внутренний служебный ключ не меняет формат бизнес-данных backup v13.

Rollback flags: MPosNativeAdminAccessEnabled, MPosNativeAdminSettingsEnabled,
MPosNativeProductWebEnabled, MPosNativeLoyaltyAuthorizationEnabled. Прежние
обработчики доступны явно до приёмки. Подготовленная, но уже закрытая нативная
форма не запускает поздний финансовый HTTP запрос.

Критерии реализации 109.07 закрыты; **новые проверки не запускались локально**,
результат GitHub Actions и физическая приёмка ещё не подтверждены. Это завершение
инженерной задачи, а не заявление об успешных тестах или полном уходе от WebView.
Прогресс: **107/110 крупных; 7/20 внутри 109; 114/129 детально (88,37%)**.
Далее 109.08 — нативное состояние навигации, выбора разделов, поиска и Back.

### 109.08 начата: владелец выбранного раздела

`MPosWorkspaceNavigationOwner` владеет tab и revision в StateFlow на время Activity.
Первое initialize принимает стартовый раздел; повторная инициализация не заменяет
нативный выбор. `setTab` отправляет прямую команду без DOM target, и только ответ
меняет совместимую JS-проекцию и вызывает render. При быстрых кликах старый ответ
не возвращает интерфейс к предыдущему разделу. Повторный выбор не создаёт историю
Back и не увеличивает revision; поиск/корзина/смена не меняются. Таблица разделов
не ужесточена: неизвестные строки сохраняются как в reviewed handler. Нестроковый
legacy input и явный MPosNativeWorkspaceNavigationEnabled=false используют rollback.

Проверки StateFlow, повторной инициализации, быстрых кликов и изоляции заказа
написаны, локально не запускались — выполняет Actions. 109.08 в работе:
поиск, выбор/Back, нативные controls/read models и остальные DOM route targets
ещё остаются. **114/129; 109 — 7/20; 107/110 крупных**, физическая приёмка pending.


### Исправление проверок и продолжение 109.08 — 08.10.2026

По запросу пользователя воспроизведены проверки Actions локально без сборки APK:
GitHub API вернул Forbidden, поэтому результат исходных удалённых запусков
не установлен. Найдены семь JS-сбоев тестовых окружений: отсутствовали
currentShiftEmployeeIsAdmin и criticalStorageRecoveryPending; тест ручного
menu sync не ожидал асинхронного сохранения сетевых настроек. Исправлены fixture
и ожидание создания HTTP-запроса, исходные assertions сохранены. После
исправления 626/626 JS и 2/2 Python проверок версий прошли.

В 109.08 добавлен нативный запрос поиска в MPosWorkspaceNavigationOwner:
первичная инициализация сохраняет query, повторная не заменяет его; изменение
только query увеличивает revision, повторное значение — нет. Таблицы и поиск
не изменяют корзину/смену/stock и не сохраняются в v13. JS сохраняет прежнее
String(value||'') и передаёт подтверждённый запрос reviewed-фильтру плиток.
Поздние ответы после нового ввода, смены раздела/категории/папки либо очистки
поиска не меняют экран. Explicit rollback сохраняет исходный onSearch.

Полный JS-набор после продолжения: **630/630**, JVM **509/509**, без ошибок
и пропусков; lint **0 ошибок / 15 предупреждений**, Gradle BUILD SUCCESSFUL.
Это локальное воспроизведение CI по прямому запросу пользователя; результат
нового удалённого запуска Actions ещё не подтверждён. APK не собиралась. DOM-фильтрация и переходы category/folder/Back ещё
сохраняются: это частичный шаг 109.08, а не завершение этапа.
Прогресс без увеличения: **114/129**, внутри 109 **7/20**, крупных **107/110**.
Физическая приёмка остаётся pending.


### 109.08: нативное состояние category/folder/edit и подтверждение Back — 08.10.2026

MPosWorkspaceNavigationOwner теперь хранит posPath, posFolder и editMode вместе
с tab/query. Первое initialize принимает стартовую проекцию; последующие
переходы рассчитываются из native StateFlow, а переданный JS state не задаёт
решение. Expected сравнивает только проекцию пяти полей, защищая от рассогласования.

prepareRoute создаёт единственное актуальное предложение без изменения выбора.
После проверки актуальности экрана JS вызывает acceptRoute: одноразовый token
проверяет исходное состояние владельца, и только затем применяет patch. Новые
tab/query/route инвалидируют старые предложения; cancel и повторный token не
меняют выбор. При ошибке включённый owner не запускает прежнее JS-решение.
Откат доступен явно через MPosNativeWorkspaceNavigationEnabled=false или
MPosNativeWorkspaceRouteEnabled=false. Неопределённый/устаревший ответ после
accept не меняет уже другой экран. discardRoute возвращает только выбор
этого неподтверждённого экраном перехода, сохраняя новый tab/query; старый
discard не отменяет последующий принятый переход. При потерянном accept reply
также отправляется discard, без автоматического повторения перехода.
Это состояние интерфейса, не financial
recovery gate; корзина и продажи не переписываются.

MPosWorkspaceNavigationRepository читает products и posNavigation согласованно
в Room только для открытия папки, проверяет их снова при подтверждении и
игнорирует подменённые bridge documents. Для обычной категории, Back и edit
нет SQL/чтения каталога. Отсутствие/JSON null обрабатываются совместимо.
Нет записей бизнес-документов, сетевых действий, новых backup v13 полей или
сохранённой истории Back. Сохранён прежний приоритет: окно папки → legacy
inline folder → корневая раскладка. Переключение edit очищает поиск и запускает
прежний drag effect; openFolder использует текущие Room-привязки.

Написаны JVM source-fixture parity, подтверждение/отмена/replay/stale owner и
Room-проверки свежих документов; JS-проверки FIFO category→Back, stale экрана,
приоритета папки и явного rollback. **Новые тесты локально не запускались**
по AGENTS.md §17; их выполняет следующий Actions после публикации.
Предыдущий коммит 98f5f73 подтверждён успешным Actions 37744808114 (tests/lint
и APK build), а не только локальным воспроизведением.

109.08 остаётся in_progress: нативные controls/read models, Android Back,
удаление DOM route targets и согласование нового runtime ещё
не завершены. Прогресс **114/129 (88,37%)**, внутри 109 **7/20**;
физическая приёмка pending.


### 109.08 — системный Android Back и смена runtime, 08.10.2026

MPosSystemBackPolicy выбирает прежнее действие из типизированных признаков:
после нативных settings — pending import → modal → warehouse → receiving →
background. История tab/category для системного Back не добавляется.
MPosSystemBackController допускает один запрос, имеет timeout и сбрасывает
поздние callback при pause/destroy. Background выполняется лишь после
подтверждения неизменившегося экрана. Временный native-system-back адаптер
передаёт presence/token, проверяет идентичность элементов/импорта и исполняет
выбранный Kotlin эффект один раз. При замене окна старый ответ ничего не
закрывает. Explicit MPosNativeSystemBackEnabled=false возвращает прежний
обработчик. DOM presence и close handlers сохраняются до замены экранов.

При rootStartup begin Kotlin очищает временную навигацию и предложения
предыдущего runtime. JS lifecycle блокирует новые действия до ready,
инвалидирует старые tab/search/route callbacks и не позволяет старому
initialization error очистить уже новую initialization promise. Новый выбор
сеется из восстановленного состояния после activate, включая import.
Это не меняет business documents, backup v13, оплату, роли или сетевые эффекты.

Написаны проверки всех 16 комбинаций Back, повторного нажатия, pause/destroy,
timeout, stale DOM, явного rollback, root activation и старых ответов при
импорте. Новые проверки локально не запускались (AGENTS.md §17), выполняет
Actions после публикации. Ошибка тестового fixture d73436b исправлена в
3f87071: ожидание постановки initialize в FIFO до выдачи ответа. Успешный
Actions 37747195877 подтвердил tests/lint и APK build для исправления.

109.08 остаётся in_progress: нативные controls/read models и устранение
оставшихся DOM targets/presence ещё впереди. Прогресс **114/129**, внутри
109 **7/20**; физические кейсы pending, APK локально не собиралась.


Проверка кода 8ca9dd6: [Actions 37748093813](https://github.com/mendelev-main/M-POS-Android/actions/runs/37748093813) успешно завершил job Tests and lint (JS, правила версий, Kotlin и lint). Подписанная APK собирается отдельным job. Локальные тесты/сборка не запускались; физическая приёмка pending.


### 109.08 — нативная панель workspace, 08.10.2026

Добавлены MPosWorkspaceToolbarModel и toolbarView: заголовок, «Назад»,
«Раскладка» и «Закрыть папку» формируются из Kotlin owner; имя папки — из
актуального Room posNavigation. Эти кнопки отправляют типизированную команду
непосредственно в Kotlin через FIFO хранилища, без нажатия скрытого HTML узла.
Revision и expected state защищают от старой панели и двойного перехода;
неприменённый ответ отменяет только свой принятый маршрут. Документы товаров,
корзины, оплат и смен не записываются. Категория без папки не читает Room;
модель панели кешируется между изменениями заказа, сбрасывается при навигации
и смене runtime. Сетка теперь исключает hidden плитки текущего JS фильтра.

Используется прежний MPosNativeTheme/Manrope, светлая/тёмная палитры и 48dp
кнопки. При ошибке модели остаётся reviewed presentation; явный rollback —
MPosNativeWorkspaceToolbarEnabled=false. Написаны проверки модели/Room,
типизированного нажатия, устаревшего ответа, оплаты во время чтения панели,
hidden плиток, кеширования и rollback. Локальные тесты и APK не запускались;
выполнение этого изменения ожидается в Actions, физическая приёмка pending.

109.08 остаётся in_progress: общие вкладки/поле поиска и controls редактора
раскладки ещё используют DOM, global Back ещё получает presence из адаптера.
Каталог/корзина read models относятся к 109.09. Прогресс не увеличен:
**114/129 (88,37%)**, 109 **7/20**, крупных **107/110**.

Проверка toolbar-кода `7bd7ecc`: [Actions 37750512318](https://github.com/mendelev-main/M-POS-Android/actions/runs/37750512318) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK собирается отдельным job; физическая приёмка pending.


### 109.08 — исправление подключения native UI, 08.10.2026

Проверка по сообщению с планшета выявила дефект: приложение объявляет `let state`,
а workspace/settings UI читали `window.state`. В браузере эти значения различны;
моки прежних тестов помещали state в window и пропускали дефект. Адаптеры workspace,
settings UI, platform settings и employee confirmation теперь выбирают lexical
state, сохраняя property fallback для совместимости. Regression fixtures используют
`let state` без window.state: включение экрана/кнопок, граница подтверждения принтера,
тема и подтверждение сотрудника. Это исправление интеграции, не новый закрытый этап.

Экран смены больше не исчезает при открытии нативного cash Dialog: фон остаётся
нативным, кнопки блокируются до закрытия формы, повторное нажатие не вызывает действие.
Hidden compatibility overlay отличает native Dialog от видимого HTML modal;
для видимого HTML modal native screen скрывается, чтобы не перекрывать окно.
Ошибка открытия возвращает управление; смена раздела/rollback/скрытие приложения
по-прежнему скрывают экран. Добавлены JS и native view проверки фонового слоя,
блокировки, возврата и stale replies. Формулы и сохранение cash movement не изменены.

Tests authored, GitHub Actions pending; локальные тесты/сборка APK не запускались.
Физическая проверка исправлений pending. Прогресс **114/129 (88,37%)**, 109 **7/20**;
109.08 остаётся in_progress, следующие controls — вкладки/поиск и DOM route targets.


### 109.08 — типизированный выбор категории с native плитки

Native category tile теперь передаёт openCategory + стабильный ID прямо в Kotlin
через ту же FIFO/revision проверку. Mounted HTML click handler не вызывается;
название карточки не подменяет ID. Прежние trim, очистка поиска/folder и выход из
editMode сохранены; бизнес-документы не записываются. Старый view token и повторное
нажатие отклоняются. Product/folder tiles и явный rollback пока используют reviewed
handlers; их read models ещё впереди. Написаны JS, Room и native view проверки,
выполнение следующего коммита в Actions pending. 109.08 in_progress; **114/129**.

Исправление `8855327`: [Actions 37752933255](https://github.com/mendelev-main/M-POS-Android/actions/runs/37752933255) — Tests and lint успешно (JS, Kotlin, lint); APK отдельным job, проверка на планшете pending.

Native category tile `a645d39`: [Actions 37753269784](https://github.com/mendelev-main/M-POS-Android/actions/runs/37753269784) — Tests and lint успешно. APK исправления `8855327` уже опубликована как Actions artifact M-POS-Android-release-0.1.173-1; категория собирается отдельным job. Физическая проверка pending.


### 109.08 — открытие папки с native плитки (08.10.2026)

Folder tile передаёт openFolder + ID непосредственно в Kotlin FIFO, без вызова
mounted HTML click. Repository проверяет expected revision и актуальные products/
posNavigation из Room при подготовке и принятии маршрута; удалённая/неизвестная
папка отклоняется. Обычная hydration уже инициализирует оба owned документа;
навигация не создаёт authority markers и не перезаписывает документы из UI.

Ответ renderFolder проверяется до изменения JS projection: category должна
совпадать с выбранной, ID — строка. После принятия остаётся прежняя category,
поиск очищается, открывается folder modal; stale/некорректный ответ отменяет только
свой маршрут. Back закрывает папку без выхода из категории. Shared appearance и
retained Views актуального main сохранены. Product tile/renderer/read models пока
сохраняют compatibility path (109.09). Добавлены JS/Room/native view проверки;
Actions следующего коммита pending, физическая приёмка pending.

109.08 in_progress: native tabs/search controls, редактор раскладки и global Back
presence остаются. Прогресс **114/129 (88,37%)**, 109 **7/20**.

Проверка folder-кода `c7e976a`: [Actions 37764445102](https://github.com/mendelev-main/M-POS-Android/actions/runs/37764445102) — Tests and lint успешно (JS, правила версий, Kotlin, lint). APK отдельным job; физическая приёмка pending.


### 109.08 — нативные основные вкладки верхней панели, 08.10.2026

MPosWorkspaceHeaderModel формирует reviewed destinations/labels и selected state
из Kotlin owner: M POS, Заказы, Приёмка, Чеки, Аналитика, Бронирования, Настройки.
MPosWorkspaceHeaderController отправляет selectHeaderTab прямо через native FIFO,
без нажатия HTML-кнопки. CAS по expected state/revision и token подтверждают
переход; discard отменяет только свой выбор, сохраняя новый search и последующие
явные selections (в том числе повторный выбор той же вкладки). Restart/import
сбрасывает outstanding selection. Native completion не разблокирует новую команду.
Header read не читает Room/catalog и не создаёт authority markers или документы.

Native controls используют navy/Manrope/selected pill и 48dp touch targets;
геометрия трёх групп ещё согласуется с source topbar. События, напоминание inventory
и shift pill остаются под их reviewed domain handlers. При оплате, редакторе,
модальном/складском overlay панель скрывается. Если source topbar требует внешнего
горизонтального скролла (группы за viewport), сохраняется исходный scroller;
при подходящей геометрии native controls возвращаются автоматически. Это временная
граница presentation, не утверждение полностью нативной верхней панели.

Rollback: MPosNativeWorkspaceHeaderEnabled=false возвращает исходные кнопки;
MPosNativeWorkspaceNavigationEnabled=false также выключает native header.
Добавлены Kotlin owner/model, Room boundary, Android View и production lexical
state JS проверки: busy/stale/runtime/late read, несовпавший DOM, modal/payment,
source rollback и narrow→wide. Локальные тесты/lint/APK не запускались; Actions
следующего коммита pending. Физическая оценка тем/портрета/font scale pending.

109.08 остаётся in_progress: поиск/filter controls, shift-tab control, controls
редактора раскладки и global Back presence ещё впереди. Domain/catalog read models
и удаление presentation geometry относятся к дальнейшим runtime этапам.
Прогресс **114/129 (88,37%)**, внутри 109 **7/20**, крупных **107/110**.

Fixed header `b0196c5`: [Actions 37767655391](https://github.com/mendelev-main/M-POS-Android/actions/runs/37767655391) — Tests and lint успешно (JS, правила версий, Kotlin, lint). Адаптер зарегистрирован в sync-pos-assets и parity fixtures; первый run 37767548247 до регистрации упал на JS проверках. APK отдельным job; физическая проверка pending.


### 109.08 — фильтрация текущих плиток в Kotlin, 08.10.2026

Live onSearch передаёт только типы/ID текущих sections-wrap плиток в
selectFilteredSearch. MPosWorkspaceSearchModel ищет по названиям авторитетного
каталога Room: trim по JavaScript whitespace, русский lower-case, substring,
строго строковые ID и первое совпадение getProduct. Пустой запрос показывает
все плитки, включая папки и отсутствующие товары; при непустом папки/категории
скрываются. Область поиска не расширена до всего каталога или folder modal.
Render-time фильтрация renderPosScreen остаётся в 109.09, нового search UI нет.

Одна native команда возвращает query/token/visibility. JS только проецирует
подтверждённую маску, без originalSearch и повторного render. Проверяются runtime,
последний запрос, прежний query/context, identity grid/плиток и type/ID. Stale или
некорректный ответ отменяет только свой query; новые query, routes, tab и import
не откатываются. Отмена последнего из нескольких неподтверждённых запросов
возвращает последний спроецированный query. Ошибка authority/модели не меняет query.

Repository сохраняется на время native FIFO; индекс имён перестраивается только
при изменении сырого Room catalog document. Каждая непустая фильтрация проверяет
текущий документ и authority; пустой запрос/отсутствующий grid не требуют SQL.
Данные каталога, корзина и финансовые документы не записываются. Rollback через
MPosNativeWorkspaceNavigationEnabled=false сохраняет reviewed onSearch.

Добавлены shared reference/Kotlin fixtures (русский текст, Unicode, whitespace,
строгие ID, дубликаты и coercion имён), Room freshness/no-writes/authority,
query token/runtime и JS stale/malformed projection проверки. Локальные тесты,
lint и APK не запускались (§17 AGENTS); GitHub Actions следующего коммита pending.
Физическая приёмка pending. 109.08 in_progress: native search presentation,
shift-tab control, редактор раскладки и global Back presence остаются.
Прогресс **114/129 (88,37%)**, внутри 109 **7/20**, крупных **107/110**.

Live search `1564939`: [Actions 37769761293](https://github.com/mendelev-main/M-POS-Android/actions/runs/37769761293) — Tests and lint успешно: JS/reference, правила версий, Kotlin и lint. APK собирается отдельным job; физическая приёмка pending.


### 109.08 — полный участок кнопки смены, 08.10.2026

Нативная shift-pill включена в MPosWorkspaceHeaderController: Manrope/navy,
white text, зелёный/красный индикатор, compact pill и 48dp. Label и status берутся
из первого открытого shift авторитетного Room document, не из текста/onclick DOM.
Формат employeeShortName сохранён, включая whitespace/первые два UTF-16 initials.
Source geometry и narrow topbar fallback остаются до native root presentation.

shiftHeaderView читает актуальный shifts document без записи/инициализации.
selectShiftHeader сравнивает его digest и expected navigation/revision в одной
Room transaction. При открытой смене подтверждается native tab=shift; при
закрытой — effect=openShift. stale changes не переключают tab/не запускают окно.
Устаревший принятый переход отменяется только своим headerToken.

NativeOpenForm.openNative запускает MPosShiftOpenDialog без openOriginal,
HTML form, select/password DOM fields. Это production путь новой кнопки; другие
legacy entry points пока относятся к следующему участку. Сумма переноса,
сотрудники и роли читаются existing native opening repository; credential
остаётся внутри Kotlin dialog и существующего native opening command.
Existing commit/ack, recovery gate и post-commit уведомления сохранены.
Cancel/fallback, pending busy и import/runtime invalidation обработаны;
SystemBack учитывает token нативного окна даже без HTML modal. Explicit fallback
возвращает reviewed форму. Global source geometry/Back presence других overlays
не удалены и этап 109.08 ещё не закрыт.

Добавлены JS boundary, Kotlin model/repository и View проверки: open/closed,
первый открытый shift, label/status, freshness/authority/no-writes, stale query,
дубли, busy/cancel/fallback/runtime/Back без DOM. Локальные тесты/lint/APK не
запускались (§17 AGENTS). Actions следующего коммита pending; tablet pending.
Список исполняемых участков: docs/NATIVE_MIGRATION_EXECUTION_RU.md.
Прогресс **114/129 (88,37%)**, 109 **7/20**. Участок не добавляет новую задачу
в общий счётчик: входит в стабильный ID 109.08.

Shift header `b38a2c4`: [Actions 37772473180](https://github.com/mendelev-main/M-POS-Android/actions/runs/37772473180) — Tests and lint успешно (JS, Kotlin, lint); участок вычеркнут в execution checklist. Tablet acceptance pending.


### 109.08 — все входы открытия смены без HTML формы, 08.10.2026

Production openShiftModal теперь сразу вызывает NativeOpenForm.openNative:
кнопка workspace, действие native shift screen и topbar используют один путь.
Hidden select/password/HTML modal не создаются; старый openOriginal вызывается
только при explicit compatibility flags или «Прежняя форма». Повторное нажатие
сохраняет тот же token/диалог и не отправляет вторую команду. Отказ bridge не
переключает молча на старую форму. Existing save/ack/recovery/effects не изменены.

NativeOpenForm уведомляет presentation через mpos-native-open-state на open и
abandon. Header скрывается и возвращается по состоянию окна; shift screen
остаётся native под диалогом, получает только block/unblock, без нового Room read.
Workspace сохраняет native фон и блокирует команды; закрытие окна разблокирует
его без HTML MutationObserver. Это блокировка окна, а не recovery failure:
сообщение о перезапуске при ней не появляется. Back и restart/import защищены
existing token/runtime guards предыдущего участка.

Написаны проверки всех default entries, повторного нажатия, отказа bridge,
событий без DOM, блокировки/разблокировки native shift/workspace/header, сохранения
и explicit compatibility path. Локально JS/JVM/lint/APK не запускались (§17);
Actions следующего коммита pending. Физическая приёмка pending.
109.08 остаётся in_progress; **114/129 (88,37%)**, внутри 109 **7/20**.

All opening entries `4c44c33`: [Actions 37773405619](https://github.com/mendelev-main/M-POS-Android/actions/runs/37773405619) — Tests and lint и Build APK успешно. Второй полный участок вычеркнут в execution checklist; следующий — раскладка рабочей зоны с полным набором действий. Физическая приёмка pending.

### 109.08 — завершение редактора и Back, проверка в Actions

Production editor теперь MPosLayoutController: native add/remove/drag, создание,
название/удаление папок, перемещение и reorder товаров, «Готово» и Back.
MPosLayoutRepository читает products/layout/posNavigation из Room; commit делает
CAS навигации и SHA256 документов и сохраняет через withTransaction в FIFO.
Переданные caller tiles/navigation не являются источником данных. Metadata layout,
ключи, backup v13, лимит 20, folder rules и действующие права сохранены.
JS только проецирует сохранённый документ; поздний результат не заменяет импорт.
При сбое доступны обновление модели и повтор; автоматического повтора записи нет.

Back использует MPosBackStateOwner и lifecycle notifications вместо DOM presence:
import → modal → warehouse → receiving → background. Новый overlay/import identity
меняет revision; устаревший callback и повторный Back не исполняют действие.
Reload очищает owner; explicit rollback сохраняет прежний путь. Folder parent
редактора и формы принадлежат native controller, отмена не записывает документы.

Граница поиска: reviewed POS не имеет поля ввода; новый control не добавлен.
Query и live decision уже принадлежат Kotlin; render-time каталог, DOM tile
projection и geometry оболочки принадлежат 109.09/root presentation. Нативные
кнопки не ищут HTML onclick targets. Legacy редактор остаётся только rollback.

Добавлены Room/controller/JS lifecycle tests: лимит, metadata, move collision,
папки и reorder, stale import/navigation, key order, overlay replacement, native
Back без DOM, busy/duplicate/fallback. Local tests/APK не запускались (AGENTS §17).
Этап пока in_progress до результатов Actions, счётчик 114/129 не увеличен.

### 109.08 завершён — 08.10.2026

Полностью закрыт стабильный этап навигации workspace: native owner вкладок,
query/live search, типизированные category/folder routes, complete shift control,
все default входы открытия смены, complete native layout editor и lifecycle Back.
Layout read/commit используют authoritative Room, transaction/digest CAS/FIFO;
права, metadata, лимит 20, folder rules, strict IDs, ключи и backup v13 сохранены.
Доказательства и граница: [109.08](../../docs/NATIVE_WORKSPACE_NAVIGATION_10908_RU.md).

Actions **37782566450**, main **c671e38**: JS/Kotlin tests и lint success;
Подписанная APK сборка success. Ранее e867895 прошёл полный Actions 37781537606
с APK, 1e27c5f — rollback SQLite и folder validation checks. Локальная APK
не собиралась. Physical acceptance pending в 110.

**115/129 — 89,15%; внутри 109 8/20; крупных 107/110.** Это количество
инженерных задач; знаменатель и scope не изменены, не native coverage.
Следующий этап **109.09** — каталог/корзина/product editor без HTML extraction
и mounted JS handlers. Geometry/DOM projection пока presentation зависимости;
runtime WebView будет удалён по 109.19, физический gate — 110.

### 109.09 — native workspace read model и типизированные действия, 08.10.2026

Default workspace теперь MPosWorkspaceReadRepository/ReadModel: products, layout,
posNavigation, root shift/recovery и parked count читаются из authoritative Room
в согласованной transaction. Плитки, имена, цвета, символы, координаты, units,
доступность simple/composite/unlimited, строки/скидки/итоги/доставка и кнопки
формирует Kotlin. Pricing/loyalty/availability переиспользуют parity engines.
No DOM labels/tile/cart extraction; только bounds и CSS geometry оболочки.
Typed action table вызывает явные allowlisted команды без скрытых node.click()
и simulated DOM events. Payment/WEB/parked/customer/settings пока handoff своих
этапов; права, retry, persist-before-effects правила не менялись.

Live search сохраняет initial mounted scope через native catalogScope, а render
поиск категории пересекает папки. Folder query не меняет содержимое папки, как
reviewed POS. IDs и dataset string coercion, fractional units, metadata/layout,
manual-price products и legacy qty string суммирование сохранены. Старый renderer
в отдельном native-workspace-legacy.js запускается только explicit
MPosNativeWorkspaceReadModelsEnabled=false. Source sync и baseline restoration
учитывают adapter без изменения reviewed source hash.

**Честная оставшаяся граница:** корзина/config ещё передаются явным runtime order
snapshot, это не native draft authority. Product modifier/manual-price/cart-item
forms и product editor ещё source; они остаются внутри 109.09. Этап не закрыт,
прогресс **115/129, 109 — 8/20**. JS/Room/native read-model проверки добавлены;
выполняет Actions, локальные тесты/lint/APK не запускались.

### 109.09 — редактор строки корзины, 08.10.2026

Каталог/read models проверены в Actions 37787596243 (5b94b88): JS/Kotlin/lint
и signed APK success. Подключены native количество/комментарий/скидка и
atomic currentOrderSession save с CAS, recovery gate и stock preflight.
Нет HTML формы или source save handler; отмена не пишет, фон остаётся native.
Новые Room/controller/JS checks pending Actions. [Границы и проверки](../../docs/NATIVE_WORKSPACE_DOMAIN_10909_RU.md).
109.09 остаётся in_progress, **115/129 (89,15%), 109 — 8/20**.

109.09 проверка текущего участка: main **1b74e9a**, [Actions 37791354617](https://github.com/mendelev-main/M-POS-Android/actions/runs/37791354617) — JS/Kotlin/lint success. Каталог, native cart presentation и полный редактор строки вычеркнуты в execution checklist. Availability рассчитывается для видимых рецептов с одним ingredient lookup; отрицательные остатки и folder exit сохраняют source parity. Полный signed run 37791017299 на 5281321 — success; текущая APK сборка также success. Остальные cart drafts/commands/forms и product editor остаются в 109.09; **115/129, 8/20**. Physical acceptance pending 110.

### 109.09 — native configured add, модификаторы и ручная цена

Подключены MPosCartAddModel/Repository/Controller: authoritative каталог/корзина,
нормализация и выбор modifiers, min/max, price formation, merge и atomic
currentOrderSession + projection save. Source handlers/HTML inputs на этом пути
не вызываются; normal/manual/folder правила сохранены. Barrier не позволяет
фоновому JS save перезаписать корзину до native ack; uncertain commit блокирует
повтор/legacy save до восстановления. [Граница, rollback и физические кейсы](../../docs/NATIVE_CART_ADD_10909_RU.md).
JS/Room/controller parity/failure проверки pending Actions; local tests/APK
не запускались (§17). 109.09 in_progress, **115/129, внутри 109 — 8/20**.

109.09 increment: add/modifier/manual price verified in Actions 37821758058
(tests/lint/signed APK success). Native remove/quantity commands now own atomic
session commits, stock checks and distinct last-row reset semantics. Automated
checks pending Actions; details/physical cases: docs/NATIVE_CART_EDIT_10909_RU.md.
Order settings/native full draft/product editor remain. 115/129; 109: 8/20.
