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
