package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class PurchaseOrderMutationRulesTest {
    private fun draft()=PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","24","bottle","500","ml")))
    private fun reject(block: ()->Unit) {try {block();fail()} catch(_:CatalogFailure) {}}
    @Test fun editPreservesPreviousRequestIdentityAndSupplierSnapshot() {
        val old=PurchaseOrderRules.create("o",draft(),1)
        val input=draft().copy(supplierRevision=2,supplierName="Новое имя",lines=draft().lines.map {it.copy(quantity="12",productName="Новое молоко",productRevision=2)})
        val next=PurchaseOrderMutationRules.apply(old,PurchaseOrderCommand("edit","owner","o",1,PurchaseOrderAction.Edit(input)),2)
        assertEquals("o",next.id);assertEquals(1L,next.createdAt);assertEquals(2L,next.requestVersion);assertEquals(2L,next.revision)
        assertEquals(listOf("6"),next.expectedQuantities);assertEquals(old.source,next.history.single().source)
        assertEquals("Поставка",next.source.supplierName);assertEquals(1L,next.source.supplierRevision);assertEquals("Новое молоко",next.source.lines.single().productName)
    }
    @Test fun cancelPreservesRequestAndHistoryAndCannotBeEditedAgain() {
        val old=PurchaseOrderRules.create("o",draft(),1)
        val c=PurchaseOrderCommand("cancel","owner","o",1,PurchaseOrderAction.Cancel)
        val next=PurchaseOrderMutationRules.apply(old,c,2)
        assertEquals(old.source,next.source);assertEquals(old.expectedQuantities,next.expectedQuantities);assertEquals("cancelled",next.status)
        assertEquals(1L,next.requestVersion);assertEquals(2L,next.revision);assertEquals(2L,next.cancelledAt);assertEquals("owner",next.cancelledBy)
        assertTrue(PurchaseOrderRules.text(next).contains("Отменён"));assertFalse(PurchaseOrderRules.text(next).contains("Просьба подтвердить"))
        reject {PurchaseOrderMutationRules.apply(next,c.copy(commandId="other",revision=2),3)}
        reject {PurchaseOrderMutationRules.apply(next,c.copy(revision=2,action=PurchaseOrderAction.Edit(draft())),3)}
    }
    @Test fun emptyReplacementSupplierIdentityAndStaleRevisionAreRejected() {
        val old=PurchaseOrderRules.create("o",draft(),1)
        val c=PurchaseOrderCommand("e","owner","o",1,PurchaseOrderAction.Edit(draft()))
        reject {PurchaseOrderMutationRules.apply(old,c.copy(revision=2),2)}
        reject {PurchaseOrderMutationRules.apply(old,c.copy(action=PurchaseOrderAction.Edit(draft().copy(id="other"))),2)}
        reject {PurchaseOrderMutationRules.apply(old,c.copy(action=PurchaseOrderAction.Edit(draft().copy(supplierId="other"))),2)}
        reject {PurchaseOrderMutationRules.apply(old,c.copy(action=PurchaseOrderAction.Edit(draft().copy(lines=emptyList()))),2)}
    }
    @Test fun payloadAndActorChangesCannotReplayTheSameMutation() {
        val c=PurchaseOrderCommand("c","owner","o",1,PurchaseOrderAction.Cancel)
        assertNotEquals(c.fingerprint(),c.copy(action=PurchaseOrderAction.Edit(draft())).fingerprint())
        assertNotEquals(c.fingerprint(),c.copy(actorId="other").fingerprint());assertNotEquals(c.fingerprint(),c.copy(revision=2).fingerprint())
    }
}
