package com.mendelev.mpos.print

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.ShiftSummary
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.domain.sale.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

enum class PrintKind { RECEIPT, BILL, KITCHEN, SHIFT, TEST }
data class PrintItem(val name: String,val quantity: String,val price: String,val net: Long,val discount: Long=0,val discountName: String="Скидка",val category: String="",val comment: String="",val options: List<ChosenOption> = emptyList(),val id: String="")
data class PrintPayment(val method: String,val amount: Long,val given: Long?=null,val change: Long?=null)
data class PrintDocument(val key: String,val kind: PrintKind,val at: Long,val label: String="",val number: String="",val employee: String="Сотрудник",val type: String="На месте",val items: List<PrintItem> = emptyList(),val total: Long=0,val gross: Long=0,val discount: Long=0,val delivery: Long=0,val payments: List<PrintPayment> = emptyList(),val customer: String="",val phone: String="",val shiftRows: List<Pair<String,String>> = emptyList(),val session: String="",val loyaltyRows: List<Pair<String,String>> = emptyList(),val loyaltyDiscounts: List<Pair<String,Long>> = emptyList())
data class PrintRow(val left: String="",val right: String?=null,val size: Float=23f,val weight: Int=400,val centered: Boolean=false,val separator: Boolean=false,val gap: Float=4f)
data class ReceiptPage(val width: Int,val margin: Float,val rows: List<PrintRow>,val top: Float=0f,val bottom: Float=4f)

object PrinterRouting {
    fun targets(kind: PrintKind,printers: List<PrinterConfiguration>,automatic: Boolean=false)=printers.filter {
        it.enabled && if(kind==PrintKind.KITCHEN) it.printOrders && (!automatic || it.autoKitchen)
        else it.printReceipts && (!automatic || it.autoReceipt || kind==PrintKind.SHIFT)
    }
    fun manualJobs(document: PrintDocument,printers: List<PrinterConfiguration>): List<PrinterConfiguration> =
        targets(document.kind,printers).filter {p->
            p.ip.isNotBlank() && (document.kind!=PrintKind.KITCHEN || document.items.any {
                p.categories.isBlank() || it.category in p.categories.lines().map(String::trim)
            })
        }.flatMap {p->List(p.copies.toInt()) {p}}
    fun opensDrawer(document: PrintDocument)=document.kind==PrintKind.RECEIPT && document.payments.any {it.method=="cash" && it.amount>0}
}

