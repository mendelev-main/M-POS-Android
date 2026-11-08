package com.mendelev.mpos.domain.shift

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class ShiftDashboardTest {
    private fun shift(id: String,opening: Long=0,previous: String?=null)=Shift(id,"r","anna","Анна",10,"UTC",opening,previous)
    @Test fun refundsBelongToPayingShiftAndCourierCreditIsNotDoubleCounted() {
        val first=shift("first",10000).copy(closedAt=20,countedCash=14500,expectedCash=14500,difference=0)
        val second=shift("second",14500,"first")
        val cash=listOf(ShiftCashMovement("deposit",ShiftCashCommand("c1","first",1,"anna","deposit",1000,"Размен"),11,13500,14500),ShiftCashMovement("withdraw",ShiftCashCommand("c2","second",2,"anna","withdrawal",500,""),21,11000,10500))
        val data=ShiftDashboardRules.build(ShiftSnapshot("r","owner",3,second,-4000),listOf(second,first),
            listOf(ShiftSaleFact("sale","first",12,10000,4000,6000,500,100)),listOf(ShiftRefundFact("refund","second",21,10000,4000,6000,500)),cash)
        val old=data.history.single();val current=data.active!!
        assertEquals(BigInteger("10000"),old.revenue);assertEquals(1,old.count)
        assertEquals(BigInteger("-10000"),current.revenue);assertEquals(0,current.count)
        assertEquals(BigInteger("10500"),current.expected);assertEquals(BigInteger.ZERO,current.baseline)
        assertEquals(BigInteger("500"),current.deposits);assertEquals(BigInteger("500"),current.withdrawals)
        assertEquals(listOf(-4000L,500L,-500L).sorted(),current.movements.map {it.delta}.sorted())
    }
    @Test fun importedCashIsLabelledAndPaidPartsAreNotSales() {
        val active=shift("imported",1000)
        val data=ShiftDashboardRules.build(ShiftSnapshot("r","owner",1,active,500),listOf(active),emptyList(),emptyList(),emptyList(),setOf("imported"),pendingCash=300)
        assertEquals(BigInteger("500"),data.active!!.baseline);assertTrue(data.active!!.imported)
        assertEquals(BigInteger.ZERO,data.active!!.sales);assertEquals(300L,data.pendingCash)
    }
    @Test fun loadedHistoryIsNotTruncatedAndNumberingFollowsPredecessorEvenAtEqualTimes() {
        val rows=(1..21).map {i->shift("s$i",previous=if(i==1) null else "s${i-1}").copy(closedAt=10,countedCash=0,expectedCash=0,difference=0)}
        val data=ShiftDashboardRules.build(ShiftSnapshot("r","owner",21,rows.last()),rows.reversed(),emptyList(),emptyList(),emptyList())
        assertEquals(21,data.history.size)
        assertEquals(rows.map {it.id}.sortedDescending(),data.history.map {it.shift.id})
        rows.forEachIndexed {index,row->assertEquals(index+1,data.history.single {it.shift.id==row.id}.number)}
    }
    @Test fun cardTurnoverUsesExactAggregateBeyondLongRange() {
        val active=shift("active")
        val rows=listOf("one","two").map {ShiftSaleFact(it,"active",11,Long.MAX_VALUE,0,Long.MAX_VALUE,0,0)}
        val data=ShiftDashboardRules.build(ShiftSnapshot("r","owner",1,active),listOf(active),rows,emptyList(),emptyList())
        assertEquals(Long.MAX_VALUE.toBigInteger()*BigInteger.TWO,data.active!!.sales)
        assertEquals(BigInteger.ZERO,data.active!!.expected)
    }
    @Test fun unexplainedNativeCashIsRejectedInsteadOfCalledWebCarry() {
        val active=shift("native")
        assertTrue(runCatching {ShiftDashboardRules.build(ShiftSnapshot("r","owner",1,active,100),listOf(active),emptyList(),emptyList(),emptyList())}.isFailure)
    }

}
