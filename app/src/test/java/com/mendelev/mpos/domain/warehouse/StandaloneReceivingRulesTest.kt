package com.mendelev.mpos.domain.warehouse

import org.junit.Assert.*
import org.junit.Test

class StandaloneReceivingRulesTest {
    private val p=ReceivingProduct("p","Молоко","l",1,1,"10","2",false)
    private fun draft()=ReceivingDraft("invoice",null,lines=listOf(ReceivingLine(p,"10","l","","l","40")))
    @Test fun noSyntheticOrderAndExactStockWeightedCostHaveNoDiscrepancy() {
        val d=draft();ReceivingRules.validate(d)
        assertNull(d.order);assertEquals(ReceivingRules.STANDALONE,d.key);assertEquals("Без поставщика",d.supplierName)
        val result=ReceivingPostingRules.preview(d,null,listOf(p))
        assertEquals(4000L,result.totalMinor);assertEquals("20",result.lines.single().newStock);assertEquals("3",result.lines.single().newCost)
        assertNull(result.lines.single().expected);assertNull(result.lines.single().shortage);assertNull(result.lines.single().overage);assertFalse(result.hasOverage)
    }
    @Test fun incompleteEmptyDraftIsSaveableButCannotBePostedAndSupplierIsSnapshot() {
        val d=draft().copy(lines=emptyList(),supplier=ReceivingSupplier("s","Поставка",2))
        ReceivingRules.validate(d);assertEquals("Поставка",d.supplierName)
        try {ReceivingPostingRules.preview(d,null,listOf(p));fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
        assertNotEquals(ReceivingCommand("c","owner",d.key,0,d).fingerprint(),ReceivingCommand("c","owner",d.key,0,d.copy(supplier=d.supplier!!.copy(revision=3))).fingerprint())
        try {ReceivingRules.validate(d.copy(supplier=d.supplier!!.copy(revision=0)));fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun explicitRebaseKeepsInvoiceFactsWhileOldBaselineCannotPost() {
        val d=draft().copy(invoiceNumber="TTN",invoiceDate="2026-10-27");val next=p.copy(stock="8",stockRevision=2)
        try {ReceivingPostingRules.preview(d,null,listOf(next));fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
        val rebased=ReceivingRules.rebase(d,null,listOf(next))
        assertNull(rebased.order);assertEquals("TTN",rebased.invoiceNumber);assertEquals("40",rebased.lines.single().total);assertEquals("10",rebased.lines.single().quantity)
        assertEquals("18",ReceivingPostingRules.preview(rebased,null,listOf(next)).lines.single().newStock)
        val duplicate=d.copy(lines=d.lines+d.lines)
        try {ReceivingRules.validate(duplicate);fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
    }
    @Test fun legacyOrderFingerprintDoesNotAcquireStandaloneFields() {
        val o=PurchaseOrderRules.create("o",PurchaseDraft("request","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","l"))),1)
        val d=ReceivingRules.open("i",o,listOf(p)).let {it.copy(lines=it.lines.map {l->l.copy(total="40")})}
        val c=ReceivingCommand("c","owner","o",0,d)
        // Frozen pre-04a.7 vector: preserve durable operation-result replay.
        assertEquals("246388451f948b7554e353da685043aafe3097c6b1d45f12177bc7d3bef7109c",c.fingerprint())
        try {ReceivingRules.validate(d.copy(supplier=ReceivingSupplier("s","Поставка",1)));fail()} catch(_:IllegalArgumentException) {}
    }
}
