package com.mendelev.mpos.domain.shift

import java.math.BigInteger

data class ShiftSaleFact(val id: String,val shiftId: String,val at: Long,val total: Long,val cash: Long,val card: Long,val courier: Long,val discount: Long)
data class ShiftRefundFact(val id: String,val shiftId: String,val at: Long,val total: Long,val cash: Long,val card: Long,val courierCredit: Long)
data class ShiftMovementRow(val id: String,val at: Long,val title: String,val note: String,val delta: Long)
data class ShiftSummary(val shift: Shift,val number: Int,val count: Int,val sales: BigInteger,val refunds: BigInteger,val discounts: BigInteger,
    val saleCash: BigInteger,val saleCard: BigInteger,val refundCash: BigInteger,val refundCard: BigInteger,
    val deposits: BigInteger,val withdrawals: BigInteger,val expected: BigInteger,val baseline: BigInteger,val imported: Boolean,
    val movements: List<ShiftMovementRow>) {
    val revenue get()=sales-refunds
    val cashNet get()=saleCash-refundCash
    val cardNet get()=saleCard-refundCard
}
data class ShiftHistoryCursor(val closedAt: Long,val id: String)
data class ShiftSequence(val id: String,val openedAt: Long,val sourceShiftId: String?)
data class ShiftDashboard(val snapshot: ShiftSnapshot,val summaries: List<ShiftSummary>,val pendingCash: Long=0,val nextHistory: ShiftHistoryCursor?=null) {
    val active get()=summaries.find {it.shift.id==snapshot.active?.id}
    val history get()=summaries.filterNot {it.shift.isOpen}.sortedWith(compareByDescending<ShiftSummary> {it.shift.closedAt}.thenByDescending {it.shift.id})
}
object ShiftDashboardRules {
    fun numbers(shifts: List<ShiftSequence>): Map<String,Int> {
        val byId=shifts.associateBy {it.id};require(byId.size==shifts.size)
        val ordered=mutableListOf<ShiftSequence>();val visited=mutableSetOf<String>()
        shifts.sortedWith(compareBy<ShiftSequence> {it.openedAt}.thenBy {it.id}).forEach {start->
            val chain=mutableListOf<ShiftSequence>();val path=mutableSetOf<String>();var cursor: ShiftSequence?=start
            while(cursor!=null && cursor.id !in visited) {
                require(path.add(cursor.id)) {"Нарушен порядок смен"};chain+=cursor;cursor=cursor.sourceShiftId?.let(byId::get)
            }
            chain.asReversed().forEach {visited+=it.id;ordered+=it}
        }
        return ordered.mapIndexed {index,s->s.id to index+1}.toMap()
    }
    fun build(state: ShiftSnapshot,shifts: List<Shift>,sales: List<ShiftSaleFact>,refunds: List<ShiftRefundFact>,manual: List<ShiftCashMovement>,imported: Set<String> = emptySet(),pendingCash: Long=0): ShiftDashboard {
        require(shifts.map {it.id}.distinct().size==shifts.size && sales.map {it.id}.distinct().size==sales.size && refunds.map {it.id}.distinct().size==refunds.size)
        val byId=shifts.associateBy {it.id}
        val sequence=numbers(shifts.map {ShiftSequence(it.id,it.openedAt,it.sourceShiftId)})
        val ordered=shifts.sortedBy {sequence.getValue(it.id)}
        require(pendingCash>=0 && shifts.count {it.isOpen}<=1)
        require(sales.all {it.shiftId in byId && listOf(it.total,it.cash,it.card,it.courier,it.discount).all {v->v>=0} && it.cash.toBigInteger()+it.card.toBigInteger()==it.total.toBigInteger()})
        require(refunds.all {it.shiftId in byId && listOf(it.total,it.cash,it.card,it.courierCredit).all {v->v>=0} && it.cash.toBigInteger()+it.card.toBigInteger()==it.total.toBigInteger()})
        manual.forEach {ShiftCashRules.validate(it);require(it.command.shiftId in byId)}
        val saleGroups=sales.groupBy {it.shiftId};val refundGroups=refunds.groupBy {it.shiftId};val cashGroups=manual.groupBy {it.command.shiftId}
        val rows=ordered.mapIndexed {index,s->
            val ss=saleGroups[s.id].orEmpty();val rr=refundGroups[s.id].orEmpty();val mm=cashGroups[s.id].orEmpty()
            fun <T> sum(rows: List<T>,value: (T)->Long)=rows.fold(BigInteger.ZERO) {a,r->a+value(r).toBigInteger()}
            val sc=sum(ss) {it.cash};val sk=sum(ss) {it.card};val rc=sum(rr) {it.cash};val rk=sum(rr) {it.card}
            val deposits=sum(mm.filter {it.command.type=="deposit"}) {it.command.amount}+sum(rr) {it.courierCredit}
            val withdrawals=sum(mm.filter {it.command.type=="withdrawal"}) {it.command.amount}+sum(ss) {it.courier}
            val calculated=s.openingCash.toBigInteger()+sc-rc+deposits-withdrawals
            val expected=if(s.isOpen) {require(state.active?.id==s.id);state.currentCash.toBigInteger()} else s.expectedCash!!.toBigInteger()
            require(expected.signum()>=0 && (expected==calculated || s.id in imported)) {"Итоги смены не совпадают с кассовым журналом"}
            val movements=mm.map {ShiftMovementRow(it.id,it.at,if(it.command.type=="deposit") "Внесение наличных" else "Изъятие наличных",it.command.note,if(it.command.type=="deposit") it.command.amount else -it.command.amount)}+
                ss.filter {it.courier>0}.map {ShiftMovementRow("delivery:${it.id}",it.at,"Доставка","Оплата курьеру",-it.courier)}+
                rr.flatMap {r->listOfNotNull(if(r.cash>0) ShiftMovementRow("refund:${r.id}",r.at,"Возврат чека","",-r.cash) else null,if(r.courierCredit>0) ShiftMovementRow("courier-credit:${r.id}",r.at,"Возврат оплаты курьеру","",r.courierCredit) else null)}
            ShiftSummary(s,index+1,ss.size,sum(ss) {it.total},sum(rr) {it.total},sum(ss) {it.discount},sc,sk,rc,rk,deposits,withdrawals,expected,expected-calculated,s.id in imported,movements.sortedWith(compareByDescending<ShiftMovementRow> {it.at}.thenBy {it.id}))
        }
        return ShiftDashboard(state,rows,pendingCash)
    }
}
