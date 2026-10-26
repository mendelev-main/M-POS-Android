package com.mendelev.mpos.domain.sale

import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.math.BigDecimal
import java.math.RoundingMode

/** Agreed BYN policy. Every displayed/payable net line is rounded once, HALF_UP to minor units. */
data class SaleDiscount(val type: String,val value: String,val name: String="Скидка")
data class SaleLineInput(val id: String,val unitPrice: String,val quantity: String,val discount: SaleDiscount?=null)
data class SaleLineMoney(val id: String,val gross: Long,val discount: Long,val net: Long)
data class DeliveryCharge(val id: String,val name: String,val fee: Long,val revision: Long)
data class SaleQuote(val lines: List<SaleLineMoney>,val gross: Long,val subtotal: Long,val discount: Long,val delivery: DeliveryCharge?,val total: Long)
data class PaymentInput(val method: String,val amount: Long,val given: Long?=null)
data class PaymentAllocation(val method: String,val amount: Long,val given: Long?,val change: Long?)
data class SalePayment(val parts: List<PaymentAllocation>,val cash: Long,val card: Long,val deliveryCashOut: Long,val expectedCashAfter: Long)
object SaleMoney {
    private fun invalid(message: String): Nothing=throw CatalogFailure("payment",message)
    private fun decimal(raw: String,scale: Int,positive: Boolean=false): BigDecimal {
        val s=raw.trim().replace(',','.')
        if(s.length>30 || !s.matches(Regex("[0-9]+(?:\\.[0-9]{1,$scale})?"))) invalid("Некорректная сумма или количество")
        return BigDecimal(s).also {if(positive && it.signum()<=0) invalid("Количество должно быть больше нуля")}
    }
    fun minor(value: BigDecimal): Long {
        if(value.signum()<0) invalid("Сумма не может быть отрицательной")
        return try {value.setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact()}
        catch(_:ArithmeticException) {invalid("Сумма слишком велика")}
    }
    private fun add(a: Long,b: Long)=try {Math.addExact(a,b)} catch(_:ArithmeticException) {invalid("Сумма слишком велика")}
    fun quote(lines: List<SaleLineInput>,type: String="here",delivery: DeliveryCharge?=null): SaleQuote {
        if(lines.isEmpty() || lines.size>500 || lines.map {it.id}.distinct().size!=lines.size || lines.any {it.id.isBlank()}) invalid("Проверьте строки заказа")
        if(type !in listOf("here","takeaway","delivery")) invalid("Неизвестный тип заказа")
        if(type=="delivery" && delivery==null) invalid("Выберите тариф доставки")
        if(type!="delivery" && delivery!=null) invalid("Тариф доступен только для доставки")
        if(delivery!=null && (delivery.id.isBlank() || delivery.name.isBlank() || delivery.name.length>100 || delivery.fee<0 || delivery.revision<=0)) invalid("Некорректный тариф доставки")
        val calculated=lines.map {line->
            val price=decimal(line.unitPrice,2);val qty=decimal(line.quantity,9,true);val gross=price*qty
            val deduction=line.discount?.let {d->
                if(d.name.isBlank() || d.name.length>100) invalid("Укажите название скидки")
                val value=decimal(d.value,2)
                when(d.type) {
                    "percent"->{if(value>BigDecimal(100)) invalid("Скидка не может превышать 100%");(gross*value).movePointLeft(2)}
                    "fixed"->(value*qty).min(gross)
                    else->invalid("Неизвестный тип скидки")
                }
            } ?: BigDecimal.ZERO
            val grossMinor=minor(gross);val net=minor(gross-deduction)
            SaleLineMoney(line.id,grossMinor,grossMinor-net,net)
        }
        val gross=calculated.fold(0L) {sum,line->add(sum,line.gross)}
        val subtotal=calculated.fold(0L) {sum,line->add(sum,line.net)}
        val discount=calculated.fold(0L) {sum,line->add(sum,line.discount)}
        return SaleQuote(calculated,gross,subtotal,discount,delivery,add(subtotal,delivery?.fee ?: 0))
    }
    fun equalParts(total: Long,count: Int): List<Long> {
        if(total<0 || count !in 2..10) invalid("Разделите чек на 2–10 частей")
        val base=total/count;val remainder=total%count
        return List(count) {base+if(it<remainder) 1 else 0}
    }
    fun payments(quote: SaleQuote,parts: List<PaymentInput>,openingCash: Long): SalePayment {
        if(openingCash<0 || parts.isEmpty() || parts.size>10) invalid("Проверьте оплату и наличные в смене")
        val allocations=parts.map {p->
            if(p.amount<0) invalid("Сумма части не может быть отрицательной")
            when(p.method) {
                "cash"->{val given=p.given ?: invalid("Введите принятую сумму");if(given<p.amount) invalid("Недостаточно внесённых наличных");PaymentAllocation("cash",p.amount,given,given-p.amount)}
                "card"->{if(p.given!=null) invalid("У карты нет наличной суммы");PaymentAllocation("card",p.amount,null,null)}
                else->invalid("Неизвестный способ оплаты")
            }
        }
        if(allocations.fold(0L) {sum,p->add(sum,p.amount)}!=quote.total) invalid("Сумма частей не совпадает с чеком")
        val cash=allocations.filter {it.method=="cash"}.fold(0L) {sum,p->add(sum,p.amount)}
        val card=allocations.filter {it.method=="card"}.fold(0L) {sum,p->add(sum,p.amount)}
        val available=add(openingCash,cash);val delivery=quote.delivery?.fee ?: 0
        if(delivery>available) invalid("Недостаточно наличных в кассе для выдачи доставки")
        return SalePayment(allocations,cash,card,delivery,available-delivery)
    }
}
