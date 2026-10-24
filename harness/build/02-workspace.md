# Этап 02 — Рабочая зона

Статус: 02a implemented, CI36 pending; device отложен владельцем2026-10-24

## Объём

Реализовать W01–W07, persistent draft и резервы, редактирование раскладки.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Детализация перед кодом

[Сценарии и инкременты](../../docs/CATALOG-WORKSPACE-SPEC.md), [контракты и fixtures](../../docs/IMPLEMENTATION-CONTRACTS.md). Взять первый незакрытый инкремент; полный диапазон acceptance не считать выполненным после одного шага.

## Системные контракты

[OrderSession переходы](../../docs/STATE-MACHINES.md), reservation ownership и revision из [модели](../../docs/DOMAIN-MODEL.md); SYS-02/SYS-03 и failure/restart cases. Revalidation не уничтожает draft.

## Приёмка

W IDs с tests, rotation/process death и планшетным walkthrough.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Фиксация продажи и оплаты.

## Контекст и результат

Значимые решения в `harness/context/02-workspace.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.


Первый инкремент: [WORKSPACE-02A.md](../../docs/WORKSPACE-02A.md), schema5/current cart/layout. W01/W02/W06 частично, current availability W03; parked02b и full revalidation02c остаются planned.
