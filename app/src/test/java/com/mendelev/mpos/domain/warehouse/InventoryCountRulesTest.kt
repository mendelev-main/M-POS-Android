package com.mendelev.mpos.domain.warehouse

import org.junit.Assert.*
import org.junit.Test

class InventoryCountRulesTest {
    private val p=InventoryProduct("p","Мука","Сырьё","kg",1,1,"10","2")
    @Test fun exactRawCountZeroAndCommaAreNotDisplayRounded() {
        assertEquals("0.0004",InventoryRules.actual("0,0004"));assertEquals("0",InventoryRules.actual("0"))
        assertEquals("-9.9996",InventoryRules.delta(InventoryCount(p,"0.0004","runtime")))
        assertEquals("2",InventoryRules.delta(InventoryCount(p.copy(stock="-2"),"0","runtime")))
        for(raw in listOf(""," ","-1","NaN","0.0000000000001","1000000000000000000")) assertTrue(raw,runCatching {InventoryRules.actual(raw)}.isFailure)
    }
    @Test fun costNameAndCategoryChangeDoNotInvalidateButEqualStockWithNewRevisionDoes() {
        assertTrue(InventoryRules.sameStock(p,p.copy(name="Новое имя",category="Другое",cost="99",revision=2)))
        assertFalse(InventoryRules.sameStock(p,p.copy(stockRevision=2)));assertFalse(InventoryRules.sameStock(p,p.copy(stock="9")))
        assertFalse(InventoryRules.sameStock(p,p.copy(unit="g")));assertFalse(InventoryRules.sameStock(p,p.copy(id="other")))
    }
    @Test fun fixedSnapshotMustMatchActualDeltaAndNextStockRevision() {
        val fixed=InventoryFix(p,"0.0004","-9.9996",1,"owner","fix","movement",2)
        val c=InventoryCount(p,"0,0004","runtime",fixed);InventoryRules.validate(c)
        for(f in listOf(fixed.copy(delta="-9.999"),fixed.copy(actual="0"),fixed.copy(stockRevisionAfter=3),fixed.copy(product=p.copy(stock="9")))) assertTrue(runCatching {InventoryRules.validate(c.copy(fixed=f))}.isFailure)
    }
    @Test fun countFingerprintBindsRawBaselineGenerationAndFixRevision() {
        val c=InventoryCommand("c","owner",2,InventoryAction.Count("d",p,"0,0004","runtime"))
        assertNotEquals(c.fingerprint(),c.copy(action=InventoryAction.Count("d",p,"0.0004","runtime")).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(action=InventoryAction.Count("d",p.copy(stockRevision=2),"0,0004","runtime")).fingerprint())
        val f=c.copy(action=InventoryAction.Fix("d","p","runtime"));assertNotEquals(f.fingerprint(),f.copy(action=InventoryAction.Fix("d","p","next-process")).fingerprint());assertNotEquals(f.fingerprint(),f.copy(revision=3).fingerprint())
    }
    @Test fun draftCannotAcquireDuplicateOrOutOfScopeCountsAndMovementOverflowRefuses() {
        val c=InventoryCount(p,"1","runtime");val d=InventoryDraft("d","adhoc",1,"2026-10-28",listOf(p),listOf(c))
        InventoryRules.validate(InventoryDocument(draft=d))
        assertTrue(runCatching {InventoryRules.validate(InventoryDocument(draft=d.copy(counts=listOf(c,c))))}.isFailure)
        assertTrue(runCatching {InventoryRules.validate(InventoryDocument(draft=d.copy(counts=listOf(c.copy(product=p.copy(id="other"))))))}.isFailure)
        assertTrue(runCatching {InventoryRules.delta(c.copy(product=p.copy(stock="-999999999999999999"),raw="999999999999999999"))}.isFailure)
    }
    @Test fun legacyConfigureFingerprintDoesNotIncludeCountFields() {
        assertEquals("3b65d9c7baf946435353abc2575f047bcf3e91b9d25aab2cbab41af924a8e3a8",InventoryCommand("c","owner",2,InventoryAction.Configure(InventoryConfig(true,"weekly",setOf("b","a")))).fingerprint())
    }

}
