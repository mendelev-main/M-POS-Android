# 04b — физическая инвентаризация

Первый блок04b.1: настройки и durable запуск пересчёта; ci-verified / API36 / run40581633690, IC01–IC04. Фиксация остатков, actual inputs, завершение/отмена и история — следующие блоки; текущий блок не меняет склад.

Текущий шаг04b.2: exact count/fix ci-verified/API36/run40640734638; завершение/отмена и история документа04b.3 ещё не подключены.

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

## 04b.2 — actual и фиксация строки

Статус: ci-verified / API36 / run40640734638, IF01–IF05. Завершение/отмена оставшихся строк/история документа остаются04b.3; стоимость недостач пока не рассчитывается.

Рабочий экран открывается из условной вкладки «Инвентаризация» (активный документ или плановое окно), группирует товары по сохранённым категориям, показывает учётный остаток, сохранённый факт и неизменные фиксации. «Продолжить позже» сохраняет уже записанное. Настройки доступны отдельно. Shared Mpos controls/dialogs/close и прежний верхний navbar; незавершённый count input/pending/unknown блокируют навигацию.

«Ввести факт/Пересчитать» сначала читает свежий baseline из Room, затем открывает **пустую** форму. Оператор пересчитывает товар и вводит значение; «Сохранить пересчёт» — явная durable граница, склад не меняется. Если склад изменился между открытием формы и сохранением, команда отказывается, raw input остаётся в форме. «Пересчитать заново» явно очищает ввод и читает новую версию. Старое сохранённое значение показывается отдельно и никогда не подставляется автоматически в новый пересчёт. Такой порядок консервативнее Web onchange: версия захватывается перед физическим пересчётом, не подменяется на более свежую после ввода.

Baseline: ID, base unit, stock quantity/revision и UUID текущего процесса. После process restart/обновления APK raw остаётся, но незавершённый baseline не разрешает фиксацию. Activity recreation в том же процессе сохраняет authority. Изменение цены/имени/категории/cost без изменения склада не требует нового физического пересчёта, как pinned Android Web test `stock unit change invalidates count but cost-only edit does not`. Tracking off/on увеличивает balance revision даже без изменения quantity; это добавлено в общий product-card transaction. Продажа→возврат к прежнему числу тоже инвалидирует baseline. NoTracking/composite/missing/unit change нельзя фиксировать.

Fix выполняется только для сохранённого count активного scope, при совпадении document CAS, process generation и stock baseline. Actual неотрицательный, пусто не равно0, до12fraction/18integer; `0,0004` сохраняется raw и становится точным0.0004 в balance. Delta=actual−baseline, без display rounding; переполнение delta/revision отказывается. Cost/price не меняются. В fixed snapshot записывается **актуальная** cost на момент фиксации для будущей истории, не для финансовой операции.

Одна transaction: movement(reason=draftId/source=inventory)→balance quantity/revision→inventory fixed snapshot→result→audit.5write boundaries, всегда записывается движение (включая0) и stock revision+1: нулевое расхождение тоже фиксирует факт пересчёта. Это документированное native уточнение; Web не меняет свою revision при неизменной stock identity. Повтор исходной команды возвращает result без второго движения, в том числе после перезапуска. Новая Count/Fix для fixed строки отказывается; последующие продажи/приёмки не меняют исторический fixed snapshot.

Inventory payload2 reads1: v1 active scope открывается без counts и не получает фиктивного физического baseline. Count сохраняет raw/baseline/generation, fixed сохраняет actual/delta/product-at-fix/actor/time/command/movement/stockRevisionAfter. Decoder проверяет согласованность actual/delta/revision и scope. Старые Configure/Start fingerprint namespace/поля неизменны; новые Count/Fix связывают baseline/generation/revision. SQLschema8 прежняя; include in future backup, import/process restart не оживляет authority.

- IF01: exact actual0/0.0004, blank/negative/precision refusal, fresh capture/recount, cost-only allowed; stock/revision/unit/tracking/process changes stale.
- IF02: atomic exact movement/balance/fixed/result/audit, zero delta, unchanged cost/price/cash, immutable fixed row, same-command replay.
- IF03: real sale→refund/receiving stale cases, each3Count/5Fix failure, SQL abort/lost reply, owner/gate/CAS/concurrency, corrupted fixed refusal.
- IF04: working UI/category groups, conditional topbar/locks, fresh blank entry, saved raw/recreation/stale refusal/recount/fix and historical display.
- IF05: payload1→2/frozen legacy Configure fingerprint, actual APK replacement preserves fixed +unfixed raw and old command replay; unfixed process authority expires.

Physical tablet/dark/150%/forced cold-process UI остаются pending; injected process-generation tests и actual APK replacement не подменяют эту ручную приёмку.

## Приёмка count/fix

04b.2 IF01–IF05 ci-verified: [FullCI40640734638](https://github.com/mendelev-main/M-POS-Android/actions/runs/40640734638)/d827373 (runtime14108f6) SUCCESS:124JVM/184API36 instrumentation/0failures/0skipped,12Python/lint/debugAPK/testAPK;2workspace-scale/16UIseed/12upgrade fixtures and actual debugAPK1→2. Exact raw0,0004/fixed0.0004/delta/revision/immutable history and original Fix replay retained without a second movement; unfixed raw preserved while old process authority expires. Literal payload1 and frozen legacy Configure fingerprint retained. Real sale→refund same-stock/receiving stale cases, cost-only allowed, tracking off/on revision invalidation, each3Count/5Fix failure/SQL abort/lost reply/owner/gate/concurrent Fix passed. UI showed stale refusal→fresh blank recount→exact Fix→recreation→pause/workspace/resume; screenshots inventory-start/stale/fix-confirm/fixed inspected readable, shared controls and conditional navbar, no overlap. All previous financial/catalog/workspace/parked/home/shift/supplier/purchase/receiving assertions retained and passed. Physical tablet/dark/150%/forced cold-process inventory UI pending. Main/stable/signing/SQLschema unchanged; no local user DB seed. Next04b.3 complete/cancel remainder/history and loss contract, not implemented; whole04 remains in progress.
