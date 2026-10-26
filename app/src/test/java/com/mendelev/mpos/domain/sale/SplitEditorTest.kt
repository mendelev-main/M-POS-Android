package com.mendelev.mpos.domain.sale

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class SplitEditorTest {
    private fun reject(block: () -> Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    private fun split(vararg amounts: Long)=SplitEditor.create(amounts.sum(),amounts.map {PaymentInput("cash",it,it)})
    private fun paid(state: EditableSplit,id: Long)=state.copy(parts=state.parts.map {if(it.id==id) it.copy(paid=true) else it})
    private fun amounts(state: EditableSplit)=state.parts.map {it.payment.amount}

    @Test fun resizePreservesPaidIdentityAndTenderWhileMovingItBeforeUnpaidRows() {
        val initial=paid(split(334,333,333),2)
        val next=SplitEditor.resize(initial,4)
        assertEquals(listOf(2L,1L,3L,4L),next.parts.map {it.id})
        assertEquals(initial.parts[1],next.parts[0])
        assertEquals(listOf(333L,223L,222L,222L),amounts(next))
        assertTrue(next.parts.drop(1).all {it.payment.given==null})
        assertEquals(5L,next.nextId)
        assertEquals(listOf(334L,333L,333L),amounts(initial))
    }

    @Test fun removedIdsAreNeverReusedAndCannotTargetReplacementPart() {
        val initial=split(250,250,250,250)
        val smaller=SplitEditor.resize(initial,2)
        assertEquals(listOf(500L,500L),amounts(smaller))
        val larger=SplitEditor.resize(smaller,4)
        assertEquals(listOf(1L,2L,5L,6L),larger.parts.map {it.id})
        reject {SplitEditor.amount(larger,3,100)}
        reject {SplitEditor.method(larger,4,"card")}
        assertEquals(7L,larger.nextId)
    }

    @Test fun editingAnAnchorKeepsPreviousAndPaidRowsAndDistributesFollowingKopecks() {
        val initial=paid(split(200,200,200,200,200),2)
        val next=SplitEditor.amount(initial,3,101)
        assertEquals(listOf(200L,200L,101L,250L,249L),amounts(next))
        assertEquals(initial.parts.take(2),next.parts.take(2))
        assertTrue(next.parts.drop(2).all {it.payment.given==null})
        val clamped=SplitEditor.amount(initial,3,Long.MAX_VALUE)
        assertEquals(listOf(200L,200L,600L,0L,0L),amounts(clamped))
    }

    @Test fun editingLastUnpaidRedistributesEarlierUnpaidWhileSingleUnpaidIsExactRemainder() {
        val initial=paid(split(334,333,333),2)
        val last=SplitEditor.amount(initial,3,100)
        assertEquals(listOf(567L,333L,100L),amounts(last))
        assertEquals(initial.parts[1],last.parts[1])
        val single=paid(initial,1)
        assertEquals(listOf(334L,333L,333L),amounts(SplitEditor.amount(single,3,0)))
        assertEquals(listOf(334L,333L,333L),amounts(SplitEditor.amount(single,3,Long.MAX_VALUE)))
    }

    @Test fun paidPartsRejectEveryEditAndCannotBeRemovedToHideAnUnpaidRemainder() {
        val initial=paid(paid(split(400,300,300),1),2)
        reject {SplitEditor.amount(initial,1,100)}
        reject {SplitEditor.method(initial,1,"card")}
        reject {SplitEditor.given(initial,1,500)}
        reject {SplitEditor.resize(initial,2)}
        val allPaid=paid(initial,3)
        reject {SplitEditor.resize(allPaid,2)}
        assertEquals(allPaid.parts,SplitEditor.resize(allPaid,4).parts.take(3))
    }

    @Test fun methodChangesClearTenderAndGivenValidatesWithoutChangingAmountOrOtherRows() {
        val initial=split(4000,6000)
        val tender=SplitEditor.given(initial,1,5000)
        assertEquals(1000L,tender.parts[0].payment.given!!-tender.parts[0].payment.amount)
        assertEquals(initial.parts[1],tender.parts[1])
        reject {SplitEditor.given(tender,1,3999)}
        val card=SplitEditor.method(tender,1,"card")
        assertNull(card.parts[0].payment.given)
        reject {SplitEditor.given(card,1,5000)}
        val cash=SplitEditor.method(card,1,"cash")
        assertNull(cash.parts[0].payment.given)
        assertEquals(listOf(4000L,6000L),amounts(cash))
        assertEquals(tender,SplitEditor.method(tender,1,"cash"))
    }

    @Test fun zeroPartsAndZeroRemainingCanBeRetainedWithoutLosingAKopeck() {
        val initial=paid(split(1,0,0),1)
        assertEquals(listOf(1L,0L),amounts(SplitEditor.resize(initial,2)))
        assertEquals(listOf(1L,0L,0L,0L),amounts(SplitEditor.resize(initial,4)))
        assertEquals(listOf(0L,0L),amounts(SplitEditor.resize(split(0,0,0),2)))
        val twoPaid=paid(initial,2)
        assertEquals(listOf(1L,0L),amounts(SplitEditor.resize(twoPaid,2)))
    }

    @Test fun corruptInputAndOverflowAreRefusedRatherThanNormalizedAway() {
        val initial=split(400,600)
        reject {SplitEditor.create(1000,listOf(PaymentInput("cash",1000)))}
        reject {SplitEditor.validate(initial.copy(total=999))}
        reject {SplitEditor.validate(initial.copy(nextId=2))}
        reject {SplitEditor.validate(initial.copy(parts=listOf(initial.parts[0],initial.parts[0])))}
        reject {SplitEditor.create(0,listOf(PaymentInput("card",-1),PaymentInput("card",1)))}
        reject {SplitEditor.create(Long.MAX_VALUE,listOf(PaymentInput("card",Long.MAX_VALUE),PaymentInput("card",1)))}
        reject {SplitEditor.create(1000,listOf(PaymentInput("card",400,500),PaymentInput("card",600)))}
        reject {SplitEditor.validate(paid(initial.copy(parts=initial.parts.map {it.copy(payment=it.payment.copy(given=null))}),1))}
        reject {SplitEditor.resize(initial,11)}
        reject {SplitEditor.resize(initial,1)}
        reject {SplitEditor.amount(initial,1,-1)}
        reject {SplitEditor.method(initial,1,"bank")}
        reject {SplitEditor.resize(initial.copy(nextId=Long.MAX_VALUE),3)}
    }

    @Test fun editsAcrossCountsAndExtremeTotalsPreserveSumIdsAndConfirmedSnapshots() {
        listOf(0L,1L,10L,1000L,Long.MAX_VALUE).forEach {total->
            (2..10).forEach {count->
                val base=SplitEditor.create(total,SaleMoney.equalParts(total,count).map {PaymentInput("cash",it,it)})
                val initial=paid(base,2)
                (2..10).forEach {nextCount->
                    val next=SplitEditor.resize(initial,nextCount)
                    assertEquals(initial.parts[1],next.parts.single {it.id==2L})
                    assertEquals(total,next.parts.fold(0L) {sum,p->Math.addExact(sum,p.payment.amount)})
                    next.parts.filterNot {it.paid}.forEach {target->
                        listOf(0L,1L,total).forEach {requested->
                            val edited=SplitEditor.amount(next,target.id,requested)
                            assertEquals(next.parts.map {it.id},edited.parts.map {it.id})
                            assertEquals(initial.parts[1],edited.parts.single {it.id==2L})
                            assertEquals(total,edited.parts.fold(0L) {sum,p->Math.addExact(sum,p.payment.amount)})
                            assertTrue(edited.parts.all {it.payment.amount>=0})
                        }
                    }
                }
            }
        }
    }
}
