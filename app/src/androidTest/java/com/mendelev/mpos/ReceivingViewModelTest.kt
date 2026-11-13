package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.receiving.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ReceivingViewModelTest {
    private class Fake: ReceivingAccess {
        val p=ReceivingProduct("p","Молоко","l",2,1,"10","2",false)
        val order=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","bottle","1000","ml"))),1)
        var s=ReceivingSnapshot("owner",listOf(order),emptyList(),listOf(p))
        var calls=0;var failRead=false;var failOutcome=false;var lose=false
        val results=mutableMapOf<String,String>()
        override suspend fun snapshot(): ReceivingSnapshot {if(failRead) error("read");return s}
        override suspend fun outcome(c: ReceivingCommand): String? {if(failOutcome) error("outcome");return results[c.commandId]}
        override suspend fun commit(c: ReceivingCommand): String {
            calls++;val record=ReceivingRecord(c.orderId,c.revision+1,c.draft);s=s.copy(records=s.records.filterNot {it.orderId==c.orderId}+record)
            val id=c.draft?.id ?: c.orderId;results[c.commandId]=id
            if(lose) {failOutcome=true;error("reply")};return id
        }
    }
    private fun model(store: ViewModelStore,f: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=ReceivingViewModel(f,autoSaveEnabled=false) as T
    })[ReceivingViewModel::class.java]
    private suspend fun ready(vm: ReceivingViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    @Test fun editedOrderAddsProductOnReopenAndRefreshWithoutLosingReceivingFacts(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.open("o")};ready(vm)
            withContext(Dispatchers.Main) {vm.change {it.copy(invoiceNumber="TT84",invoiceDate="2026-11-13",lines=it.lines.map {l->l.copy(quantity="3",total="12")})};vm.save()};ready(vm)
            val second=f.p.copy(id="p2",name="Вода")
            val added=f.order.source.lines.single().copy(productId="p2",productName="Вода")
            val edited=f.order.copy(source=f.order.source.copy(lines=f.order.source.lines+added),revision=2,requestVersion=2,updatedAt=2,expectedQuantities=f.order.expectedQuantities+f.order.expectedQuantities,history=listOf(PurchaseRequestRevision(f.order.source,f.order.expectedQuantities,1,2)))
            f.s=f.s.copy(orders=listOf(edited),products=listOf(f.p,second))
            withContext(Dispatchers.Main) {vm.refresh()};ready(vm)
            val draft=vm.state.value.form!!
            assertEquals(listOf("p","p2"),draft.lines.map {it.product.id})
            assertEquals("3",draft.lines.first().quantity);assertEquals("12",draft.lines.first().total)
            assertEquals("TT84",draft.invoiceNumber);assertEquals("2026-11-13",draft.invoiceDate)
            assertEquals(edited,draft.order);assertEquals("10",f.s.products.first().stock)
            assertEquals("10",draft.lines.last().quantity);assertEquals("20.00",draft.lines.last().total)
            assertTrue("p2" in vm.state.value.suggestedAmounts)
            withContext(Dispatchers.Main) {vm.save()};ready(vm)
            withContext(Dispatchers.Main) {vm.close();vm.open("o")};ready(vm)
            assertEquals(draft,vm.state.value.form)
            val removed=PurchaseOrderMutationRules.apply(edited,PurchaseOrderCommand("remove","owner","o",2,
                PurchaseOrderAction.Edit(edited.source.copy(lines=listOf(added)))),3)
            f.s=f.s.copy(orders=listOf(removed))
            withContext(Dispatchers.Main) {vm.refresh()};ready(vm)
            assertEquals(listOf("p2"),vm.state.value.form!!.lines.map {it.product.id})
            assertEquals("20.00",vm.state.value.form!!.lines.single().total)
            assertEquals("TT84",vm.state.value.form!!.invoiceNumber)
            withContext(Dispatchers.Main) {vm.save()};ready(vm)
            withContext(Dispatchers.Main) {vm.close();vm.open("o")};ready(vm)
            assertEquals(listOf("p2"),vm.state.value.form!!.lines.map {it.product.id})
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun operatorNextSavesBeforePreviewAndSaveExitPreservesInvoice(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            com.mendelev.mpos.core.access.Administration.session.logout()
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            assertTrue(vm.state.value.canEdit)
            withContext(Dispatchers.Main) {vm.open("o")};ready(vm)
            withContext(Dispatchers.Main) {vm.line("p") {it.copy(total="40")};vm.next()}
            withTimeout(20_000) {vm.state.first {it.preview!=null && !it.pending}}
            assertFalse(vm.state.value.dirty);assertEquals("40",vm.state.value.saved!!.lines.single().total)
            withContext(Dispatchers.Main) {vm.dismissPreview();vm.change {it.copy(invoiceNumber="сотрудник")};vm.saveAndExit()}
            withTimeout(20_000) {vm.state.first {it.form==null && !it.pending}}
            assertEquals("сотрудник",f.s.records.single().draft!!.invoiceNumber)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun openingIsDurableAndLostSaveReplyLocksSameCommandUntilRecovery(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.open("o")};assertFalse(ready(vm).dirty);assertEquals(1,f.calls)
            f.lose=true
            withContext(Dispatchers.Main) {vm.line("p") {it.copy(total="40")};vm.save();vm.save()}
            val first=ready(vm);assertNotNull(first.unresolved);assertEquals(2,f.calls)
            withContext(Dispatchers.Main) {vm.clear();vm.close();vm.discard();vm.line("p") {it.copy(quantity="99")}}
            assertEquals(first.form,vm.state.value.form);assertEquals(first.unresolved,vm.state.value.unresolved)
            f.failOutcome=false;withContext(Dispatchers.Main) {vm.retry()};val saved=ready(vm)
            assertNull(saved.unresolved);assertFalse(saved.blocked);assertFalse(saved.dirty);assertEquals("40",saved.form!!.lines.single().total);assertEquals(2,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun knownSaveReadFailureAndDirtyRefreshDoNotLoseInput(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.open("o")};ready(vm);f.failRead=true
            withContext(Dispatchers.Main) {vm.line("p") {it.copy(total="40")};vm.save()};val saved=ready(vm)
            assertNull(saved.unresolved);assertTrue(saved.blocked);assertFalse(saved.dirty);assertTrue(saved.message!!.startsWith("Изменение сохранено"))
            f.failRead=false;withContext(Dispatchers.Main) {vm.refresh()};assertFalse(ready(vm).blocked)
            withContext(Dispatchers.Main) {vm.preview()};assertEquals("3",ready(vm).preview!!.lines.single().newCost)
            withContext(Dispatchers.Main) {vm.line("p") {it.copy(quantity="9")};vm.refresh()}
            assertNull(vm.state.value.preview);assertEquals("9",vm.state.value.form!!.lines.single().quantity);assertEquals(2,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun stockRebaseKeepsFactsButAnotherDraftRevisionCannotBeAdopted(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.open("o")};ready(vm)
            withContext(Dispatchers.Main) {vm.line("p") {it.copy(total="40")}}
            f.s=f.s.copy(products=listOf(f.p.copy(stock="8",stockRevision=2)))
            withContext(Dispatchers.Main) {vm.preview()};assertNull(ready(vm).preview)
            withContext(Dispatchers.Main) {vm.rebase()};val rebased=ready(vm)
            assertEquals("40",rebased.form!!.lines.single().total);assertEquals("10",rebased.form.lines.single().quantity);assertEquals("8",rebased.form.lines.single().product.stock)
            f.s=f.s.copy(records=f.s.records.map {it.copy(revision=2)})
            withContext(Dispatchers.Main) {vm.rebase()};val conflict=ready(vm)
            assertEquals(1L,conflict.snapshot!!.records.single().revision);assertEquals(rebased.form,conflict.form);assertEquals(1,f.calls)
            withContext(Dispatchers.Main) {vm.line("p") {it.copy(total="-1")};vm.save()}
            assertNull(vm.state.value.unresolved);assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
}
