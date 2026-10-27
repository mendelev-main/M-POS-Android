# 04a.7 — приёмка без заказа и ввод накладной

Статус: in progress, SA01–SA05. Pinned1682c883 receiving-drafts.js openReceivingDocument/saveReceivingDraft; receiving-ui.js updateInvoiceLine/add/remove; receiving.js invoiceReceivingItems/applyReceivingDocument. Android inventory adapter не меняет receiving. Reference не обновлялся.

## Независимый документ

«Приёмка без заказа» открывает один самостоятельный saved draft installation, как Web. Никакой фиктивной закупки: order=null, optional supplier snapshot id/name/revision, UUID invoice, номер/дата и строки simple товаров. Без поставщика допустимо как Web. Каталог поставщика не ограничивает произвольную накладную: можно выбрать любой simple, без дублей. Добавление/удаление только здесь; по заказу прежнее правило quantity0. Пустой/неполный draft можно сохранить, allzero/empty проводить нельзя. Dirty close/reload/switch подтверждаются. Удаление черновика не трогает склад/заказы.

Registry receiving-drafts payload2 reads1, reserved permanent slot `receiving:standalone` для CAS/tombstone. Реальные order IDs из UUID factory не используют reserved slot; opening такого ID отказывается. Новый документ после Clear/Post получает новый UUID, но монотонная slot revision сохраняется. Старый Save replay после Clear/Post не восстанавливает документ. Заказные черновики сохраняют свои независимые slots и fingerprints.

Product/stock/cost/tracking baseline актуализируется явно, сохраняя raw facts. Supplier snapshot обновляется явно; stale/deleted supplier запрещает Save/Post, можно явно выбрать другого или Без поставщика без потери строк. Draft может хранить stale stock facts, но preview/post требуют live baseline. Historical standalone product/supplier refs защищены guard каталога/поставщика, включая активный saved draft. Цена продажи/default packs/current purchase draft не меняются.

## Проведение и история

Общий PostingRepository: installation gate→owner→same-command outcome→saved slot CAS/payload→supplier/current economic baseline→recompute→products/stock movements/balance revisions→immutable receiving→slot tombstone→result/audit. Standalone не читает/не пишет purchase-orders, не создаёт PurchaseOrder и не закрывает другие заказы. На одной tracked строке7write boundaries (3product/ledger/balance +4record/draft/result/audit). NoTracking cost only, zero rows no product writes. Cost policy2=2decimal HALF_UP, exact quantities/line totals; cash/shift прежние.

History record сохраняет order=null/supplier/полные invoice facts/posted changes/costPolicy. Unknown expected, shortage/overage отсутствуют для standalone, подтверждение превышения не нужно. Idempotency по command +invoice UUID; второй command для того же invoice не проводит снова. Несколько последовательных standalone receipts допустимы, уникальность order применяется только к непустым order refs. Legacy order-bound history and replay unchanged.

## Режим цены — открытый контракт

Вопрос владельцу:1.5кг×2.35BYN=3.525→сумма строки3.53 HALF_UP2, итог из округлённых строк;3коробки×10BYN=30. Предложение пока не ответ, зависимая математика режима цены не подключается до ответа. Input price относится к invoice unit, не base stock unit; переключение l→ml обязано сохранять physical qty/economic amount или явно очищать несовместимую цену, не изобретать pack size. Точная сумма накладной в total mode остаётся источником стоимости как прежде.

## Acceptance

- SA01: standalone save/resume/clear/supplier/simple rows/dirty safeguards, no synthetic orders.
- SA02: exact posting/cost/zero/noTracking; unchanged purchase docs/price/defaults/cash.
- SA03: slot CAS/concurrency/owner/gate/each write failure/SQL abort/lost reply/replay.
- SA04: product/supplier guards, stale baseline/rebase, historical refs and legacy codec/fingerprints.
- SA05: UI/recreate/history and actual APK replacement retain independent saved+posted invoice and legacy orders.

Local/CI evidence добавляется после результата. Physical tablet/dark/150%/forced cold-process UI отдельно. Допрасходы/сторно/inventory не входят.
