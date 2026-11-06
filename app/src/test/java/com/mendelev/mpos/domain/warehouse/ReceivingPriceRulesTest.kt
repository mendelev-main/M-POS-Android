package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class ReceivingPriceRulesTest {
    private val p=ReceivingProduct("p","Молоко","l",1,1,"10","2",false)
    private fun line(q: String="1.5",price: String="2.35")=ReceivingLine(p,q,"l","","l",priceMode="price",unitPrice=price)
    private fun draft(l: ReceivingLine)=ReceivingDraft("invoice",null,lines=listOf(l))
    private fun refuse(block: ()->Unit) {try {block();fail("expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun roundsEachLineBeforeSummingAndUsesRoundedValueForCost() {
        val l=line();assertEquals(353L,ReceivingRules.lineTotal(l))
        val d=draft(l).copy(lines=listOf(l,l.copy(product=p.copy(id="p2"))))
        val result=ReceivingPostingRules.preview(d,null,d.lines.map {it.product})
        assertEquals(706L,result.totalMinor);assertEquals("11.5",result.lines.first().newStock);assertEquals("2.05",result.lines.first().newCost)
        assertEquals(1L,ReceivingRules.lineTotal(line("0.5","0.01")))
    }
    @Test fun boxPriceAppliesToInvoiceQuantityAndNeedsExplicitPackContent() {
        val l=line("3","10").copy(unit="box",packSize="500",contentUnit="ml")
        assertEquals(l,ReceivingRules.switchUnit(l,"box"))
        val result=ReceivingPostingRules.preview(draft(l),null,listOf(p))
        assertEquals(3000L,result.totalMinor);assertEquals("1.5",result.lines.single().actual)
        refuse {ReceivingPostingRules.preview(draft(l.copy(packSize="")),null,listOf(p))}
    }
    @Test fun incompletePriceCanSaveButPositiveReceiptRequiresPriceAndZeroNeedsNone() {
        val l=line(price="");ReceivingRules.validate(draft(l));assertNull(ReceivingRules.lineTotal(l))
        refuse {ReceivingPostingRules.preview(draft(l),null,listOf(p))}
        assertEquals(0L,ReceivingRules.lineTotal(l.copy(quantity="0")))
        refuse {ReceivingPostingRules.preview(draft(l.copy(quantity="0")),null,listOf(p))}
        refuse {ReceivingRules.validate(draft(line().copy(total="3.53")))}
        refuse {ReceivingRules.validate(draft(line().copy(priceMode="total",total="3.53")))}
    }
    @Test fun physicalConversionPreservesQuantityPriceAndTotalWhilePacksClearPrice() {
        val l=line();val ml=ReceivingRules.switchUnit(l,"ml")
        assertEquals("1500",ml.quantity);assertEquals("0.00235",ml.unitPrice);assertEquals(353L,ReceivingRules.lineTotal(ml))
        assertEquals(l,ReceivingRules.switchUnit(ml,"l"));assertEquals("",ReceivingRules.switchUnit(l,"box").unitPrice)
        refuse {ReceivingRules.switchUnit(line(price="0.000000000001"),"ml")}
    }
    @Test fun explicitModeSwitchFreezesRoundedAmountAndNewPriceRequiresInput() {
        val total=ReceivingRules.priceMode(line(),"total")
        assertEquals("3.53",total.total);assertEquals("",total.unitPrice)
        val price=ReceivingRules.priceMode(total,"price");assertEquals("",price.total);assertEquals("",price.unitPrice)
        assertNotEquals(ReceivingCommand("c","owner",draft(line()).key,0,draft(line())).fingerprint(),ReceivingCommand("c","owner",draft(line()).key,0,draft(line(price="2.36"))).fingerprint())
    }
    @Test fun refusesNegativeExcessPrecisionAndMoneyOverflowWithoutFloatingPoint() {
        for(l in listOf(line(price="-1"),line(price="0.0000000000001"),line("999999999999999999","999999999999999999"))) refuse {ReceivingRules.validate(draft(l))}
        assertEquals(0L,ReceivingRules.lineTotal(line("0.0004","0.01")))
    }
}
