package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class PurchaseDraftRulesTest {
    private fun line()=PurchaseLine("p",1,"Молоко","l","24","bottle","500","ml")
    private fun reject(block: ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun bottleBoxAndPhysicalUnitsConvertExactlyWithoutFloatOrRounding() {
        assertEquals("12",PurchaseDraftRules.expected(line())!!.stripTrailingZeros().toPlainString())
        assertEquals("12",PurchaseDraftRules.expected(line().copy(quantity="2",unit="box",packSize="6000"))!!.stripTrailingZeros().toPlainString())
        assertEquals("0.0004",PurchaseDraftRules.expected(line().copy(stockUnit="kg",quantity="0,4",unit="g",packSize="",contentUnit="kg"))!!.toPlainString())
    }
    @Test fun unknownPackIsNotZeroAndIncompleteFieldsRemainDrafts() {
        assertNull(PurchaseDraftRules.expected(line().copy(packSize="")))
        assertNull(PurchaseDraftRules.expected(line().copy(quantity="")))
        assertNull(PurchaseDraftRules.expected(line().copy(quantity="0")))
        reject {PurchaseDraftRules.expected(line().copy(packSize="0"))}
        reject {PurchaseDraftRules.expected(line().copy(contentUnit="kg"))}
        reject {PurchaseDraftRules.expected(line().copy(unit="piece"))}
    }
    @Test fun invalidAndUnrepresentableNumbersAreRejectedInsteadOfRounded() {
        for(raw in listOf("-1","NaN","1e3","0.0000000000001")) reject {PurchaseDraftRules.expected(line().copy(quantity=raw))}
        reject {PurchaseDraftRules.expected(line().copy(stockUnit="l",unit="ml",quantity="0.000000000001",packSize=""))}
        reject {PurchaseDraftRules.expected(line().copy(quantity="999999999999999999",packSize="999999999999999999"))}
    }
    @Test fun staleReferencesRequireExplicitRevalidationAndKeepEnteredPackaging() {
        val d=PurchaseDraft("d","s",1,"Поставщик",listOf(line()))
        val supplier=Supplier("s","Новое имя",2,setOf("p"))
        val product=PurchaseProduct("p","Новое молоко",2,"l","","10","box","99")
        reject {PurchaseDraftRules.validateReferences(d,listOf(supplier),listOf(product))}
        val refreshed=PurchaseDraftRules.revalidate(d,listOf(supplier),listOf(product))
        assertEquals("500",refreshed.lines.single().packSize);assertEquals("ml",refreshed.lines.single().contentUnit)
        assertEquals("24",refreshed.lines.single().quantity);assertEquals(2L,refreshed.supplierRevision)
        assertEquals("12",PurchaseDraftRules.expected(refreshed.lines.single())!!.stripTrailingZeros().toPlainString())
        reject {PurchaseDraftRules.revalidate(d,listOf(supplier.copy(productIds=emptySet())),listOf(product))}
    }
    @Test fun duplicateLinesAndChangedReplayPayloadCannotPassValidation() {
        val d=PurchaseDraft("d","s",1,"Поставщик",listOf(line()))
        reject {PurchaseDraftRules.validate(d.copy(lines=d.lines+d.lines))}
        val c=PurchaseCommand("cmd","owner",0,PurchaseAction.Save(d))
        assertNotEquals(c.fingerprint(),c.copy(action=PurchaseAction.Save(d.copy(lines=listOf(line().copy(quantity="25"))))).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(actorId="other").fingerprint())
    }
}
