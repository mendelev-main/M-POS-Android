# Этап 04 — Склад

Статус: in progress —04a.1–04a.4 suppliers/drafts/requests/edit-cancel ci-verified/API36/run40487143428; весь04 не закрыт

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

04a.2: [черновик закупки](../../docs/PURCHASE-DRAFT-04A.md), PO01–PO05 implemented; I01/I04 только в scope drafts, полная приёмка pending. Текущие проверки записываются в журнал после результата.

04a.2 PO01–PO05 ci-verified в scope draft: [FullCI40477779141 attempt2](https://github.com/mendelev-main/M-POS-Android/actions/runs/40477779141/attempts/2)/f4c80e2 SUCCESS:88JVM/108API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/10UIseed/3financial+1supplier+1purchase fixture и actual debugAPK1→2. PurchaseUpgradeTest проверяет raw24×500ml→12l, historical supplier/product snapshots, replay без повторной записи и неизменные stock/cost/movements. Исходные sale/split/refund/catalog/workspace/parked/home/shift assertions также прошли. Physical/dark/150%/cold-process UI отдельно; receiving pending.

04a.3: [формирование и история](../../docs/PURCHASE-ORDERS-04A.md), PO06–PO10 ci-verified/API36/run40483522354. Pending request +defaults +clear draft; no receiving.

04a.3 итог: [FullCI40483522354](https://github.com/mendelev-main/M-POS-Android/actions/runs/40483522354)/dbd5fb2 SUCCESS:91JVM/116API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/11UIseed/6upgrade fixtures (3financial+supplier+purchase draft+purchase order), actual debugAPK1→2. PURCHASE ORDER UPDATE PASSED: pending snapshot/raw quantities/requestVersion1, cleared draft, current product metadata/defaults and replay without duplicate preserved. Existing sale/split/refund/catalog/workspace/parked/home/shift assertions также прошли. Следующий04a.4 edit/cancel pending; receiving позже.

04a.4: [изменение и отмена](../../docs/PURCHASE-ORDER-CHANGES-04A.md), PO11–PO15 ci-verified/API36/run40487143428. No receiving.

04a.4 итог: [FullCI40487143428](https://github.com/mendelev-main/M-POS-Android/actions/runs/40487143428)/a751e8b SUCCESS first attempt:95JVM/125API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/12UIseed/7upgrade fixtures (3financial+supplier+purchase draft+purchase order+order mutation), actual debugAPK1→2. PURCHASE ORDER MUTATION UPDATE PASSED: request history, cancellation actor/status, unchanged stock/defaults and old edit replay preserved; existing financial/catalog/workspace/parked/home/shift assertions также passed. Next04a.5 receiving draft/preview без stock commit; весь04 in progress.

04a.5: [receiving draft/preview](../../docs/RECEIVING-DRAFT-04A.md), RI01–RI05 implemented/CI pending. Actual commit separate04a.6.
