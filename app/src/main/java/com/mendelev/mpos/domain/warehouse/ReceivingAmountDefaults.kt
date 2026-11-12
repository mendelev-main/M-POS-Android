package com.mendelev.mpos.domain.warehouse

import java.math.BigDecimal
import java.math.RoundingMode

/** Suggestions use the current product cost; invoice amounts remain exact independent facts. */
object ReceivingAmountDefaults {
    fun cost(product: ReceivingProduct): BigDecimal?=runCatching {PurchaseDraftRules.number(product.cost)}.getOrNull()

    fun amount(line: ReceivingLine,basePrice: BigDecimal): String? = runCatching {
        if(PurchaseDraftRules.number(line.quantity)?.signum()==0) return@runCatching "0.00"
        val quantity=PurchaseDraftRules.expected(line.purchaseLine()) ?: return@runCatching null
        (quantity*basePrice).setScale(2,RoundingMode.HALF_UP).toPlainString()
    }.getOrNull()

    fun unitPrice(line: ReceivingLine): String? = runCatching {
        val quantity=PurchaseDraftRules.expected(line.purchaseLine()) ?: return@runCatching null
        if(quantity.signum()<=0) return@runCatching null
        val total=ReceivingRules.lineTotal(line) ?: return@runCatching null
        BigDecimal.valueOf(total,2).divide(quantity,2,RoundingMode.HALF_UP).toPlainString()
    }.getOrNull()

    fun shortage(record: PostedReceiving)=record.result.lines.any {it.shortage?.toBigDecimalOrNull()?.signum()==1}
}
