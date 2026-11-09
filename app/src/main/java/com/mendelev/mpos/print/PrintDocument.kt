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
data class PrintDocument(val key: String,val kind: PrintKind,val at: Long,val label: String="",val number: String="",val employee: String="Сотрудник",val type: String="На месте",val items: List<PrintItem> = emptyList(),val total: Long=0,val gross: Long=0,val discount: Long=0,val delivery: Long=0,val payments: List<PrintPayment> = emptyList(),val customer: String="",val phone: String="",val shiftRows: List<Pair<String,String>> = emptyList(),val session: String="")
data class PrintRow(val left: String="",val right: String?=null,val size: Float=23f,val weight: Int=400,val centered: Boolean=false,val separator: Boolean=false,val gap: Float=4f)
data class ReceiptPage(val width: Int,val margin: Float,val rows: List<PrintRow>)

object PrinterRouting {
    fun targets(kind: PrintKind,printers: List<PrinterConfiguration>,automatic: Boolean=false)=printers.filter {
        if(kind==PrintKind.KITCHEN) it.printOrders && (!automatic || it.autoKitchen)
        else it.printReceipts && (!automatic || it.autoReceipt || kind==PrintKind.SHIFT)
    }
    fun opensDrawer(document: PrintDocument)=document.kind==PrintKind.RECEIPT && document.payments.any {it.method=="cash" && it.amount>0}
}

