# Этап 03 — Продажи

Статус: 03a/03b.1/03b.2a/03b.2b repository ci-verified; 03b.2c manual checkout/history UI ci-verified; 03b.3a split progress repository ci-verified; split editing/UI иrefund planned

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

03b.1: [денежное ядро и последующие шаги](../../docs/SALE-03B.md), S04 pure ci-verified/run40223239353; S01/S02/S05 и actual checkout planned. Дополнительные D02 примеры подтверждены владельцем25.10.2026.

03a выпущен0.1.46.1, public API36 upgrade5101→5601 passed40223234154.03b.1 pure [CI40223239353](https://github.com/mendelev-main/M-POS-Android/actions/runs/40223239353):55JVM/6Python/lint/build passed; actual sale/history next03b.2.

03b.2a checkout persistence ci-verified [40224718420](https://github.com/mendelev-main/M-POS-Android/actions/runs/40224718420)/c753666:55JVM/53instrumentation/6Python/lint/build и прежний actual updater passed. Новый draft Room reopen/rollback/conflicts/gate covered; atomic sale/history/UI далее, S01/S02/S05 остаются planned. Подчасть не выпускается отдельно.

03b.2b S01/S02/S05 repository ci-verified [40226577890](https://github.com/mendelev-main/M-POS-Android/actions/runs/40226577890)/bb40ae2:56JVM/60instrumentation/6Python/lint/build +2scale/5UIseed/financial seed+verify actualAPK1→2 passed. Scope SALE-03B; next03b.2c shared checkout/history UI. Не переходить к refund03c до завершения этой вертикали.

03b.2c manual checkout/history UI ci-verified [40231561635](https://github.com/mendelev-main/M-POS-Android/actions/runs/40231561635)/e23f3c5:56JVM/62instrumentation/6Python/lint/build +2scale/6UIseed/financial seed+verify/actualdebugAPK1→2. Включён вход ксохранённомуresult изemptyworkspace. Screenshot modals/result reviewed, sharedUI. Следующий03b.3 —sequential paid parts/freeze/recovery guards; весь03b/refund03c/physical pending. Не выпускать каждыйинкремент отдельно.

03b.3a repository only: API36 [emulator40234272200](https://github.com/mendelev-main/M-POS-Android/actions/runs/40234272200/job/121292369386)65tests passed + [checks40273381968](https://github.com/mendelev-main/M-POS-Android/actions/runs/40273381968)56JVM/6Python/lint/build passed послеwhitespace-only fix747f833. Scope SALE-03B: persisted flags/freeze/atomic final, безUI/unpaid-editor/interimcashdisplay. Partial actualupgrade/physical/full03b pending; следующий03b.3b unpaidpart editing/stable IDs передUI.


03b.3b.1: S04 pure unpaid allocation/stable ID lifecycle implemented; см. [SALE-03B](../../docs/SALE-03B.md). Persistence editor/совместимость progress1 и подтверждение по ID — следующий03b.3b.2, затем shared UI. Текущий progress API по-прежнему запрещает изменение всей подготовленной матрицы. Проверки новой подчасти фиксируются в build-log; весь03b.3 не закрыт.


03b.3b.1 S04 pure allocation/ID lifecycle ci-verified: [checks40284878361](https://github.com/mendelev-main/M-POS-Android/actions/runs/40284878361)/cb9b4d3, job121446079712 —65JVM/0failures/0errors/0skipped (9 новых SplitEditor),6Python,lintDebug/assembleDebug passed. Эмулятор не перезапускался: редактор пока не подключён к Room/UI; это не evidence сохранённого редактирования частичной оплаты. Следующий03b.3b.2 —transactional integration/legacy replay, затем UI.
