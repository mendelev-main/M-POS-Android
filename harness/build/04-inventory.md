# Этап 04 — Склад

Статус: planned

## Объём

Приёмка, поставщики, закупки, частичная приёмка, инвентаризация и история.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Приёмка

I01: units/pack conversion; I02: stale baseline; I03: отмена сохраняет фиксации; I04: duplicate receive/restart/отказ записи.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Переписывание прошлых продаж.

## Контекст и результат

Значимые решения в `harness/context/04-inventory.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.