object PrintDocuments {
    fun money(minor: Long)=BigDecimal.valueOf(minor,2).toPlainString()+" BYN"
    private fun money(minor: BigInteger)=BigDecimal(minor,2).toPlainString()+" BYN"
    fun receipt(value: Pair<ReceiptEntity,ReceiptSnapshot>,employee: String): PrintDocument {
        val (record,s)=value
        val categories=s.products.map {JSONObject(it)}.associate {it.getString("id") to it.optString("categoryName")}
        return cart(s.cart,s.quote,PrintKind.RECEIPT,employee,categories).let {doc->doc.copy(items=doc.items.mapIndexed {index,item->item.copy(discountName=s.checkout.discounts[s.cart.lines[index].id]?.name ?: item.discountName)})}.copy(key="receipt:${record.id}",at=record.createdAt,number="#${record.number}",payments=s.payment.parts.map {PrintPayment(it.method,it.amount,it.given,it.change)})
    }
    fun cart(cart: CurrentCart,quote: SaleQuote,kind: PrintKind,employee: String,categories: Map<String,String>): PrintDocument {
        return PrintDocument("cart:${cart.sessionId}",kind,cart.createdAt,cart.details.label,"",employee,OrderRules.typeLabel(cart.details.type),cart.lines.map {l->
            val amount=quote.lines.single {it.id==l.id}
            PrintItem(l.name,l.quantity,l.unitPrice,amount.net,amount.discount,l.discount?.name ?: "Скидка",categories[l.productId].orEmpty(),l.comment,l.options,l.id)
        },quote.total,quote.gross,quote.discount,quote.delivery?.fee ?: 0,customer=cart.details.customerName,phone=cart.details.phone,session=cart.sessionId)
    }
    fun shift(s: ShiftSummary)=PrintDocument("shift:${s.shift.id}",PrintKind.SHIFT,s.shift.closedAt ?: s.shift.openedAt,employee=s.shift.actorName,shiftRows=listOf(
        "Открыта" to date(s.shift.openedAt),"Закрыта" to date(s.shift.closedAt ?: s.shift.openedAt),"Заказов" to s.count.toString(),
        "Выручка" to money(s.revenue),"Наличные" to money(s.cashNet),"Карта" to money(s.cardNet),"Наличные на начало" to money(s.shift.openingCash),
        "Внесения" to money(s.deposits),"Изъятия" to money(s.withdrawals),"Ожидается в кассе" to money(s.expected),"Фактически в кассе" to money(s.shift.countedCash ?: 0),"Расхождение" to money(s.shift.difference ?: 0)))
    fun date(at: Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
    fun page(d: PrintDocument,p: PrinterConfiguration,choosePhrase: (List<String>)->String={it.random()}): ReceiptPage {
        val narrow=p.paperWidth=="58";val small=if(narrow)18f else 20f;val regular=if(narrow)21f else 23f;val medium=if(narrow)22f else 24f;val title=if(narrow)30f else 33f
        val rows=mutableListOf<PrintRow>()
        fun text(s: String,size: Float=regular,weight: Int=400,center: Boolean=false,gap: Float=4f) {rows+=PrintRow(s,size=size,weight=weight,centered=center,gap=gap)}
        fun pair(a: String,b: String,size: Float=regular,weight: Int=400,gap: Float=4f) {rows+=PrintRow(a,b,size,weight,gap=gap)}
        fun sep() {rows+=PrintRow(separator=true,gap=9f)}
        fun modifiers(item: PrintItem,kitchen: Boolean) {item.options.forEach {o->
            val qty=o.quantity.toBigDecimal();val delta=o.priceDelta.toBigDecimal()
            text("↳ "+(if(qty>BigDecimal.ONE) "${qty.stripTrailingZeros().toPlainString()} × " else "")+o.name+(if(!kitchen && delta.signum()!=0) " (${if(delta.signum()>0) "+" else ""}${delta.setScale(2).toPlainString()} BYN)" else ""),if(kitchen) if(narrow)28f else 30f else small)
        }}
        when(d.kind) {
            PrintKind.TEST->{text("M POS",title,700,true);text("ПРОБНАЯ ПЕЧАТЬ",medium,700,true);text(p.name);text("${p.ip}:9100");text("Бумага: ${p.paperWidth} мм");text("Кириллица: АБВГД абвгд 0123456789");sep();text("Тест завершён",center=true)}
            PrintKind.SHIFT->{text("ОТЧЁТ О ЗАКРЫТИИ СМЕНЫ",title,700,true);text("Сотрудник: ${d.employee}",small,center=true);sep();d.shiftRows.forEach {(a,b)->pair(a,b,if(a=="Расхождение") title else regular,if(a in listOf("Выручка","Расхождение"))700 else 400)};sep();text("Смена закрыта",medium,600,true)}
            PrintKind.KITCHEN->{
                text(d.label.ifBlank {d.number.ifBlank {"Заказ"}},if(narrow)40f else 44f,700,true)
                text(date(d.at),if(narrow)24f else 26f,center=true);sep();text(d.type,if(narrow)30f else 32f,600,true);sep()
                val categories=p.categories.lines().map {it.trim()}.filter {it.isNotEmpty()}
                d.items.filter {categories.isEmpty() || it.category in categories}.forEach {item->text("${item.quantity.toBigDecimal().stripTrailingZeros().toPlainString()} × ${item.name}",if(narrow)38f else 42f,700);modifiers(item,true);if(item.comment.isNotBlank()) text("↳ ${item.comment}",if(narrow)28f else 30f,gap=9f)}
            }
            else->{
                if(p.receiptTitle.isNotBlank()) text(p.receiptTitle,title,700,true,14f)
                text("Сотрудник: ${d.employee}",small);if(p.registerLabel.isNotBlank()) text("Касса: ${p.registerLabel}",small,gap=11f)
                if(d.customer.isNotBlank()) text("Клиент: ${d.customer}");if(d.phone.isNotBlank()) text(d.phone)
                sep();text(d.type);sep()
                d.items.forEach {item->
                    pair(item.name,money(item.net),medium,600,0f);text("${item.quantity.toBigDecimal().stripTrailingZeros().toPlainString()} × ${item.price} BYN",gap=1f)
                    modifiers(item,false);if(item.discount>0) text("${item.discountName}: −${money(item.discount)}",small)
                    if(p.paymentComments && item.comment.isNotBlank()) text("Комментарий: ${item.comment}",small)
                    text("",small,gap=0f)
                }
                if(d.discount>0) {sep();pair("Сумма без скидок",money(d.gross));pair("Скидки на товары","−"+money(d.discount))}
                if(d.delivery>0) pair("Доставка",money(d.delivery));sep();pair("Итого",money(d.total),medium,600,12f)
                if(d.kind==PrintKind.BILL) text("СЧЁТ · НЕ ОПЛАЧЕН",medium,600,true)
                else d.payments.forEachIndexed {i,payment->
                    val method=if(payment.method=="cash") "Наличные" else "Карта"
                    pair(if(d.payments.size>1) "Платёж ${i+1} · $method" else method,money(payment.amount))
                    if(payment.method=="cash" && payment.given!=null) {pair("Внесено",money(payment.given));pair("Сдача",money(payment.change ?: 0),medium,600)}
                }
                sep();pair(date(d.at),d.number.ifBlank {d.label.ifBlank {"#—"}},small)
                val phrases=p.phrases.lines().map {it.trim()}.filter {it.isNotEmpty()};if(phrases.isNotEmpty()) text(choosePhrase(phrases),center=true,gap=10f)
            }
        }
        return ReceiptPage(if(p.printWidth=="auto") if(narrow)384 else 576 else p.printWidth.toInt(),if(narrow)14f else 24f,rows)
    }
}
