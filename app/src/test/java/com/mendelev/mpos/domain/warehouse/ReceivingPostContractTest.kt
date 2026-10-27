package com.mendelev.mpos.domain.warehouse

import org.junit.Assert.*
import org.junit.Test

class ReceivingPostContractTest {
    private fun order()=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","bottle","1000","ml"))),1)
    @Test fun receivedOrderIsTerminalAndKeepsRequestIdentity() {
        val o=order();val received=o.copy(status="received",revision=2,updatedAt=2,receivingId="r",receivedAt=2,receivedBy="owner")
        PurchaseOrderRules.validate(received)
        assertEquals(o.source,received.source);assertEquals(o.expectedQuantities,received.expectedQuantities);assertEquals("Принят",purchaseOrderStatus(received))
        assertTrue(PurchaseOrderRules.text(received).contains("Поставка принята"))
        try {PurchaseOrderMutationRules.apply(received,PurchaseOrderCommand("cancel","owner","o",2,PurchaseOrderAction.Cancel),3);fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
        try {PurchaseOrderRules.validate(received.copy(receivingId=null));fail()} catch(_:Exception) {}
        try {PurchaseOrderRules.validate(o.copy(receivingId="r"));fail()} catch(_:Exception) {}
    }
    @Test fun postingFingerprintCapturesFactsBaselineRevisionAndExplicitApproval() {
        val o=order();val p=ReceivingProduct("p","Молоко","l",2,1,"10","2",false)
        val d=ReceivingRules.open("r",o,listOf(p)).let {it.copy(lines=it.lines.map {l->l.copy(total="40")})}
        val c=ReceivingPostCommand("cmd","owner",1,d)
        assertEquals(c.fingerprint(),c.copy(commandId="retry-id").fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(overageConfirmed=true).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(revision=2).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(draft=d.copy(invoiceNumber="changed")).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(draft=d.copy(lines=d.lines.map {it.copy(product=p.copy(stockRevision=2))})).fingerprint())
        try {c.copy(revision=0).validate();fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
    }
    @Test fun costPolicyRoundsOnlyFinalCostAndRejectsPositiveUnderflow() {
        val o=order();val p=ReceivingProduct("p","Молоко","l",2,1,"0","0",false)
        fun draft(q: String,total: String)=ReceivingRules.open("r",o,listOf(p)).let {it.copy(lines=it.lines.map {l->l.copy(quantity=q,total=total)})}
        val d=draft("3","10")
        assertEquals("3.333333333333",ReceivingPostingRules.preview(d,o,listOf(p)).lines.single().newCost)
        assertEquals(1000L,ReceivingPostingRules.preview(d,o,listOf(p)).totalMinor)
        val tiny=draft("999999999999999999","0.01")
        try {ReceivingPostingRules.preview(tiny,o,listOf(p));fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
        val hugeCost=draft("0.000000000001","92233720368547758.07")
        try {ReceivingPostingRules.preview(hugeCost,o,listOf(p));fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
        val free=draft("3","0");assertEquals("0",ReceivingPostingRules.preview(free,o,listOf(p)).lines.single().newCost)
        val over=draft("12","48")
        try {ReceivingPostingRules.validate(PostedReceiving("receipt","cmd","owner",1,1,over,false));fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
        ReceivingPostingRules.validate(PostedReceiving("receipt","cmd","owner",1,1,over,true))
    }

}
