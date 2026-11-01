package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.shift.Shift
import java.math.BigInteger
import java.time.ZoneId

/** Financial events belong to the shift that actually admitted them, not to the original seller. */
data class ShiftReportEvent(val id: String,val shiftId: String,val at: Long,val kind: String,
    val cash: Long=0,val card: Long=0,val courier: Long=0)
data class ShiftReportRow(val shift: Shift,val sales: BigInteger,val refunds: BigInteger,
    val saleCash: BigInteger,val saleCard: BigInteger,val refundCash: BigInteger,val refundCard: BigInteger,
    val courierOut: BigInteger,val courierCredit: BigInteger,val deposits: BigInteger,val withdrawals: BigInteger,
    val calculatedCash: BigInteger,val savedCashMatches: Boolean?)
object ShiftReports {
    fun build(period: ReportPeriod,shifts: List<Shift>,events: List<ShiftReportEvent>): List<ShiftReportRow> {
        fun bad(): Nothing=throw CatalogFailure("report","Повреждена связь событий со сменой")
        if(shifts.map {it.id}.distinct().size!=shifts.size || events.map {it.id}.distinct().size!=events.size) bad()
        val byId=shifts.associateBy {it.id}
        if(shifts.count {it.isOpen}>1) bad()
        shifts.forEach {s->
            try {ZoneId.of(s.zoneId)} catch(_:Exception) {bad()}
            if(s.id.isBlank() || s.actorId.isBlank() || s.actorName.isBlank() || s.openedAt<0 || s.openingCash<0 ||
                s.closedAt?.let {it<s.openedAt}==true) bad()
            if(s.isOpen && listOf(s.expectedCash,s.countedCash,s.difference).any {it!=null}) bad()
            if(!s.isOpen && (s.expectedCash==null || s.countedCash==null || s.difference==null || s.expectedCash<0 || s.countedCash<0)) bad()
        }
        events.forEach {e->
            val s=byId[e.shiftId] ?: bad()
            if(e.id.isBlank() || e.at<s.openedAt || s.closedAt?.let {e.at>it}==true ||
                e.kind !in setOf("sale","refund","deposit","withdrawal") || listOf(e.cash,e.card,e.courier).any {it<0}) bad()
            if(e.kind in setOf("deposit","withdrawal") && (e.card!=0L || e.courier!=0L || e.cash==0L)) bad()
        }
        return shifts.filter {s->s.openedAt<period.endExclusive && (s.closedAt==null || s.closedAt>=period.start)}
            .sortedWith(compareByDescending<Shift> {it.openedAt}.thenBy {it.id}).map {s->
                val rows=events.filter {it.shiftId==s.id}
                fun sum(kind: String,field: (ShiftReportEvent)->Long)=rows.filter {it.kind==kind}.fold(BigInteger.ZERO) {a,e->a+field(e).toBigInteger()}
                val sc=sum("sale") {it.cash};val sk=sum("sale") {it.card};val rc=sum("refund") {it.cash};val rk=sum("refund") {it.card}
                val out=sum("sale") {it.courier};val credit=sum("refund") {it.courier}
                val dep=sum("deposit") {it.cash};val wd=sum("withdrawal") {it.cash}
                val cash=s.openingCash.toBigInteger()+sc-rc-out+credit+dep-wd
                if(cash.signum()<0) bad()
                val matches=if(s.isOpen) null else cash==s.expectedCash!!.toBigInteger() &&
                    s.countedCash!!.toBigInteger()-s.expectedCash.toBigInteger()==s.difference!!.toBigInteger()
                ShiftReportRow(s,sc+sk,rc+rk,sc,sk,rc,rk,out,credit,dep,wd,cash,matches)
            }
    }
}
