# 04a.7 — приёмка без заказа и ввод накладной

Статус: implemented / CI pending, SA01–SA08. Pinned1682c883 receiving-drafts.js openReceivingDocument/saveReceivingDraft; receiving-ui.js updateInvoiceLine/add/remove; receiving.js invoiceReceivingItems/applyReceivingDocument. Android inventory adapter не меняет receiving. Reference не обновлялся.

## Независимый документ

«Приёмка без заказа» открывает один самостоятельный saved draft installation, как Web. Никакой фиктивной закупки: order=null, optional supplier snapshot id/name/revision, UUID invoice, номер/дата и строки simple товаров. Без поставщика допустимо как Web. Каталог поставщика не ограничивает произвольную накладную: можно выбрать любой simple, без дублей. Добавление/удаление только здесь; по заказу прежнее правило quantity0. Пустой/неполный draft можно сохранить, allzero/empty проводить нельзя. Dirty close/reload/switch подтверждаются. Удаление черновика не трогает склад/заказы.

Registry receiving-drafts payload3 reads1/2, reserved permanent slot `receiving:standalone` для CAS/tombstone. Реальные order IDs из UUID factory не используют reserved slot; opening такого ID отказывается. Новый документ после Clear/Post получает новый UUID, но монотонная slot revision сохраняется. Старый Save replay после Clear/Post не восстанавливает документ. Заказные черновики сохраняют свои независимые slots и fingerprints.

Product/stock/cost/tracking baseline актуализируется явно, сохраняя raw facts. Supplier snapshot обновляется явно; stale/deleted supplier запрещает Save/Post, можно явно выбрать другого или Без поставщика без потери строк. Draft может хранить stale stock facts, но preview/post требуют live baseline. Historical standalone product/supplier refs защищены guard каталога/поставщика, включая активный saved draft. Цена продажи/default packs/current purchase draft не меняются.

## Проведение и история

Общий PostingRepository: installation gate→owner→same-command outcome→saved slot CAS/payload→supplier/current economic baseline→recompute→products/stock movements/balance revisions→immutable receiving→slot tombstone→result/audit. Standalone не читает/не пишет purchase-orders, не создаёт PurchaseOrder и не закрывает другие заказы. На одной tracked строке7write boundaries (3product/ledger/balance +4record/draft/result/audit). NoTracking cost only, zero rows no product writes. Cost policy2=2decimal HALF_UP, exact quantities/line totals; cash/shift прежние.

History record сохраняет order=null/supplier/полные invoice facts/posted changes/costPolicy. Unknown expected, shortage/overage отсутствуют для standalone, подтверждение превышения не нужно. Idempotency по command +invoice UUID; второй command для того же invoice не проводит снова. Несколько последовательных standalone receipts допустимы, уникальность order применяется только к непустым order refs. Legacy order-bound history and replay unchanged.

## Режим цены — подтверждённый контракт

Владелец подтвердил:1.5кг×2.35BYN=3.525→3.53 HALF_UP до копейки **в каждой строке**, затем итог из округлённых строк. Две такие строки дают7.06, а не7.05.3коробки×10BYN=30.00, цена относится к единице накладной, размер упаковки влияет только на складское количество. Себестоимость рассчитывается из округлённой суммы строки по ранее утверждённой cost policy2.

Строка хранит `priceMode=total|price` и raw `unitPrice`. В total цена пустая; в price total пустая и вычисляется из raw quantity×unitPrice через BigDecimal. Никакой второй независимой суммы. Положительной строке нужна цена; неполный draft сохраняется, preview отказывается. Нулевое количество даёт0 без цены; полностью нулевой документ не проводится. Цена допускает до12 дробных/18 целых знаков, сумма — Long копейки с проверкой переполнения, отрицательные/невалидные числа отвергаются. Малые положительные значения могут округлиться в0.00 по общему правилу HALF_UP.

Переход price→total фиксирует текущую округлённую сумму. Переход total→price требует явно ввести цену: деление суммы на количество не создаёт новую цену с потерей точности. Физические l↔ml/kg↔g меняют количество и обратно цену точно, сохраняя сумму. Если цена после конверсии выходит за12 знаков, операция отказывается, исходный ввод остаётся. При смене на/с упаковки цена очищается для нового ввода; количество и pack facts остаются явными, размер1 не подставляется. Режим доступен и заказной, и независимой приёмке.

Receiving draft payload3 reads1/2/3; старые строки читаются как total/пустая цена. Immutable receivings payload2 содержит nested invoice3, старые invoice1/2 читаются. SQLschema8 прежняя. Старые total-mode command fingerprints остаются побайтно прежними; price mode включает цену/режим в отдельный fingerprint namespace. Повтор команды с изменённой ценой отказывается. История и повтор уже проведённых команд не пересчитываются из текущего каталога.

## Acceptance

- SA01: standalone save/resume/clear/supplier/simple rows/dirty safeguards, no synthetic orders.
- SA02: exact posting/cost/zero/noTracking; unchanged purchase docs/price/defaults/cash.
- SA03: slot CAS/concurrency/owner/gate/each write failure/SQL abort/lost reply/replay.
- SA04: product/supplier guards, stale baseline/rebase, historical refs and legacy codec/fingerprints.
- SA05: UI/recreate/history and actual APK replacement retain independent saved+posted invoice and legacy orders.

- SA06: per-line HALF_UP2, sum rounded lines, invoice-unit pack price, exact physical conversion/refusal and explicit mode switch.
- SA07: price draft restart, rollback at each posting boundary, same-command replay/conflict, payload1/2 compatibility.
- SA08: price entry/recreate/calculated sum in UI, actual APK replacement preserves raw price draft and posted invoice.

Local/CI evidence добавляется после результата. Physical tablet/dark/150%/forced cold-process UI отдельно. Допрасходы/сторно/inventory не входят.
