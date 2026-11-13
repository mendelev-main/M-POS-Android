package com.mendelev.mpos.telegram

import com.mendelev.mpos.domain.report.ReportDetailLine
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

data class OwnerSale(val id: String,val shiftId: String,val at: Long,val total: Long,val delivery: Long,val lines: List<ReportDetailLine>,val courier: Long=delivery)
data class OwnerRefund(val id: String,val receiptId: String,val shiftId: String,val at: Long,val total: Long,val delivery: Long,val courier: Long=delivery)
data class OwnerRank(val key: String,val name: String,val sales: BigInteger,val refunds: BigInteger,val sold: Map<String,BigDecimal>,val returned: Map<String,BigDecimal>)
data class OwnerHour(val hour: Int,val sales: BigInteger)
data class OwnerAnalytics(val sales: BigInteger,val refunds: BigInteger,val profit: BigInteger,val delivery: BigInteger,val checks: Int,val refundChecks: Int,
    val sold: Map<String,BigDecimal>,val returned: Map<String,BigDecimal>,val products: List<OwnerRank>,val categories: List<OwnerRank>,val hours: List<OwnerHour>) {
    val net get()=sales-refunds
    val average get()=if(checks==0) BigInteger.ZERO else BigDecimal(sales).divide(checks.toBigDecimal(),0,RoundingMode.HALF_UP).toBigIntegerExact()
}
data class OwnerShiftInfo(val number: Int,val openedAt: Long,val closedAt: Long,val employee: String,val zoneId: String)
data class OwnerAnalyticsReport(val title: String,val period: String,val cutoff: Long,val current: OwnerAnalytics,val previous: OwnerAnalytics?,val comparison: String,val warnings: List<String> = emptyList(),val shift: OwnerShiftInfo?=null,val monthly: Boolean=false)

object OwnerReportCaption {
    fun format(report: OwnerAnalyticsReport): String {
        val shift=report.shift ?: return TelegramMessages.escape(report.period)
        val zone=ZoneId.of(shift.zoneId)
        fun date(at: Long,pattern: String)=java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(zone).format(Instant.ofEpochMilli(at))
        val month=if(report.monthly) "📅 Месяц: ${date(shift.closedAt,"MM.yyyy")}\n" else ""
        return month+"🧾 Смена: №${shift.number}\n🟢 Открыта: ${date(shift.openedAt,"dd.MM.yyyy HH:mm")}\n🔴 Закрыта: ${date(shift.closedAt,"dd.MM.yyyy HH:mm")}\n👤 Сотрудник: ${TelegramMessages.escape(employeeName(shift.employee))}"
    }
    /** Preserve the surname and already abbreviated initials, including imported names. */
    internal fun employeeName(value: String): String {
        val parts=value.trim().split(Regex("\\s+")).filter {it.isNotBlank()}
        if(parts.size<2) return parts.firstOrNull().orEmpty()
        val initials=parts.drop(1).joinToString("") {part->
            if(Regex("(?:\\p{L}\\.)+").matches(part)) part else part.take(1).uppercase(java.util.Locale.ROOT)+"."
        }
        return parts.first()+" "+initials

    }
}

enum class OwnerStatsKind { SHIFT, MONTH, QUARTER }
object OwnerStatsRouting {
    fun kinds(s: TelegramSettings,closedAt: Long,zone: ZoneId): List<OwnerStatsKind> {
        return OwnerReportSchedule.closingKinds(s,closedAt,zone)
    }
    fun recipient(s: TelegramSettings)=s.copy(chatId=s.ownerChatId,threadId="")
    fun key(kind: OwnerStatsKind,shiftId: String,closedAt: Long,zone: ZoneId,page: Int): String {
        val month=java.time.YearMonth.from(Instant.ofEpochMilli(closedAt).atZone(zone))
        return "owner:${kind.name}:$month:close:$shiftId:page:$page"
    }
}

