package com.mendelev.mpos.telegram

import com.mendelev.mpos.domain.warehouse.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class TtnRow(val name: String,val packing: String,val quantity: String,val price: String,val amount: String)
internal data class ReceivingTtn(val number: String,val invoiceDate: String,val supplier: String,val accepted: String,val employee: String,val rows: List<TtnRow>,val total: String,val note: String) {
    val caption get()="Поставщик: ${TelegramMessages.escape(supplier)}\nДата: ${TelegramMessages.escape(invoiceDate)}\nПринял: ${TelegramMessages.escape(employee)}"
    companion object {
        fun unit(raw: String)=when(raw) {"piece","pcs","шт"->"шт.";"kg"->"кг";"g"->"г";"l","liter"->"л";"ml"->"мл";"pack"->"уп.";"box"->"кор.";"bottle"->"бут.";else->raw}
        fun money(minor: Long)=BigDecimal.valueOf(minor,2).setScale(2).toPlainString().replace('.',',')
        private fun quantity(raw: String)=if(raw.isBlank()) "—" else raw.toBigDecimal().stripTrailingZeros().toPlainString().replace('.',',')
        fun from(r: PostedReceiving,employee: String,zone: ZoneId): ReceivingTtn {
            val date=runCatching {LocalDate.parse(r.draft.invoiceDate).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.getOrDefault(r.draft.invoiceDate)
            val rows=r.result.lines.map {result->
                val source=r.draft.lines.single {it.product.id==result.productId}
                val packing=if(source.unit in PurchaseDraftRules.packs) "${quantity(source.quantity)} ${unit(source.unit)} × ${quantity(source.packSize)} ${unit(source.contentUnit)}" else "${quantity(source.quantity)} ${unit(source.unit)}"
                val price=if(result.actual.toBigDecimal().signum()==0) "—" else BigDecimal.valueOf(result.totalMinor,2).divide(result.actual.toBigDecimal(),2,RoundingMode.HALF_UP).toPlainString().replace('.',',')
                TtnRow(result.name,packing,quantity(result.actual)+" "+unit(source.product.unit),price+" / "+unit(source.product.unit),money(result.totalMinor))
            }
            return ReceivingTtn(r.draft.invoiceNumber,date,r.draft.supplierName,
                DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm").withZone(zone).format(Instant.ofEpochMilli(r.at)),
                OwnerReportCaption.employeeName(employee),rows,money(r.result.totalMinor),r.draft.order?.source?.note.orEmpty())
        }
    }
}

/** Height-based pagination: every measured row appears once, with room reserved for the final total. */
internal object TtnPages {
    fun paginate(heights: List<Int>,capacity: Int): List<IntRange> {
        require(capacity>0 && heights.all {it in 1..capacity}) {"Строка ТТН превышает размер страницы"}
        if(heights.isEmpty()) return listOf(IntRange.EMPTY)
        val result=mutableListOf<IntRange>();var start=0;var used=0
        heights.forEachIndexed {i,h->if(used+h>capacity) {result+=start until i;start=i;used=0};used+=h}
        result+=start until heights.size;return result
    }
    fun batches(pages: Int): List<IntRange> {require(pages>0);return (0 until pages step 10).map {it until minOf(it+10,pages)}}
}
