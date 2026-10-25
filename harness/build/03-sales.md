# Этап 03 — Продажи

Статус: 03a ci-verified; sale/refund planned

## Объём

Оплата наличными/картой/смешанная, скидки/доставка, receipt snapshots, stock transactions, повтор/возврат.

## Зависимости и источники

Порядок/зависимости: `PLANS.md`. Источник: `docs/SOURCE.md`, карта: `docs/PARITY.md`; архитектура/данные: `docs/ARCHITECTURE.md`, `docs/DATA.md`. Перед реализацией уточнить сценарии по pinned исходнику, добавить тестовые fixtures и acceptance details для этой итерации.

## Спецификация

[Сценарии, отказы и открытые контракты](../../docs/OPERATIONS-SPEC.md); [команды и регрессионные examples](../../docs/IMPLEMENTATION-CONTRACTS.md). Статус planned; описание не является evidence реализации.

## Системные gates

Перед финансовым кодом реализовать минимальную смену03a по уточнённому плану, определить cash/refund contract до полного этапа05, денежную policy, outcome внешнего платежа и поддерживаемый объём возврата. См. [состояния](../../docs/STATE-MACHINES.md), [commit/recovery](../../docs/RESILIENCE.md), [gates](../../docs/SYSTEM-ACCEPTANCE.md). Ссылка на будущий05 не разрешает молча создать фиктивную смену.

## Модульная детализация

[Контракты инкремента](../../docs/MANAGEMENT-REPORTS-SPEC.md); [карта модулей](../../docs/MODULES.md), [порядок/документационные gates](../../docs/DOCUMENTATION-PLAN.md).

## Приёмка

S01: commit атомарен; S02: повтор idempotent; S03: historical refund; S04: decimal rounding/split; S05: история после restart.

Для каждого критерия записать implemented / ci-verified / device-verified и evidence. Проверить happy path, отказ хранения, повтор действия и restart там, где есть сохранение. Детальные UI и integration сценарии уточняются при начале этапа.

## Вне этапа

Эквайринг/фискальный SDK без отдельного контракта.

## Контекст и результат

Значимые решения в `harness/context/03-sales.md` при начале работ; наблюдаемые результаты в `harness/build-log.md`.

## Совместимость с обновлением приложения

При появлении финансового commit/recovery связать разрешение установки APK (00.1) с реальным состоянием доменных операций: установка запрещена во время записи/восстановления, черновик сохранён до запуска внешнего installer. Проверить отказ/повтор и сохранность Room при обновлении. В bootstrap 00.1 финансовых операций ещё нет; [описание](../../docs/UPDATES.md).

## Подготовка03a —2026-10-25

[Сверка01/02](../../docs/PHASE-02-REVIEW.md), [минимальная смена](../../docs/SHIFT-03A.md), [утверждённая часть D02](../../docs/MONEY-03.md). Разрешены line HALF_UP2 и полный cross-shift cash refund; скидочные/split allocations отдельно. Runtime03 отсутствует; эта подготовка не закрывает S/H acceptance. Сначала релиз готового01/02 в main, затем реализация03a в develop.

03a implementation: [SHIFT-03A](../../docs/SHIFT-03A.md), H01/H02 minimal ci-verified API36/run40206673038; schema6/общий экран смены/installation gate. S01–S05 и full05 по-прежнему planned.

Итог03a: [run40206673038](https://github.com/mendelev-main/M-POS-Android/actions/runs/40206673038)/fc7cde5,50JVM/52instrumentation+2scale+5UIseed,6Python/lint/build, Room6 migrations/fault/replay/concurrency/installation gate и actual updater сactive140BYN passed. Physical later. Следующий03b только после выпуска03a и применимых D02 fixtures.
