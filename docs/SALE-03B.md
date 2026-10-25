# 03b — оплата и история: контракт инкрементов

Статус:03b.1 money engine ci-verified; checkout/sale/history runtime planned. Это не готовая кнопка оплаты. Предпосылки:03a выпущен stable0.1.46.1, [MONEY-03](MONEY-03.md) и ответы владельца25.10.2026. BYN, API36+, единый дизайн pinned Web. Физические тесты по-прежнему позже.

## Источники и D02

Pinned1682c883: cart-presentation.js discountValue/itemTotal/cartSubtotal; pos.html cartTotal; payment.js buildSplitPayments/normalizePaymentParts/finalizePaymentSaved; shifts.js cashDrawerBalance; android-safety.js journal boundary. Fixed discount в Web на единицу, capped gross; percentage0–100; доставка добавляется к итогу и выдаётся наличными одним связанным движением. Sale сохраняет products/orders/shifts/current session до UI успеха/внешних эффектов. Confirmed partial split хранится отдельно и не теряется при закрытии.

Владелец подтвердил line HALF_UP2/total=sum rounded lines, одну скидку строки, delivery cash-out, cash change, remainder-first equal split, полный cross-shift refund первым. Native input отклоняет percentage>100/negative/invalid вместо скрытого clamp; fixed discount capped gross как Web. Лояльность06, refund03c, SDK acquiring/фискализация не подключаются автоматически.

## 03b.1 — денежное ядро

SaleMoney не зависит от Android/Compose/Room и не пишет финансовую операцию. Вход: стабильные line IDs, unitPrice≤2 decimal, qty≤9, одна percent/fixed скидка с snapshot названия; тип заказа и explicit DeliveryCharge(id/name/feeMinor/revision), подготовленные PaymentInput(method/amountMinor/givenMinor). delivery требует тариф, включая выбранный бесплатный тариф0; here/takeaway отвергают скрытый fee.

Вычисления BigDecimal до границы net line HALF_UP2→Long minor. Quote хранит rounded gross/discount/net каждой строки и checked totals. discountMinor=grossMinor−netMinor, а не самостоятельно округлённая скидка с повторным вычитанием. Процент — точный decimal shift на2 разряда: Kotlin BigDecimal operator / не используется, поскольку его scale может преждевременно округлить дробную скидку. Input/string/list limits, duplicate IDs, invalid method/given/sum и Long overflow дают отказ.

PaymentAllocation cashGiven/change сохраняются отдельно от cash net. ExpectedCashAfter=current expectedCash+cash net−deliveryCashOut; card не даёт наличных для курьера. Ровно один cash-out доставки; эта сумма не вычитается из оплаты чека. При shortage cash выдачи отказ. EqualSplit2–10 сохраняет каждую копейку; нулевые части допустимы, карта никогда не содержит given/change. Это расчёт подготовленных частей, не интеграция банковского списания и не durable подтверждение отдельных частей.

Примеры/tests:0.05×3−10%=0.135→0.14;3different lines0.045→0.05 each,total0.15;fixed1×2 →18+delivery5=23;cash40/card60/given50→change10/cash+40;card25/delivery5 needs cash5;10/3→3.34+3.33+3.33;0.01/3→0.01+0+0;overflow/malformed/refusal. Производные quote/payment DTO никогда не являются доверенным разрешением записи: future repository повторяет расчёт из committed snapshot внутри transaction.

## 03b.2 — сохраняемый checkout и atomic sale/history

Следующая вертикальная часть: durable checkout intent с commandId/cart owner+revision, снимками скидок/тарифа/частей. Смена/корзина/каталог изменились после preview →Conflict/reconfirm, не тихая замена суммы. Нынешний cart payload2 не содержит скидки/денежного тарифа, поэтому их сохранение и формат миграции нужно добавить явно; нельзя пометить старый delivery order бесплатно оплаченным без выбора тарифа.

CommitSale одной Room transaction: requireActive, owner/revisions/fingerprint, fresh refs/aggregate current+parked reservations, exact stock consumption, receipt/line/price/cost/category/recipe/flags/discount/payment/delivery snapshots, stock balances+ledger, shift totals/revision, очистка именно исходного current owner, result/audit/outbox только для соответствующих настроенных эффектов. Receipt уникален по cart session/command, номер внутри смены; повтор возвращает тот же ID даже после очистки current. Сбой не создаёт половину продажи. Historical refs защищают товар от удаления, категория/цены/рецепт не переписывают receipt. History/details читают snapshots после restart.

InstallationGate расширяется на sale/сохранение checkout/recovery. После process death lookup(commandId) определяет commit; pending intent не объявляется отменённым по отсутствию UI. Sale и CloseShift сериализуются в одной БД: sale вошла до close либо отказ после close. ExpectedCash/close preview должны включить продажи и delivery cash-out и инвалидироваться новым register revision.

Проверки: pure policy→Room migration всех прежних версий→fault каждой записи/повтор/concurrency/shortage0.0004kg/noStock/allowNegative/stale refs/close race→Compose empty/no shift/invalid inputs/commit/recreate/history→actual APK replacement сохраняет receipts/ledger/shift/current/parked. UI использует общие dialogs/Close/buttons и честно обозначает manual card accounting без SDK. Отдельное API подтверждения оплаченных split частей требует durable progress и защиты от смены суммы/возврата до завершения.

## Границы приёмки

S04 ci-verified только pure money часть, S01/S02/S05 planned до vertical sale; S03 в03c. Не присваивать financial ci-verified всему03b по денежным unit tests. Дальнейшие инкременты03b не выпускаются отдельно автоматически; готовый функциональный блок —PRdevelop→main по workflow владельца.

Evidence03b.1: [Android PR manual40223239353](https://github.com/mendelev-main/M-POS-Android/actions/runs/40223239353)/91acd06,55JVM/0failures,6Python/lint/assembleDebug passed.5new SaleMoney tests,46supplemental local cached Kotlin2.1.20 tests passed; actual pinned compiler вCI.45selected pinned Web payment/split/discount/delivery tests passed, SHA256 source совпали сconfig/reference.json. SQLschema6/cart payload2/UI не менялись, новый emulator suite не требовался для unused pure engine. S01/S02/S05 и actual checkout остаются planned.
