package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class ReceivingRulesTest {
    private fun product(stock: String="10",cost: String="2",tracking: Boolean=true)=ReceivingProduct("p","Молоко","l",2,1,stock,cost,!tracking)
    private fun order(unknown: Boolean=false)=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","bottle",if(unknown) "" else "1000","ml"))),1)
    private fun draft(o: PurchaseOrder=order(),p: ReceivingProduct=product())=ReceivingRules.open("r",o,listOf(p))
    private fun reject(block: ()->Unit) {try {block();fail()} catch(_:CatalogFailure) {}}
    @Test fun tenAtTwoPlusTenForFortyPreviewThreeWithoutMutatingBaseline() {
        val o=order();val p=product();val d=draft(o,p).let {it.copy(lines=it.lines.map {l->l.copy(total="40")})}
        val result=ReceivingRules.preview(d,o,listOf(p));val row=result.lines.single()
        assertEquals("20",row.newStock);assertEquals("3",row.newCost);assertEquals(4000L,result.totalMinor)
        assertEquals("10",p.stock);assertEquals("2",p.cost);assertEquals("0",row.shortage);assertEquals("0",row.overage)
    }
    @Test fun blankZeroUnknownAndMissingMoneyAreNotInventedFacts() {
        val o=order(true);val p=product();val d=draft(o,p);assertEquals("",d.lines.single().quantity)
        reject {ReceivingRules.preview(d,o,listOf(p))}
        val unknown=d.copy(lines=d.lines.map {it.copy(quantity="3",total="12")});reject {ReceivingRules.preview(unknown,o,listOf(p))}
        val known=unknown.copy(lines=unknown.lines.map {it.copy(packSize="1000")})
        assertNull(ReceivingRules.preview(known,o,listOf(p)).lines.single().expected)
        reject {ReceivingRules.preview(known.copy(lines=known.lines.map {it.copy(total="")}),o,listOf(p))}
        reject {ReceivingRules.preview(known.copy(lines=known.lines.map {it.copy(quantity="0",total="1")}),o,listOf(p))}
        reject {ReceivingRules.preview(known.copy(lines=known.lines.map {it.copy(quantity="0",total="0")}),o,listOf(p))}
    }
    @Test fun shortageOverageNegativeAndNonTrackingFollowSourcePreviewOnly() {
        val o=order();val p=product()
        fun amount(q: String,p: ReceivingProduct)=draft(o,p).let {it.copy(lines=it.lines.map {l->l.copy(quantity=q,total="12")})}
        assertEquals("7",ReceivingRules.preview(amount("3",p),o,listOf(p)).lines.single().shortage)
        assertTrue(ReceivingRules.preview(amount("12",p),o,listOf(p)).hasOverage)
        val negative=product("-2");val n=ReceivingRules.preview(amount("3",negative),o,listOf(negative)).lines.single()
        assertEquals("1",n.newStock);assertEquals("4",n.newCost)
        val noTracking=product(tracking=false);val t=ReceivingRules.preview(amount("3",noTracking),o,listOf(noTracking)).lines.single()
        assertEquals("10",t.newStock);assertEquals("4",t.newCost)
    }
    @Test fun rebaseKeepsInvoiceFactsRemovedRowsAndAppendsBlankNewProducts() {
        val o=order();val p=product();val original=draft(o,p).copy(invoiceNumber="TTN",lines=draft(o,p).lines.map {it.copy(quantity="3",total="12")})
        val input=o.source.copy(lines=listOf(o.source.lines.single().copy(productId="new")))
        val changed=PurchaseOrderMutationRules.apply(o,PurchaseOrderCommand("edit","owner","o",1,PurchaseOrderAction.Edit(input)),2)
        val newer=p.copy(revision=3,stockRevision=2,stock="8",cost="5")
        val rebased=ReceivingRules.rebase(original,changed,listOf(newer,p.copy(id="new")))
        assertEquals("TTN",rebased.invoiceNumber);assertEquals("3",rebased.lines.first().quantity);assertEquals("12",rebased.lines.first().total)
        assertEquals("",rebased.lines.last().quantity);assertEquals("",rebased.lines.last().total);assertEquals("8",rebased.lines.first().product.stock)
        reject {ReceivingRules.preview(original,o,listOf(newer))}
        val cancelled=PurchaseOrderMutationRules.apply(changed,PurchaseOrderCommand("cancel","owner","o",2,PurchaseOrderAction.Cancel),3)
        reject {ReceivingRules.rebase(rebased,cancelled,listOf(newer,p.copy(id="new")))}
    }
    @Test fun exactQuantityMoneyLimitsAndPhysicalUnitSwitchNeverRoundStock() {
        val line=ReceivingLine(product(),"0,4","l","","l","1")
        assertEquals("400",ReceivingRules.switchUnit(line,"ml").quantity)
        assertEquals("0.4",ReceivingRules.switchUnit(ReceivingRules.switchUnit(line,"ml"),"l").quantity)
        reject {ReceivingRules.money("0.001")};reject {ReceivingRules.money("999999999999999999")}
        assertEquals(123L,ReceivingRules.money("1,23"))
        reject {ReceivingRules.switchUnit(line,"kg")}
        val kg=product().copy(unit="kg");val o=PurchaseOrderRules.create("o",order().source.copy(lines=listOf(PurchaseLine("p",1,"Молоко","kg","1","kg","","kg"))),1)
        val d=draft(o,kg).copy(lines=listOf(ReceivingLine(kg,"0.4","g","","kg","1")))
        assertEquals("0.0004",ReceivingRules.preview(d,o,listOf(kg)).lines.single().actual)
    }
}
