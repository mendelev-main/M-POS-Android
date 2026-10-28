# Этап 04 — Склад

Статус: in progress —04a.1–04a.7 и04b.1 ci-verified/API36;04b.2 count/fix ci-verified/API36/run40640734638; весь04 не закрыт

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

04a.5: [receiving draft/preview](../../docs/RECEIVING-DRAFT-04A.md), RI01–RI05 ci-verified/API36/run40502219375. Actual commit separate04a.6.

04a.5 итог: [FullCI40502219375](https://github.com/mendelev-main/M-POS-Android/actions/runs/40502219375)/a8d8c61 SUCCESS first attempt:100JVM/134API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/13UIseed/8upgrade fixtures and actual debugAPK1→2. RECEIVING DRAFT UPDATE PASSED: invoice facts, baseline, exact preview and old save replay preserved without stock/order posting. Existing sale/split/refund/catalog/workspace/parked/home/shift/supplier/purchase assertions retained and passed. Physical tablet/dark/150%/forced cold-process receiving UI pending; whole04 remains in progress. Next04a.6 receiving commit. Main/release/signing/SQLschema unchanged.

04a.6 RP01–RP05 ci-verified/API36/run40512178440: [проведение и история приёмки](../../docs/RECEIVING-POST-04A.md). Saved-only atomic stock/cost/ledger/order received/draft tombstone/result/audit; immutable posted changes and source facts, explicit overage, stale baseline refusal, same-command recovery. Whole04 remains in progress.

04a.6 итог: [FullCI40512178440](https://github.com/mendelev-main/M-POS-Android/actions/runs/40512178440)/2d8a183 SUCCESS first attempt:103JVM/145API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/14UIseed/9upgrade fixtures и actual debugAPK1→2. RECEIVING POST UPDATE PASSED: immutable invoice/posted changes, exact stock13/cost2.461538461538, received order/actor/link, draft tombstone and original post replay retained without another stock movement. All existing financial/catalog/workspace/parked/home/shift/supplier/purchase assertions retained and passed. Physical tablet/dark/150%/forced cold-process receiving UI pending. Whole04 remains in progress; main/stable/signing/SQLschema unchanged.

Уточнение RP01/RP05: confirmed cost policy2=2fraction HALF_UP; versioned compatibility/replay legacy1, new payload2 reads1. Ci-verified/API36/run40519616659; previous evidence refers to policy1.

Rounding follow-up acceptance: [CI40519616659](https://github.com/mendelev-main/M-POS-Android/actions/runs/40519616659)/5f8276d SUCCESS:103JVM/146API36 instrumentation/0failures/0skipped,12Python/lint/APKs,2scale/14UIseed/9upgrade fixtures and actual debugAPK1→2. Owner-confirmed policy2 cost2 HALF_UP verified (3шт/10BYN→3.33;1.005→1.01; invoice totals exact). Mixed legacy1/new2 registry and original command replay preserved without another movement; all previous sale/split/refund/catalog/workspace/parked/home/shift/supplier/purchase assertions passed. Deterministic delayed-history route and post-dialog success/recreate passed. No local user DB seed, no physical device check, no main/release/signing/SQLschema changes.

04a.7a SA01–SA05 ci-verified/API36/run40576618684: [приёмка без заказа](../../docs/RECEIVING-STANDALONE-04A.md). Independent order=null slot, optional supplier, simple add/remove, shared posting/history/guards. 04a.7b SA06–SA08 ci-verified/API36/run40576618684: owner confirmed per-line HALF_UP2 then sum; invoice-unit price, payload3 reads1/2, legacy fingerprints preserved.

04a.7 SA01–SA08 ci-verified: [FullCI40576618684](https://github.com/mendelev-main/M-POS-Android/actions/runs/40576618684)/a879923 SUCCESS:113JVM/160API36 instrumentation/0failures/0skipped,12Python/lint/debugAPK/testAPK;2scale/15UIseed/10upgrade fixtures and actual debugAPK1→2. Standalone invoice without synthetic purchase, raw price2.35×1.5→3.53, stock10→11.5/cost2→2.05, next saved price draft, old policy1/history and original save/post replay preserved without duplicate movements. Existing sales/split/refund/catalog/workspace/parked/home/shift/supplier/purchase assertions retained and passed. Screenshots standalone-draft/posted reviewed: price/quantity/calculated sum and shared controls readable, no overlap. No local user DB seed; physical tablet/dark/150%/forced cold-process receiving UI pending. Whole04 remains in progress. Main/stable/signing/SQLschema unchanged. Charges/storno and inventory remain separate future contracts.

04b.1 IC01–IC04 ci-verified/API36/run40581633690: [контракт](../../docs/INVENTORY-04B.md). Durable settings/scope/start/resume, no stock changes; actual counts/fix/complete/cancel/history next04b.2/04b.3. Owner chose inventory; receiving charges/storno deferred.

04b.1 IC01–IC04 ci-verified: [FullCI40581633690](https://github.com/mendelev-main/M-POS-Android/actions/runs/40581633690)/d72af5e SUCCESS:118JVM/170API36 instrumentation/0failures/0skipped,12Python/lint/debugAPK/testAPK;2workspace-scale/16UIseed/11upgrade fixtures and actual debugAPK1→2. Inventory config/active scope/type/id/product snapshots and original Save/Start replay retained without stock/cost/movement writes. All previous financial/catalog/workspace/parked/home/shift/supplier/purchase/receiving assertions retained and passed. inventory-start screenshot inspected: settings, active scope and shared controls readable, list scrolls without overlap. No local user DB seed/main/release/signing/SQLschema change. Physical tablet/dark/150%/forced cold-process inventory UI pending. Next04b.2 actual input/fresh baseline/Fix; complete/cancel/history04b.3. Whole04 not complete; receiving charges/storno deferred by owner.

04b.2 IF01–IF05 ci-verified/API36/run40640734638: exact count/fix, fresh process/stock baseline, immutable fixed snapshot and shared work UI. See ../../docs/INVENTORY-04B.md. Complete/cancel/history04b.3 separate; no main/release.

04b.2 IF01–IF05 ci-verified: [FullCI40640734638](https://github.com/mendelev-main/M-POS-Android/actions/runs/40640734638)/d827373 (runtime14108f6) SUCCESS:124JVM/184API36 instrumentation/0failures/0skipped,12Python/lint/debugAPK/testAPK;2workspace-scale/16UIseed/12upgrade fixtures and actual debugAPK1→2. Exact raw0,0004/fixed0.0004/delta/revision/immutable history and original Fix replay retained without a second movement; unfixed raw preserved while old process authority expires. Literal payload1 and frozen legacy Configure fingerprint retained. Real sale→refund same-stock/receiving stale cases, cost-only allowed, tracking off/on revision invalidation, each3Count/5Fix failure/SQL abort/lost reply/owner/gate/concurrent Fix passed. UI showed stale refusal→fresh blank recount→exact Fix→recreation→pause/workspace/resume; screenshots inventory-start/stale/fix-confirm/fixed inspected readable, shared controls and conditional navbar, no overlap. All previous financial/catalog/workspace/parked/home/shift/supplier/purchase/receiving assertions retained and passed. Physical tablet/dark/150%/forced cold-process inventory UI pending. Main/stable/signing/SQLschema unchanged; no local user DB seed. Next04b.3 complete/cancel remainder/history and loss contract, not implemented; whole04 remains in progress.

04b.3 IT01–IT05 implemented, проверки pending: Complete/CancelRemainder/history, подтверждённые HALF_UP по строкам и marker по дате завершения. Контракт docs/INVENTORY-04B.md; fullCI evidence будет записано после наблюдения результата. Весь04 in progress.
