package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.domain.workspace.CartEngine
import org.json.JSONObject

/** Caller must hold the same read transaction as the summary. */
internal suspend fun CatalogDatabase.reportHistory(receipts: List<ReceiptEntity>,snapshots: Map<String,ReceiptSnapshot>): List<ReportDetailSale> = receipts.map {r->
    fun corrupt(): Nothing=throw CatalogFailure("report","Повреждена историческая детализация чека")
    val snapshot=snapshots[r.id] ?: corrupt()
    val shift=shifts().shift(r.shiftId) ?: corrupt()
    if(shift.actorId.isBlank() || shift.actorName.isBlank()) corrupt()
    val metadata=snapshot.products.map {JSONObject(it)}
    if(metadata.map {it.getString("id")}.distinct().size!=metadata.size) corrupt()
    val byId=metadata.associateBy {it.getString("id")}
    val money=snapshot.quote.lines
    if(money.map {it.id}.distinct().size!=money.size || snapshot.cart.lines.map {it.id}.distinct().size!=snapshot.cart.lines.size ||
        money.map {it.id}.toSet()!=snapshot.cart.lines.map {it.id}.toSet()) corrupt()
    val calculator=RecipeCalculator(snapshot.cart.graph)
    val lines=snapshot.cart.lines.map {line->
        val product=byId[line.productId] ?: corrupt()
        val amount=money.single {it.id==line.id}
        if(amount.gross<0 || amount.discount<0 || amount.net<0 || amount.gross.toBigInteger()-amount.discount.toBigInteger()!=amount.net.toBigInteger()) corrupt()
        val quantity=line.quantity.toBigDecimalOrNull() ?: corrupt()
        if(quantity.signum()<=0 || line.name.isBlank()) corrupt()
        quantityUnit(line.unit)
        val category=if(product.isNull("categoryId")) "" else product.getString("categoryId")
        val categoryName=product.getString("categoryName")
        if(category.isNotEmpty() && categoryName.isBlank()) corrupt()
        val cost=calculator.costMinor(CartEngine.requests(snapshot.cart.copy(lines=listOf(line))))
        ReportDetailLine(line.id,line.productId,line.name,category,categoryName.ifBlank {"Без категории"},line.unit,quantity,amount.net,cost)
    }
    if(lines.fold(java.math.BigInteger.ZERO) {a,l->a+l.netMinor.toBigInteger()}!=snapshot.quote.subtotal.toBigInteger() ||
        snapshot.quote.subtotal.toBigInteger()+(snapshot.quote.delivery?.fee ?: 0).toBigInteger()!=r.total.toBigInteger()) corrupt()
    ReportDetailSale(r.id,shift.actorId,shift.actorName,lines)
}
