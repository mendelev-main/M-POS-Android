package com.mendelev.mpos.telegram

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.domain.catalog.unitLabel
import com.mendelev.mpos.domain.shift.ShiftSummary
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

object TelegramReports {
    private fun amount(v: Long)=BigDecimal.valueOf(v,2).toPlainString()
    private fun amount(v: BigInteger)=BigDecimal(v,2).toPlainString()
    fun shift(s: ShiftSummary): JSONObject=JSONObject().apply {
        put("id",s.number.toString());put("employeeName",s.shift.actorName);put("openedAt",s.shift.openedAt);put("closedAt",s.shift.closedAt)
        put("openingCash",amount(s.shift.openingCash));put("countedCash",amount(s.shift.countedCash ?: 0));put("expectedCash",amount(s.expected));put("difference",amount(s.shift.difference ?: 0))
        put("cash",amount(s.cashNet));put("card",amount(s.cardNet));put("total",amount(s.revenue));put("deposits",amount(s.deposits));put("withdrawals",amount(s.withdrawals))
        put("currency","BYN");put("establishmentName","M-POS");put("orders",JSONArray(List(s.count) {JSONObject()}))
        put("cashMovements",JSONArray(s.movements.map {m->JSONObject().put("type",if(m.delta>=0) "deposit" else "withdrawal").put("amount",BigDecimal.valueOf(m.delta,2).abs().toPlainString()).put("timestamp",m.at).put("note",listOf(m.title,m.note).filter {it.isNotBlank()}.joinToString(" · "))}))
    }
    internal suspend fun warehouse(db: CatalogDatabase,month: YearMonth): JSONObject=db.adminTransaction {
        val period=ReportPeriod(month.atDay(1),month.atEndOfMonth(),ZoneId.systemDefault())
        val report=db.warehouseReport(period)
        val invoices=PostedReceivingStore(db).all().filter {period.contains(it.at)}
        fun supplier(r: com.mendelev.mpos.domain.warehouse.PostedReceiving)=r.draft.supplier?.name ?: r.draft.order?.source?.supplierName ?: "Без поставщика"
        fun quantity(v: BigDecimal?)=v?.stripTrailingZeros()?.toPlainString() ?: "—"
        fun money(v: BigInteger?)=v?.let {amount(it)+" BYN"} ?: "—"
        val suppliers=invoices.groupBy(::supplier)
        fun section(title: String,headers: List<String>,rows: List<List<String>>)=JSONObject().put("title",title).put("headers",JSONArray(headers)).put("rows",JSONArray(rows.map {JSONArray(it)}))
        val simple=report.rows.map {r->listOf(invoices.filter {it.result.lines.any {l->l.productId==r.product.id}}.map(::supplier).distinct().sorted().joinToString(", ").ifBlank {"—"},r.product.name,unitLabel(r.product.unit),quantity(r.incoming),quantity(r.outgoing),quantity(r.end))}
        val detailed=report.rows.map {r->listOf(r.product.name+" / "+unitLabel(r.product.unit),quantity(r.start),quantity(r.incoming),quantity(r.outgoing),quantity(r.returned),quantity(r.end),quantity(r.product.current),money(r.invoiceValue),money(r.outgoingValue),money(r.endValue))}
        fun quantityTotal(value: (WarehouseRow)->BigDecimal?): String=report.rows.groupBy {it.product.unit}.map {(unit,rows)->
            val values=rows.map(value);(if(values.any {it==null}) "—" else quantity(values.filterNotNull().fold(BigDecimal.ZERO,BigDecimal::add)))+" "+unitLabel(unit)
        }.joinToString("; ").ifBlank {"—"}
        fun valueTotal(value: (WarehouseRow)->BigInteger?): BigInteger? {
            val values=report.rows.map(value);return if(values.any {it==null}) null else values.filterNotNull().fold(BigInteger.ZERO,BigInteger::add)
        }
        val sections=listOf(
            section("Упрощённый учёт",listOf("Поставщики за период","Товар","Ед.","Получено","Списано","Остаток на конец*"),simple),
            section("Сводка",listOf("Показатель","Количество","Сумма"),listOf(listOf("Поступления",quantityTotal {it.incoming},money(report.invoiceValue)),listOf("Списания при продаже · оценка",quantityTotal {it.outgoing},money(valueTotal {it.outgoingValue})),listOf("Возвраты на склад · оценка",quantityTotal {it.returned},money(valueTotal {it.returnedValue})),listOf("Текущий остаток",quantityTotal {it.product.current},money(report.currentValue)),listOf("Остаток на конец · расчёт",quantityTotal {it.end},money(report.endValue)))),
            section("Движения и остатки",listOf("Товар / ед.","Начало*","Приход","Расход","Возврат","Конец*","Сейчас","Приход, BYN","Расход*, BYN","Конец*, BYN"),detailed),
            section("Поставщики",listOf("Поставщик","Приёмок","Сумма","Расхождений"),suppliers.map {(name,rows)->listOf(name,rows.size.toString(),money(rows.fold(BigInteger.ZERO) {sum,r->sum+r.result.totalMinor.toBigInteger()}),rows.count {r->r.result.lines.any {it.shortage?.toBigDecimal()?.signum()==1}}.toString())}),
            section("Реестр полученных документов",listOf("Поставщик","Номер","Дата документа","Дата приёмки","Сумма","Статус"),invoices.map {r->listOf(supplier(r),r.draft.invoiceNumber,r.draft.invoiceDate,com.mendelev.mpos.print.PrintDocuments.date(r.at),money(r.result.totalMinor.toBigInteger()),if(r.result.lines.any {it.shortage?.toBigDecimal()?.signum()==1}) "Расхождение" else "Принят")})
        )
        JSONObject().put("title","Складской учёт").put("period","${period.from} — ${period.through}").put("generatedAt",com.mendelev.mpos.print.PrintDocuments.date(System.currentTimeMillis())).put("company","M-POS").put("sections",JSONArray(sections))
            .put("notes",JSONArray(listOf("Остатки прошлого периода оценены по текущей себестоимости. HALF_UP по каждой денежной строке. НДС отдельно не рассчитывается.","Прочерк означает, что остаток неизвестен или учёт отключён.")+report.rows.flatMap {r->r.warnings.map {"${r.product.name}: $it"}}))
    }
}
