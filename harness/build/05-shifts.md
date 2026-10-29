# Этап 05 — Смены и роли

Статус: in progress —05a cash movements CM01–CM05 ci-verified; roles/auth/reporting planned

## Объём

Открытие/закрытие смены, сотрудники/роли, отчёты и аналитика.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/OPERATIONS-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Системные контракты

[Доступ/аудит](../../docs/ACCESS-AND-AUDIT.md), runtime session и проверка capabilities в команде; [системные gates](../../docs/SYSTEM-ACCEPTANCE.md).

## Модульная детализация

[Контракты инкремента](../../docs/MANAGEMENT-REPORTS-SPEC.md); [карта модулей](../../docs/MODULES.md), [порядок/документационные gates](../../docs/DOCUMENTATION-PLAN.md).

## Приёмка

H01: повтор закрытия не дублирует; H02: деньги сверены; H03: use-case permissions; H04: admin session не переживает restart.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Реальная рассылка отчётов.

## Контекст и результат

Значимые решения в `harness/context/05-shifts.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.

2026-10-29: 05a CM01–CM05 implemented, CI pending; manual cash deposit/withdrawal journal and exact reconciliation, same-command recovery. Контракт SHIFT-CASH-05A.md; single-owner только, roles/auth/analytics planned.

2026-10-29: 05a CM01–CM05 ci-verified/API36: [FullCI40853537689](https://github.com/mendelev-main/M-POS-Android/actions/runs/40853537689)/7293d9c SUCCESS; production runtime93c1f2c, screenshot helper7293d9c.136JVM/211instrumentation/0failures/0skipped,13Python/harness/lint/debugAPK/testAPK; actual debugAPK1→2 passed. Manual deposit/withdrawal, exact cash/close/count/carry, four-write rollback/SQL abort/lost reply/replay, owner/gate/CAS/concurrent withdrawal, unfinished split refusal, corrupt journal refusal, recreation/input retention and unknown-result locks passed. SHIFT CASH UPDATE PASSED: journal, closed summary, carry/current cash and original command replays preserved without duplication. Full-window modal/history screenshots inspected: readable, no overlap. Initial40852329275 failed only the modal screenshot helper (two Compose roots); helper corrected without weakening monetary assertions. Single-owner scope only; roles/auth/analytics and physical tablet/dark/150%/cold-process UI remain pending. Main/signing/SQLschema8 unchanged.

2026-10-29: начата подготовка05b; pinned employee/admin session source и текущие owner guards исследованы. Контракт docs/STAFF-ACCESS-05B.md, SA01–SA06 planned. Вопросы владельцу: группы прав, credential, runtime lifecycle. До ответов auth/runtime не менялись; H03/H04 остаются planned.

2026-10-29: решения05b приняты: employee/admin, общий заданный password только как salted slow verifier, отдельная runtime admin session; background сохраняет, logout/process death сбрасывают. 05b.1 A01–A04 implemented/CI pending; guards существующих admin commands, settings login/logout/indicator. Справочник/выбор кассира05b.2 и полный SA01–SA06 planned; STAFF-ACCESS-05B.md. Plaintext credential не записывается.

2026-10-29: 05b.1 A01–A04 ci-verified/API36: [FullCI40859068370](https://github.com/mendelev-main/M-POS-Android/actions/runs/40859068370)/645f179 SUCCESS;141JVM/215instrumentation/0failures/0skipped,14Python/preflight/lint/debugAPK/testAPK.2workspace-scale/18UIseed/15upgrade fixtures and actual debugAPK1→2 passed. ADMIN SESSION UPDATE PASSED: standalone new process starts without admin access; all prior catalog/workspace/shift/cash/financial/warehouse upgrade assertions retained. Slow exact verifier, wrong password/input clearing, monotonic limiter, serialized logout/commit, default locked new session, direct catalog/supplier/inventory/layout refusals without writes and cashier shift/deposit after logout passed. Login/logout/recreation preserved cashier/shift cash; unique administration navigation tag regression passed. Login/session screenshots inspected: shared controls, readable, no overlap. Plaintext supplied credential absent in main sources and local APK entries; privileged fixture credential absent in target APK. Initial40857960735/9bcf34b failed with duplicate topNav-settings (7 UI flows) and3 subsequent split-fixture workspace failures; distinct indicator tag fixed, no gates weakened. Read-only catalog row IDs allow updater same-row stock verification without granting edit access. Employee directory/selection, complete UI form revocation and full SA01–SA06 remain planned; no full05b/H03 completion claim. Background lifecycle is implemented by process memory; physical tablet/dark/150% remain pending. SQLschema8/main/signing unchanged.

2026-10-29: начат05b.2 по плану; EMPLOYEES-05B2.md. Независимые Employee/Command/Rules и strict payload1 codec EF01–EF05 implemented, локально146 JVM/0failures/0skipped,14Python,preflight/diff passed; debug build/lint passed. Вопросы владельцу о bootstrap «Локального владельца» и изменениях активного кассира отправлены; интеграция directory/shift actors ждёт ответов. Runtime/SQLschema8/исторические документы не изменены.

2026-10-29: решения владельца05b.2: перед новой сменой обязательно создать сотрудника; любые изменения кассира открытой смены запрещены до закрытия. ED01–ED06 implemented/CI pending: EmployeeRepository atomic CAS/results/audit/tombstones/admin+installation guards, shared staff UI и выбор кассира, active actor для open/close/cash/checkout/split/sale/refund, legacy смены/replay без переписывания. Контракт EMPLOYEES-05B2.md; SQLschema8 прежняя. Tests/actual upgrade выполняются; verified статуса пока нет. Test-only initializeCashierFixture явно создаёт кассира старых business fixtures до открытия; default APK не содержит bootstrap/bypass.
