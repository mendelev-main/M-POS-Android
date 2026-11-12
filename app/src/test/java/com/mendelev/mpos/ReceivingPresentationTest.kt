package com.mendelev.mpos

import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.receiving.receivingDiscrepancy
import org.junit.Assert.*
import org.junit.Test

class ReceivingPresentationTest {
    private val product=ReceivingProduct("p","Молоко","l",1,1,"10","2",false)
    private val order=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","l"))),1)
    private fun record(actual: String,linked: Boolean=true)=PostedReceiving("r","command","owner",2,1,ReceivingDraft("invoice",if(linked) order else null,lines=listOf(ReceivingLine(product,actual,"l","","l","40"))),actual.toBigDecimal()>java.math.BigDecimal.TEN)
    @Test fun shortageBadgeUsesHistoricalReceiptAndDoesNotMarkConfirmedOverdelivery() {
        assertFalse(receivingDiscrepancy(record("10")))
        assertTrue(receivingDiscrepancy(record("9")))
        assertFalse(receivingDiscrepancy(record("12")))
        assertFalse(receivingDiscrepancy(record("9",false)))
    }
}
