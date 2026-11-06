# 04a.4 — изменение и отмена ожидающего заказа

Статус: PO11–PO15 ci-verified/API36. Продолжает [04a.3](PURCHASE-ORDERS-04A.md), часть M11/I04. Весь склад04 не завершён.

## Источник и границы

Pinned Web1682c883, `Web/js/features/purchase-orders.js`: canEditPurchaseOrder/savePurchaseOrderEdit и admin delete handlers; `android-purchase-validation.js` — diagnostics/conversion refusal. Web edit проверяет baseline, обновляет items/requestVersion/updatedAt, не пишет product defaults. Web admin deletion запрещена для received, оставляет deleted marker и receiving history entry без складского проведения.

Native edit/cancel доступны только pending. Cancel — статус `cancelled`/«Отменён», а не физическое удаление и не новая приёмка с нулевой стоимостью. Предыдущие версии заявки сохраняются отдельными request snapshots — осознанное расширение относительно Web. Supplier/order/sourceDraft IDs и createdAt неизменны; текущее supplier name/revision остаётся историческим от формирования. При новой редакции item names/revisions захватываются из актуального каталога. Нет автосмены упаковки/количества на defaults.

Receiving draft, receipt, valuation, внешняя отправка и admin sessions05 ещё отсутствуют. До05 применяется repository current-local-owner guard, без заявления о готовой employee authorization. Будущий receiving обязан сохранить введённые факты при редактировании заявки и проверять новые order/request revisions перед commit, как предусмотрено WAREHOUSE-SPEC. Это не реализуется фиктивной очисткой отсутствующего receiving draft.

## Команды и версии

PurchaseOrderCommand(commandId, actorId, orderId, expected order revision, Edit(draft)|Cancel). Fingerprint включает actor/action/revision/orderId и полный edit payload. Place/Save старых04a.2/04a.3 не меняли fingerprint.

Pending revision=requestVersion. Edit повышает обе версии на1, создаёт current positive-only snapshot и добавляет прежние source/expectedQuantities/requestVersion/at в history. Empty/zero строки не включаются; пустой результат/invalid decimal/unit отвергаются. Unknown pack expected=null сохраняется. Количества/пересчёты по DATA, BigDecimal12fraction/18integer, без money/stock округления.

Cancel повышает только order revision, сохраняет requestVersion/current lines/history, пишет cancelledAt/cancelledBy и updatedAt. Повтор с исходным commandId возвращает orderId; новый cancel или edit после отмены отклоняются. Старый edit replay после отмены не восстанавливает pending. Текст отменённого заказа явно содержит статус и отсутствие ожидания поставки.

Edit capture при открытии использует актуальные доступные refs из прочитанного snapshot; supplier immutable для заказа. Недоступная/отвязанная строка остаётся видимой до явного удаления. Save требует актуальные supplier/product revisions, существование simple и привязки. «Обновить ссылки» сохраняет введённые qty/pack/content, но не усваивает изменившуюся order revision: после конфликта нужно закрыть редактор и перечитать историю. Cancel не требует свежих catalog refs, так как не создаёт новую заявку или складское движение.

## Атомарность и независимый новый draft

InstallationGate →Room transaction→owner→result replay→order revision/status→live refs for Edit→orders document→operation_result→audit. Три write boundaries; отказ любого шага откатывает всё. Не меняются product/defaults/cost/price/revision, balances/revisions/movements, current purchase draft, checkout/cash. Одновременные edit/cancel одной revision имеют одного победителя. Команда и списки фиксируются перед ожиданием gate.

Редактор сформированного заказа независим от нового `purchase-draft`. Даже если пользователь набрал новую закупку и не сохранил её, история/редактирование/отмена старого заказа сохраняют этот ввод и исходную CAS revision нового draft. После mutation обновляются только orders/products/suppliers read models; чужая новая draft revision не подставляется незаметно.

