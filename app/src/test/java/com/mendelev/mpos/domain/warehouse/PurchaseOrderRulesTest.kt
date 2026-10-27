package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class PurchaseOrderRulesTest {
    private fun draft()=PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","24","bottle","500","ml")))
    @Test fun onlyPositiveLinesAreSubmittedAndUnknownPackRemainsNull() {
        val d=draft();val line=d.lines.single()
        val o=PurchaseOrderRules.create("o",d.copy(lines=d.lines+line.copy(productId="zero",quantity="0")+line.copy(productId="empty",quantity="")+line.copy(productId="unknown",packSize="")),1)
        assertEquals(listOf("p","unknown"),o.source.lines.map {it.productId});assertEquals(listOf("12",null),o.expectedQuantities)
        assertEquals("0.5",PurchaseOrderRules.defaultPackSize(line));assertNull(PurchaseOrderRules.defaultPackSize(line.copy(packSize="")))
        try {PurchaseOrderRules.create("empty",d.copy(lines=listOf(line.copy(quantity="0"))),1);fail()} catch(_:CatalogFailure) {}
    }
    @Test fun historicalTextUsesCapturedNamesAndPackagingWithoutCatalog() {
        val o=PurchaseOrderRules.create("o",draft(),1);val text=PurchaseOrderRules.text(o)
        assertTrue(text.contains("Поставка"));assertTrue(text.contains("Молоко — 24 бутылка × 500 мл"))
        assertEquals("pending",o.status);assertEquals(1L,o.requestVersion)
        try {PurchaseOrderRules.validate(o.copy(expectedQuantities=listOf("0")));fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun placeFingerprintCannotCollideWithDraftSaveOrAnotherOrder() {
        val d=draft();val c=PurchaseCommand("c","owner",1,PurchaseAction.Place("o",d))
        assertNotEquals(c.fingerprint(),c.copy(action=PurchaseAction.Save(d)).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(action=PurchaseAction.Place("other",d)).fingerprint())
    }
}
