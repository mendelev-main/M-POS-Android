# Этап 02 — Рабочая зона

Статус: 02a/02b ci-verified API36; 02c implemented/CI36 pending; device отложен владельцем2026-10-24

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


Первый инкремент: [WORKSPACE-02A.md](../../docs/WORKSPACE-02A.md), schema5/current cart/layout. W01/W02/W06 частично, current availability W03; parked02b проверен отдельно ниже; full revalidation02c остаётся planned.


Итог первого инкремента: [run40106275939](https://github.com/mendelev-main/M-POS-Android/actions/runs/40106275939), commit0e6dd0b: checks/emulator36 success;31JVM/4Python/lint/assembleDebug,31instrumentation (6workspace Room+1workspace UI), затем3explicit UI seed tests. CATALOG UPDATE PASSED/WORKSPACE UPDATE PASSED/UPDATE E2E PASSED: package1→2, stock8 и current cart quantity5/total15 сохранены. Полные W01/W02/W03/W06 и W04/W05/W07 ещё не закрыты; ограничения/следующие части в WORKSPACE-02A.


02b: [parked/ownership/metadata](../../docs/WORKSPACE-02B.md) ci-verified API36/run40140796798; Room schema5 не меняется, payload current1→2 мигрирует atomically. W03/W04/W05 проверяются отдельно, full02/revalidation02c не закрыты.

02c: [пересчёт с подтверждением](../../docs/WORKSPACE-02C.md) implemented/CI pending. W03/W06 частично; physical и прочие UI критерии отдельно.