Unknown mutation outcome удерживает ту же команду и edit form, блокирует закрытие/повторное действие до проверки результата. Known commit + failed history reload закрывает уже сохранённый edit buffer, сохраняет независимый draft, показывает известный результат и отдельное перечитывание истории. Stale detail не выдаётся за актуальную карточку до успешного чтения. Failed plain history read не сообщает ложное сохранение.

## Формат и совместимость

Room SQLschema8 остаётся прежней. `purchase-orders` namespace пишет payload2; decoder читает payload1 (pending/requestVersion1) без сброса и повышает формат при следующей успешной записи. Payload2 добавляет revision/updatedAt/cancelledAt/cancelledBy и history snapshots. Проверяются последовательность версий, статус, согласованность revision, expected quantities и неизменные source/supplier identities. Неизвестный/corrupt payload блокирует чтение/запись. Payload1 `purchase-draft` не меняется.

Delete guards товаров учитывают current и previous request lines, включая удалённую из последней редакции строку. Supplier остаётся защищён историей. Cancel не освобождает исторические ссылки. Оба документа постоянные и входят в будущий backup, а не cache.

## Интерфейс и acceptance

Общий MposDialog/Close/Field/Select/Primary/Secondary/Danger. Edit form прокручивается, dirty close запрашивает подтверждение; recreation сохраняет несохранённую форму через ViewModel. Process death до save не обещает восстановление edit buffer. В карточке видны текущая requestVersion и прежние версии. Cancel подтверждается отдельно; отменённая карточка не содержит edit/cancel actions, receiving disabled. Copy — локальный текст, сеть отсутствует.

| ID | Проверки |
| --- | --- |
| PO11 | Edit pending, positive/unknown pack, previous snapshots, fixed identities, no product/stock changes |
| PO12 | Cancel marker/actor/time/history; rejected edit/cancel after cancellation; old replay does not restore |
| PO13 | CAS/concurrency/owner/gate/live refs/fingerprint; each write failure/SQLite abort/lost reply |
| PO14 | Edit dirty close/recreate, cancel confirmation, history/status; independent dirty new draft and CAS preserved |
| PO15 | Payload1→2/reopen/corruption refusal; actual APK replacement retains edits/history/cancel/replay |

Tests: PurchaseOrderMutationRulesTest4 JVM, PurchaseOrderMutationRepositoryTest4 API36, PurchaseViewModelTest+3, PurchaseOrderMutationUiTest1, PurchaseOrderMutationUpgradeTest1. Existing suppliers/draft/place and financial suites retained. CI evidence follows execution; physical/dark/150%/forced cold-process UI remain pending.

Следующий bounded04a.5 — receiving draft из pending request: ввод фактических quantities/costs, явный preview без движения; commit receiving отдельной частью после его invariants. Согласованы weighted cost;3/10 closes shortage7;12/10 explicit confirmation. Отменённый заказ нельзя превращать в receiving.

## Результат

[FullCI40487143428](https://github.com/mendelev-main/M-POS-Android/actions/runs/40487143428)/a751e8b SUCCESS first attempt:95JVM/125API36 instrumentation/0failures/0skipped,12Python/lint/build;2scale/12UIseed/7upgrade fixtures (3financial+supplier+purchase draft+purchase order+order mutation), actual debugAPK1→2. PURCHASE ORDER MUTATION UPDATE PASSED: request history, cancellation actor/status, unchanged stock/defaults and old edit replay preserved; existing financial/catalog/workspace/parked/home/shift assertions также passed.

Hosted XML95/0 и HTML125/0/0 проверены. Screenshots edit/edited/cancelled1600×1125 просмотрены: единый Close/actions, fields12×500ml→6l, версия2 и прежняя24×500ml→12l видны; cancelled marker/disabled receiving, без перекрытий/IME/постороннего окна. UI test сохранил независимый dirty draft5 через edit/cancel/recreate и успешно записал его с исходной CAS. Отдельный Room fixture проверил payload1→2 и reopen. Physical/dark/150%/forced cold-process UI pending. Main/stable0.1.74.1 и SQLschema8/signing не менялись.
