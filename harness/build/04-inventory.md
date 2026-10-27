# Этап 04 — Склад

Статус: in progress —04a.1 suppliers ci-verified/API36/run40472850012; весь04 не закрыт

## Объём

Приёмка, поставщики, закупки, частичная приёмка, инвентаризация и история.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/OPERATIONS-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Модульная детализация

[Контракты инкремента](../../docs/WAREHOUSE-SPEC.md); [карта модулей](../../docs/MODULES.md), [порядок/документационные gates](../../docs/DOCUMENTATION-PLAN.md).

## Приёмка

I01: units/pack conversion; I02: stale baseline; I03: отмена сохраняет фиксации; I04: duplicate receive/restart/отказ записи.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Переписывание прошлых продаж.

## Контекст и результат

Значимые решения в `harness/context/04-inventory.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.

04a.1: [поставщики](../../docs/SUPPLIERS-04A.md), SUP01–SUP05; source refs/границы/тесты и следующий04a.2 отдельно. D03 decisions2026-10-27 заменяют прежний open partial remainder:3/10 closes shortage7, weighted cost3,12/10 explicit confirm.

SUP01–SUP05: [fullCI40472850012](https://github.com/mendelev-main/M-POS-Android/actions/runs/40472850012)/6f387ee SUCCESS83JVM/99instrumentation/12Python/lint/build,9UIseed/3financial+1supplier fixture and actualAPK1→2. Остальные I01–I04/physical pending.
