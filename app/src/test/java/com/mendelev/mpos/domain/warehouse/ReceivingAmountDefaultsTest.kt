package com.mendelev.mpos.domain.warehouse

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ReceivingAmountDefaultsTest {
    private val product=ReceivingProduct("milk","Молоко","l",1,1,"10","2",false)
    private fun receipt(id: String,at: Long,supplier: String,quantity: String,total: String,unit: String="l",pack: String="",content: String="l") =
        PostedReceiving(id,"cmd$id","owner",at,1,ReceivingDraft("d$id",null,lines=listOf(ReceivingLine(product,quantity,unit,pack,content,total)),supplier=ReceivingSupplier(supplier,"Поставщик",1)),false)

    @Test fun currentCostKeepsItsPrecisionUntilLineAmountIsRounded() {
        val p=product.copy(cost="2.345")
        assertEquals(BigDecimal("2.345"),ReceivingAmountDefaults.cost(p))
        assertEquals("4.69",ReceivingAmountDefaults.amount(ReceivingLine(p,"2","l","","l"),ReceivingAmountDefaults.cost(p)!!))
        assertNull(ReceivingAmountDefaults.cost(product.copy(cost="ошибка")))
    }
    @Test fun packsAndMillilitresUseBaseUnitAndHalfUpLineAmount() {
        val price=ReceivingAmountDefaults.cost(product.copy(cost="5"))!!
        val line=ReceivingLine(product,"3","box","250","ml")
        assertEquals("3.75",ReceivingAmountDefaults.amount(line,price))
        assertEquals("3.53",ReceivingAmountDefaults.amount(ReceivingLine(product,"1.5","l","","l"),BigDecimal("2.35")))
    }
    @Test fun manualInvoiceTenForThreeIsNeverRecomputedFromRoundedUnitCost() {
        val line=ReceivingLine(product,"3","l","","l","10")
        assertEquals("3.33",ReceivingAmountDefaults.unitPrice(line))
        assertEquals(1000L,ReceivingRules.lineTotal(line))
        assertNull(ReceivingAmountDefaults.unitPrice(line.copy(quantity="0",total="0")))
        assertNull(ReceivingAmountDefaults.amount(line.copy(quantity="не число"),BigDecimal("2")))
    }
    @Test fun overdeliveryIsNotShortageAndZeroSupplyHasZeroAmount() {
        val order=PurchaseOrderRules.create("o",PurchaseDraft("pd","s",1,"Поставщик",listOf(PurchaseLine("milk",1,"Молоко","l","10","l"))),0)
        val d=ReceivingRules.open("d",order,listOf(product)).copy(lines=listOf(ReceivingLine(product,"12","l","","l","24")))
        assertFalse(ReceivingAmountDefaults.shortage(PostedReceiving("r","cmd","owner",1,1,d,true)))
        assertTrue(ReceivingAmountDefaults.shortage(PostedReceiving("r2","cmd2","owner",2,1,d.copy(lines=d.lines.map {it.copy(quantity="3",total="6")}),false)))
        assertEquals("0.00",ReceivingAmountDefaults.amount(d.lines.single().copy(quantity="0"),ReceivingAmountDefaults.cost(product)!!))
    }
}
