package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

data class OrderDetails(val label: String = "", val type: String = "here", val comment: String = "", val address: String = "", val phone: String = "")
data class ParkedOrder(val id: String, val revision: Long, val cart: CurrentCart, val parkedAt: Long)

object OrderRules {
    private fun text(raw: String, maximum: Int, field: String, multiline: Boolean = false): String = raw.trim().also {
        if(it.codePointCount(0,it.length)>maximum || it.any { c -> Character.isISOControl(c) && !(multiline && (c=='\n' || c=='\t')) })
            throw CatalogFailure("orderDetails","$field: до $maximum символов, без недопустимых управляющих символов")
    }
    fun details(raw: OrderDetails): OrderDetails {
        if(raw.type !in listOf("here","takeaway","delivery")) throw CatalogFailure("orderDetails","Выберите тип заказа")
        return raw.copy(label=text(raw.label,80,"Подпись"),comment=text(raw.comment,1000,"Комментарий",true),
            address=if(raw.type=="delivery") text(raw.address,500,"Адрес") else "",
            phone=if(raw.type=="delivery") text(raw.phone,40,"Телефон") else "")
    }
    fun labelKey(value: String)=Normalizer.normalize(value.trim(),Normalizer.Form.NFC).lowercase(Locale.ROOT).replace(Regex("[\\p{Z}\\s]+")," ")
    fun uniqueLabel(raw: String, used: List<String>): String {
        val label=text(raw,80,"Подпись")
        if(label.isEmpty()) throw CatalogFailure("orderLabel","Введите подпись заказа")
        val keys=used.map(::labelKey).toSet()
        if(labelKey(label) !in keys) return label
        val base=Regex("^(.*) \\(([2-9]|[1-9]\\d+)\\)$").matchEntire(label)?.groupValues?.get(1) ?: label
        var number=2
        while(true) {
            val suffix=" ($number)";val length=80-suffix.length
            val clipped=base.substring(0,base.offsetByCodePoints(0,minOf(length,base.codePointCount(0,base.length)))).trimEnd()+suffix
            if(labelKey(clipped) !in keys) return clipped
            number++
        }
    }
    fun compareLabels(left: String,right: String): Int {
        val a=Regex("\\d+|\\D+").findAll(labelKey(left)).map {it.value}.toList()
        val b=Regex("\\d+|\\D+").findAll(labelKey(right)).map {it.value}.toList()
        for(i in 0 until minOf(a.size,b.size)) {
            val comparison=if(a[i].all(Char::isDigit) && b[i].all(Char::isDigit)) a[i].toBigInteger().compareTo(b[i].toBigInteger()) else a[i].compareTo(b[i])
            if(comparison!=0) return comparison
        }
        return a.size.compareTo(b.size)
    }
    fun typeLabel(type: String)=when(type) {"delivery" -> "Доставка";"takeaway" -> "С собой";else -> "На месте"}
}
object OrderReservations {
    fun total(carts: List<CurrentCart>): Map<String,BigDecimal> {
        val ids=carts.map {it.sessionId}
        if(ids.any {it.isBlank()} || ids.distinct().size!=ids.size) throw CatalogFailure("orderOwner","Заказ имеет двух владельцев")
        val totals=linkedMapOf<String,BigDecimal>()
        carts.forEach {cart -> CartEngine.reserved(cart).forEach { (id,qty) -> totals[id]=(totals[id] ?: BigDecimal.ZERO)+qty } }
        return totals
    }
}
