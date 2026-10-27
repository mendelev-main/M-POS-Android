# Этап 02 — Рабочая зона

Статус: инкременты02a–02e ci-verified API36 (последний run40177332732); полный02/performance/device не закрыт, physical отложен владельцем

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


Первый инкремент: [WORKSPACE-02A.md](../../docs/WORKSPACE-02A.md), schema5/current cart/layout. W01/W02/W06 частично, current availability W03; parked02b проверен отдельно ниже; revalidation02c реализован, точные проверки ниже.


Итог первого инкремента: [run40106275939](https://github.com/mendelev-main/M-POS-Android/actions/runs/40106275939), commit0e6dd0b: checks/emulator36 success;31JVM/4Python/lint/assembleDebug,31instrumentation (6workspace Room+1workspace UI), затем3explicit UI seed tests. CATALOG UPDATE PASSED/WORKSPACE UPDATE PASSED/UPDATE E2E PASSED: package1→2, stock8 и current cart quantity5/total15 сохранены. Полные W01/W02/W03/W06 и W04/W05/W07 ещё не закрыты; ограничения/следующие части в WORKSPACE-02A.


02b: [parked/ownership/metadata](../../docs/WORKSPACE-02B.md) ci-verified API36/run40140796798; Room schema5 не меняется, payload current1→2 мигрирует atomically. W03/W04/W05 проверяются отдельно, full02/revalidation02c не закрыты.

02c: [пересчёт с подтверждением](../../docs/WORKSPACE-02C.md) ci-verified API36/run40172190371. W03/W06 частично; physical и прочие UI критерии отдельно.

02d: [размеры папок и live total](../../docs/WORKSPACE-02D.md) ci-verified API36/run40172821096. Fixed-slot drag остаётся отдельной частью W01; полный02 не закрыт.

Итог02d: run40172821096/da8db71 —41JVM/6Python/lint/build,41instrumentation+4UIseed, actual updater success. Fixed-slot drag и физическая приёмка не закрыты.

02e: [home slots и drag](../../docs/WORKSPACE-02E.md) ci-verified API36/run40177332732. W01/W06; оставшиеся performance/physical критерии не закрыты.

Итог02e:45JVM/6Python/lint/build,44instrumentation+4UIseed, actual updater включая home slot8 success; оставшиеся performance/adaptivity/physical критерии отдельно.

02f: [большой каталог/Coil/адаптация](../../docs/WORKSPACE-02F.md) ci-verified API36/run40184227465; W01/W03/W07/DS04, physical acceptance отдельно.

Итог02f: [run40184227465](https://github.com/mendelev-main/M-POS-Android/actions/runs/40184227465)/f1639b8,47JVM/6Python/lint/build,47instrumentation+2scale+4UIseed; actual updater success.2000товаров:load306/search1209/add595ms;150%portrait/landscape screenshots reviewed. Physical/full02 отдельно.

Визуальный checkpoint: [WORKSPACE-VISUAL](../../docs/WORKSPACE-VISUAL.md), W01/W02/W06/DS04, source380dp cart/toolbar/tiles. Проверки текущей реализации и ограничения — build-log; не считать полный02 закрытым.

Visual checkpoint CI-verified: [run40461096340](https://github.com/mendelev-main/M-POS-Android/actions/runs/40461096340)/3e11c33,80JVM/90instrumentation/12Python/lint/APKs/actual updater passed. Source geometry/placeholder safety/150% reachability в scope WORKSPACE-VISUAL; physical/dark/paired diff отдельно.