object PrintDocuments {
    private fun quantity(raw: String): String {
        val value=raw.toBigDecimal().stripTrailingZeros()
        return if(value.scale()<=0 || value.signum()!=0 && value.abs()<BigDecimal("0.005")) value.toPlainString()
        else value.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()
    }
    fun money(minor: Long)=BigDecimal.valueOf(minor,2).toPlainString()+" BYN"
    private fun money(minor: BigInteger)=BigDecimal(minor,2).toPlainString()+" BYN"
    fun receipt(value: Pair<ReceiptEntity,ReceiptSnapshot>,employee: String,effect: LoyaltyEffect?=null): PrintDocument {
        val (record,s)=value
        val categories=s.products.map {JSONObject(it)}.associate {it.getString("id") to it.optString("categoryName")}
        return cart(s.cart,s.quote,PrintKind.RECEIPT,employee,categories,s.checkout.discounts).let {doc->doc.copy(items=doc.items.mapIndexed {index,item->item.copy(discountName=s.checkout.discounts[s.cart.lines[index].id]?.name ?: item.discountName)})}.copy(key="receipt:${record.id}",at=record.createdAt,number="#${record.number}",payments=s.payment.parts.map {PrintPayment(it.method,it.amount,it.given,it.change)},loyaltyRows=LoyaltyReceiptRows.build(s,effect))
    }
    fun cart(cart: CurrentCart,quote: SaleQuote,kind: PrintKind,employee: String,categories: Map<String,String>,discounts: Map<String,SaleDiscount> = cart.lines.mapNotNull {l->l.discount?.let {l.id to it}}.toMap()): PrintDocument {
        val allocation=com.mendelev.mpos.domain.loyalty.LoyaltyRules.allocation(cart,discounts)
        val loyaltyTotal=allocation.gifts.fold(0L) {sum,g->Math.addExact(sum,g.discount)}
        return PrintDocument("cart:${cart.sessionId}",kind,cart.createdAt,cart.details.label,"",employee,OrderRules.typeLabel(cart.details.type),cart.lines.map {l->
            val amount=quote.lines.single {it.id==l.id}
            PrintItem(l.name,l.quantity,l.unitPrice,Math.addExact(amount.net,allocation.byLine[l.id] ?: 0L),amount.discount-(allocation.byLine[l.id] ?: 0L),l.discount?.name ?: "Скидка",categories[l.productId].orEmpty(),l.comment,l.options,l.id)
        },quote.total,quote.gross,quote.discount-loyaltyTotal,quote.delivery?.fee ?: 0,customer=cart.details.customerName,phone=cart.details.phone,session=cart.sessionId,loyaltyDiscounts=allocation.gifts.groupBy {it.programName}.map {(name,gifts)->name to gifts.fold(0L) {sum,g->Math.addExact(sum,g.discount)}})
    }
    fun shift(s: ShiftSummary)=PrintDocument("shift:${s.shift.id}",PrintKind.SHIFT,s.shift.closedAt ?: s.shift.openedAt,employee=s.shift.actorName,shiftRows=listOf(
        "Открыта" to receiptDate(s.shift.openedAt),"Закрыта" to receiptDate(s.shift.closedAt ?: s.shift.openedAt),"Заказов" to s.count.toString(),
        "Выручка" to money(s.revenue),"Наличные" to money(s.cashNet),"Карта" to money(s.cardNet),"Наличные на начало" to money(s.shift.openingCash),
        "Внесения" to money(s.deposits),"Изъятия" to money(s.withdrawals),"Ожидается в кассе" to money(s.expected),"Фактически в кассе" to money(s.shift.countedCash ?: 0),"Расхождение" to money(s.shift.difference ?: 0)))
    internal fun receiptDate(at: Long)=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,java.util.Locale.getDefault()).format(java.util.Date(at))
    fun date(at: Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
    fun page(d: PrintDocument,p: PrinterConfiguration,choosePhrase: (List<String>)->String={it.random()}): ReceiptPage {
        val narrow=p.paperWidth=="58";val small=if(narrow)18f else 20f;val regular=if(narrow)21f else 23f;val medium=if(narrow)22f else 24f;val bold=if(narrow)29f else 32f;val title=if(narrow)30f else 33f
        val rows=mutableListOf<PrintRow>()
        fun text(s: String,size: Float=regular,weight: Int=400,center: Boolean=false,gap: Float=4f) {rows+=PrintRow(s,size=size,weight=weight,centered=center,gap=gap)}
        fun pair(a: String,b: String,size: Float=regular,weight: Int=400,gap: Float=4f) {rows+=PrintRow(a,b,size,weight,gap=gap)}
        fun sep(gap: Float=8f) {rows+=PrintRow(separator=true,size=small,gap=gap)}
        fun modifiers(item: PrintItem,kitchen: Boolean) {item.options.forEach {o->
            val qty=o.quantity.toBigDecimal();val delta=o.priceDelta.toBigDecimal()
            text("↳ "+(if(qty>BigDecimal.ONE) "${quantity(o.quantity)} × " else "")+o.name+(if(!kitchen && delta.signum()!=0) " (${if(delta.signum()>0) "+" else ""}${delta.setScale(2).toPlainString()} BYN)" else ""),if(kitchen) if(narrow)28f else 30f else small,gap=3f)
        }}
        when(d.kind) {
            PrintKind.TEST->{text("M POS",title,700,true);text("ПРОБНАЯ ПЕЧАТЬ",medium,700,true);text(p.name);text("${p.ip}:9100");text("Бумага: ${p.paperWidth} мм");text("Кириллица: АБВГД абвгд 0123456789");sep();text("Тест завершён",center=true)}
            PrintKind.SHIFT->{
                if(d.label.isNotBlank())text(d.label,title,700,true,5f)
                text("ОТЧЁТ О ЗАКРЫТИИ СМЕНЫ",bold,700,true,6f)
                text("Сотрудник: ${d.employee}",small,center=true,gap=3f)
                val values=d.shiftRows.toMap()
                values["Открыта"]?.let {text("Открыта: "+it,small,center=true,gap=2f)}
                values["Закрыта"]?.let {text("Закрыта: "+it,small,center=true,gap=8f)}
                sep()
                listOf("Заказов","Выручка","Наличные","Карта").forEach {name->
                    values[name]?.let {pair(name,it,if(name=="Выручка")medium else regular,if(name=="Выручка")600 else 400,if(name=="Карта")8f else 3f)}
                }
                sep()
                listOf("Наличные на начало","Внесения","Изъятия","Ожидается в кассе","Фактически в кассе","Расхождение").forEach {name->
                    values[name]?.let {pair(name,it,if(name=="Расхождение")bold else if(name in listOf("Ожидается в кассе","Фактически в кассе"))medium else regular,
                        if(name=="Расхождение")700 else if(name in listOf("Ожидается в кассе","Фактически в кассе"))600 else 400,if(name=="Расхождение")8f else 3f)}
                }
                sep();text("Смена закрыта",medium,600,true,8f)
            }
            PrintKind.KITCHEN->{
                text(d.label.ifBlank {d.number.ifBlank {"Заказ"}},if(narrow)40f else 44f,700,true,3f)
                text(receiptDate(d.at),if(narrow)24f else 26f,center=true,gap=8f);sep(7f);text(d.type,if(narrow)30f else 32f,600,true,8f);sep()
                val categories=p.categories.lines().map {it.trim()}.filter {it.isNotEmpty()}
                d.items.filter {categories.isEmpty() || it.category in categories}.forEach {item->text("${quantity(item.quantity)} × ${item.name}",if(narrow)38f else 42f,700);modifiers(item,true);if(item.comment.isNotBlank()) text("↳ ${item.comment}",if(narrow)28f else 30f,gap=9f)}
            }
            else->{
                if(p.receiptTitle.isNotBlank()) text(p.receiptTitle,title,700,true,14f)
                text("Сотрудник: ${d.employee}",small,gap=2f);if(p.registerLabel.isNotBlank()) text("Касса: ${p.registerLabel}",small,gap=11f)
                if(d.customer.isNotBlank()) text("Клиент: ${d.customer}",gap=2f);if(d.phone.isNotBlank()) text(d.phone,gap=10f)
                sep();text(d.type,gap=9f);sep(10f)
                d.items.forEach {item->
                    pair(item.name,money(item.net),medium,600,0f);text("${quantity(item.quantity)} × ${item.price.toBigDecimal().setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()} BYN",gap=1f)
                    modifiers(item,false);if(item.discount>0) text("${item.discountName}: −${money(item.discount)}",small,gap=1f)
                    if(p.paymentComments && item.comment.isNotBlank()) text("Комментарий: ${item.comment}",small,gap=1f)
                    text("",small,gap=0f)
                }
                if(d.discount>0 || d.loyaltyDiscounts.isNotEmpty()) {
                    sep(7f);if(d.gross>0)pair("Сумма без скидок",money(d.gross),gap=3f)
                    if(d.discount>0)pair("Скидки на товары","−"+money(d.discount),gap=3f)
                    d.loyaltyDiscounts.filter {it.second>0}.forEach {(name,amount)->pair("Лояльность · "+name,"−"+money(amount),gap=3f)}
                }
                if(d.delivery>0) pair("Доставка",money(d.delivery),gap=10f);sep(10f);pair("Итого",money(d.total),medium,600,12f)
                if(d.kind==PrintKind.BILL) text("СЧЁТ · НЕ ОПЛАЧЕН",medium,600,true,10f)
                else {
                    if(d.payments.size>1) text("Платежи",small)
                    d.payments.forEachIndexed {i,payment->
                        val method=if(payment.method=="cash") "Наличные" else "Карта"
                        pair(if(d.payments.size>1) "Платёж ${i+1} · $method" else method,money(payment.amount),gap=3f)
                        if(payment.method=="cash" && payment.given!=null && payment.given>0) {
                            val split=d.payments.size>1
                            pair(if(split)"  Внесено" else "Внесено",money(payment.given),if(split)small else regular,gap=if(split)2f else 3f)
                            pair(if(split)"  Сдача" else "Сдача",money(payment.change ?: 0),if(split)small else medium,if(split)400 else 600,gap=if(split)4f else 10f)
                        }
                    }
                }
                if(d.kind==PrintKind.RECEIPT) d.loyaltyRows.forEach {(name,value)->
                    sep(7f);text("Акция · "+name,medium,600,gap=5f)
                    value.split("\n").forEach {text(it,medium,gap=5f)}
                }
                sep(9f);pair(receiptDate(d.at),d.number.ifBlank {d.label.ifBlank {"#—"}},small,gap=8f)
                val phrases=p.phrases.lines().map {it.trim()}.filter {it.isNotEmpty()};if(phrases.isNotEmpty()) {text("",small,center=true,gap=5f);text(choosePhrase(phrases),center=true,gap=10f)}
            }
        }
        return ReceiptPage(if(p.printWidth=="auto") if(narrow)384 else 576 else p.printWidth.toInt(),if(narrow)14f else 24f,rows,if(d.kind==PrintKind.KITCHEN)4f else 0f,if(d.kind==PrintKind.KITCHEN)10f else 4f)
    }
}