object OwnerAnalyticsRules {
    fun lastDay(at: Long,zone: ZoneId)=Instant.ofEpochMilli(at).atZone(zone).toLocalDate().let {it.dayOfMonth==it.lengthOfMonth()}
    fun build(sales: List<OwnerSale>,refunds: List<OwnerRefund>,originals: Map<String,OwnerSale>,zone: ZoneId): OwnerAnalytics {
        require(sales.map {it.id}.distinct().size==sales.size && refunds.map {it.id}.distinct().size==refunds.size) {"Повтор события аналитики"}
        class MutableRank(val key: String,val name: String) {
            var soldMoney=BigInteger.ZERO;var refundMoney=BigInteger.ZERO
            val sold=sortedMapOf<String,BigDecimal>();val returned=sortedMapOf<String,BigDecimal>()
            fun add(l: ReportDetailLine,refund: Boolean) {
                if(refund) refundMoney+=l.netMinor.toBigInteger() else soldMoney+=l.netMinor.toBigInteger()
                val target=if(refund) returned else sold;target[l.unit]=(target[l.unit] ?: BigDecimal.ZERO)+l.quantity
            }
            fun result()=OwnerRank(key,name,soldMoney,refundMoney,sold.toMap(),returned.toMap())
        }
        val products=linkedMapOf<String,MutableRank>();val categories=linkedMapOf<String,MutableRank>()
        val sold=sortedMapOf<String,BigDecimal>();val returned=sortedMapOf<String,BigDecimal>()
        var saleMoney=BigInteger.ZERO;var refundMoney=BigInteger.ZERO;var cost=BigInteger.ZERO;var delivery=BigInteger.ZERO;var courier=BigInteger.ZERO
        val hours=sortedMapOf<Int,BigInteger>()
        fun detail(s: OwnerSale,refund: Boolean) {
            require(s.total>=0 && s.delivery in 0..s.total && (s.courier==0L || s.courier==s.delivery) && s.lines.isNotEmpty()) {"Повреждён чек аналитики"}
            require(s.lines.fold(BigInteger.ZERO) {sum,l->sum+l.netMinor.toBigInteger()}+s.delivery.toBigInteger()==s.total.toBigInteger()) {"Суммы строк не совпадают с чеком"}
            s.lines.forEach {l->
                require(l.quantity.signum()>0 && l.netMinor>=0 && l.costMinor.signum()>=0 && l.name.isNotBlank() && l.unit.isNotBlank())
                val target=if(refund) returned else sold;target[l.unit]=(target[l.unit] ?: BigDecimal.ZERO)+l.quantity
                val productKey=listOf(l.productId,l.name,l.categoryId,l.categoryName,l.unit).joinToString("\u0000")
                val categoryKey=listOf(l.categoryId,l.categoryName).joinToString("\u0000")
                products.getOrPut(productKey) {MutableRank(productKey,l.name)}.add(l,refund)
                categories.getOrPut(categoryKey) {MutableRank(categoryKey,l.categoryName.ifBlank {"Без категории"})}.add(l,refund)
                cost+=if(refund) -l.costMinor else l.costMinor
            }
        }
        sales.forEach {s->
            detail(s,false);saleMoney+=s.total.toBigInteger();delivery+=s.delivery.toBigInteger();courier+=s.courier.toBigInteger()
            val hour=Instant.ofEpochMilli(s.at).atZone(zone).hour;hours[hour]=(hours[hour] ?: BigInteger.ZERO)+s.total.toBigInteger()
        }
        refunds.forEach {r->
            val s=originals[r.receiptId] ?: error("Не найден исходный чек возврата")
            require(r.at>=s.at && r.total==s.total && r.delivery==s.delivery && r.courier==s.courier) {"Возврат не совпадает с исходной продажей"}
            detail(s,true);refundMoney+=r.total.toBigInteger();delivery-=r.delivery.toBigInteger();courier-=r.courier.toBigInteger()
        }
        fun ranks(map: Map<String,MutableRank>)=map.values.map {it.result()}.sortedWith(compareByDescending<OwnerRank> {it.sales}.thenBy {it.name}.thenBy {it.key})
        val hourRows=if(hours.isEmpty()) emptyList() else (hours.keys.first()..hours.keys.last()).map {OwnerHour(it,hours[it] ?: BigInteger.ZERO)}
        return OwnerAnalytics(saleMoney,refundMoney,saleMoney-refundMoney-cost-courier,delivery,sales.size,refunds.size,sold,returned,ranks(products),ranks(categories),hourRows)
    }
    /** Largest remainder keeps visible percentage labels at exactly100 without floating point. */
    fun percentages(values: List<BigInteger>): List<Int> = shares(values,100)
    fun percentageTenths(values: List<BigInteger>): List<Int> = shares(values,1000)
    private fun shares(values: List<BigInteger>,units: Int): List<Int> {
        require(values.all {it.signum()>=0});val total=values.fold(BigInteger.ZERO,BigInteger::add)
        if(total.signum()==0) return values.map {0}
        val parts=values.map {it*units.toBigInteger()}.map {it.divideAndRemainder(total)}
        val result=parts.map {it[0].toInt()}.toMutableList()
        parts.indices.sortedWith(compareByDescending<Int> {parts[it][1]}.thenBy {it}).take(units-result.sum()).forEach {result[it]++}
        return result
    }
}
