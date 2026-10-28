package com.mendelev.mpos.domain.warehouse

import org.junit.Assert.*
import org.junit.Test

class InventoryClosingRulesTest {
    private fun count(id: String,stock: String="3",actual: String="1.5",cost: String="2.35"): InventoryCount {
        val p=InventoryProduct(id,"Мука","Сырьё","kg",1,1,stock,cost)
        val c=InventoryCount(p,actual,"runtime")
        return c.copy(fixed=InventoryFix(p,actual,InventoryRules.delta(c),1,"owner","fix-$id","move-$id",2))
    }
    private fun draft(vararg c: InventoryCount)=InventoryDraft("d","scheduled",1,"2026-10-01",c.map {it.product},c.toList())
    @Test fun roundsEachShortageHalfUpAndDoesNotOffsetSurpluses() {
        val d=draft(count("a"),count("b"),count("c",actual="5"))
        assertEquals(353L,InventoryClosingRules.lineLoss(d.counts.first()))
        val s=InventoryClosingRules.summary(d);assertEquals(706L,s.lossMinor);assertEquals(2,s.shortages);assertEquals(1,s.surpluses)
    }
    @Test fun unfixedInputHasNoValuationAndOnlyCancelCanCloseIt() {
        val c=count("a").copy(fixed=null);val d=draft(c)
        val r=InventoryRecord(d,true,1,"2026-11-01","owner","close",0)
        InventoryClosingRules.validate(r);assertEquals(1,InventoryClosingRules.summary(d).remaining)
        assertTrue(runCatching {InventoryClosingRules.validate(r.copy(cancelled=false))}.isFailure)
    }
    @Test fun overflowIsExplicitlyUnavailableAndDoesNotPreventClosing() {
        val d=draft(count("a",stock="999999999999999999",actual="0",cost="999999999999999999"))
        assertNull(InventoryClosingRules.summary(d).lossMinor)
        InventoryClosingRules.validate(InventoryRecord(d,false,1,"2026-11-01","owner","close",null))
        assertTrue(runCatching {InventoryClosingRules.validate(InventoryRecord(d,false,1,"2026-11-01","owner","close",0))}.isFailure)
    }
    @Test fun historyRejectsDuplicateDocumentsActiveReuseAndChangedValuation() {
        val d=draft(count("a"));val r=InventoryRecord(d,false,1,"2026-11-01","owner","close",353)
        InventoryRules.validate(InventoryDocument(history=listOf(r)))
        assertTrue(runCatching {InventoryRules.validate(InventoryDocument(history=listOf(r,r)))}.isFailure)
        assertTrue(runCatching {InventoryRules.validate(InventoryDocument(draft=d,history=listOf(r)))}.isFailure)
        assertTrue(runCatching {InventoryClosingRules.validate(r.copy(lossMinor=352))}.isFailure)
    }
    @Test fun finishFingerprintBindsStatusDocumentAndRevision() {
        val c=InventoryCommand("close","owner",4,InventoryAction.Finish("d",false))
        assertNotEquals(c.fingerprint(),c.copy(action=InventoryAction.Finish("d",true)).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(action=InventoryAction.Finish("other",false)).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(revision=5).fingerprint())
    }
}
