# Этап 05 — Смены и роли

Статус: planned

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
