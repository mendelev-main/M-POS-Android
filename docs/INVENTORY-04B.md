# 04b — физическая инвентаризация

Первый блок04b.1: настройки и durable запуск пересчёта; ci-verified / API36 / run40581633690, IC01–IC04. Фиксация остатков, actual inputs, завершение/отмена и история — следующие блоки; текущий блок не меняет склад.

Владелец выбрал продолжить инвентаризацию, отложив дополнительные расходы/сторно приёмки. Их наличие не подтверждено в pinned1682c883, не считать обязательной parity функцией. Основной порядок DELIVERY-ROADMAP:04a→04b.

## Источники и границы

Pinned `Web/js/features/inventory.js`: inventoryPeriodStart/End/Window, saveInventorySettings, startInventory, renderInventoryScreen/WorkScreen. `android-inventory.js` переопределяет baseline и отмену: фиксированные строки сохраняются, старые после перезапуска не разрешают фиксацию. `tests/source-product-stock.test.cjs` покрывает период, настройки, фиксацию/отмену и группировку; при реализации baseline читать Android adapter вместе с core. Reference не обновлялся.

Настройки: еженедельно/ежемесячно/ежеквартально, выбранные simple товары с учётом остатков, поиск/категории. При сохранении нужен хотя бы один доступный товар. Weekly начинается с понедельника, monthly с1числа, quarterly с1числа квартала; окно напоминания первые7дней периода. В04b.1 окно показано на экране настроек; условную вкладку верхней панели подключаем с рабочим экраном фиксации04b.2. Даты календарные по устройству, без вычисления дней через86400000 (DST). Завершённый плановый пересчёт закрывает напоминание до следующего периода; внеплановый не закрывает плановый. Этот completion marker подключается в блоке завершения, не имитируется запуском.

Один активный документ. Start использует сохранённые настройки и свежие снимки выбранных товаров в transaction; тип scheduled/adhoc, UUID, startedAt/scheduledDate, immutable scope. Повтор command возвращает прежний ID, новый Start при активном документе отказывается. Изменение настроек не меняет уже начатый scope. Возврат к настройкам/кассе сохраняет документ. В04b.1 нет actual inputs/фиксации и нет обещания о валидности старого физического baseline: снимок при старте только информационный.

Room8 namespace `inventory` payload1: config+active draft, CAS document revision; result/audit в одной transaction. SaveConfig/Start inventory only; product/balance/cost/ledger/cash/purchase документы не меняются. Owner guard до ролей05, installation gate, same-command recovery, optimistic revision. Повреждённый/неизвестный payload отказывается читаться без сброса. Выбранные в config/active товары защищены от удаления; чтобы освободить config reference, убрать товар из настроек (draft scope остаётся защищённым). Изменившийся tracking mode не даёт начать новый пересчёт до обновления выбора.

## Acceptance04b.1

- IC01: календарь weekly/monthly/quarterly,7дней, leap/year/DST-safe dates, completed marker из будущего completion не подставляется.
- IC02: config save/reopen, tracked simple validation, active scope independent of changed settings, start/resume and duplicate refusal; no stock/cash writes.
- IC03: CAS/owner/installation gate, each3write failure/SQL abort, lost reply/original replay/concurrent start; corrupt format preserved and product refs guarded.
- IC04: shared UI/settings route, selection/search, dirty safeguards, pending/unknown locks, recreation; actual APK replacement retains config and active draft.

## Следующие блоки

04b.2: ввод actual и baseline в момент ввода, durable raw fields, process-generation invalidation; Fix одной строки атомарно пишет delta/balanceRevision/immutable fixed snapshot/result/audit. Продажа/приёмка после ввода actual требует нового пересчёта,0 допустим, отрицательный actual запрещён как Android Web, дробь0.0004 сохраняется точно. NoTracking/composite нельзя фиксировать. Cost/price не менять.

04b.3: complete после фиксации всех строк; summary shortages/surpluses, historical cost snapshot и оценочный loss (отдельно закрепить rounding примерами перед расчётами). CancelRemainder сохраняет уже применённые строки, последующие продажи/приёмки не откатывает; история completed/cancelled и scheduled marker. Повтор complete/cancel не дублирует запись. Physical tablet/dark/150% и реальные process-kill checks остаются отдельной приёмкой.

## Проверки первого блока

04b.1 IC01–IC04 ci-verified: [FullCI40581633690](https://github.com/mendelev-main/M-POS-Android/actions/runs/40581633690)/d72af5e SUCCESS:118JVM/170API36 instrumentation/0failures/0skipped,12Python/lint/debugAPK/testAPK;2workspace-scale/16UIseed/11upgrade fixtures and actual debugAPK1→2. Inventory config/active scope/type/id/product snapshots and original Save/Start replay retained without stock/cost/movement writes. All previous financial/catalog/workspace/parked/home/shift/supplier/purchase/receiving assertions retained and passed. inventory-start screenshot inspected: settings, active scope and shared controls readable, list scrolls without overlap. No local user DB seed/main/release/signing/SQLschema change. Physical tablet/dark/150%/forced cold-process inventory UI pending. Next04b.2 actual input/fresh baseline/Fix; complete/cancel/history04b.3. Whole04 not complete; receiving charges/storno deferred by owner.
