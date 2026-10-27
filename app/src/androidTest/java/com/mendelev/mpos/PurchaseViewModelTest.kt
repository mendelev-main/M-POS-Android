package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.purchasing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PurchaseViewModelTest {
    private class Fake: PurchaseAccess {
        var s=PurchaseSnapshot(0,"owner",null,listOf(Supplier("s","Поставка",1,setOf("p"))),listOf(PurchaseProduct("p","Молоко",1,"l","", "0","bottle","0.5")))
        var failRead=false;var failOutcome=false;var lose=false;var calls=0;var barrier: CompletableDeferred<Unit>?=null
        val results=mutableMapOf<String,String>()
        override suspend fun snapshot(): PurchaseSnapshot {if(failRead) error("read");return s}
        override suspend fun outcome(command: PurchaseCommand): String? {if(failOutcome) error("outcome");return results[command.commandId]}
        override suspend fun commit(command: PurchaseCommand): String {
            calls++;barrier?.await();val place=command.action as? PurchaseAction.Place
            val d=(command.action as? PurchaseAction.Save)?.draft
            s=s.copy(revision=s.revision+1,draft=d,orders=if(place==null) s.orders else s.orders+PurchaseOrderRules.create(place.orderId,place.draft,1));val id=place?.orderId ?: d?.id ?: "purchase-draft";results[command.commandId]=id
            if(lose) {failOutcome=true;error("lost")};return id
        }
    }
    private suspend fun ready(vm: PurchaseViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    private fun model(store: ViewModelStore,fake: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=PurchaseViewModel(fake) as T
    })[PurchaseViewModel::class.java]
    private fun edit(vm: PurchaseViewModel) {vm.supplier("s");vm.line("p") {it.copy(quantity="24",packSize="500",contentUnit="ml")}}
    @Test fun unknownResultLocksInputAndRecoversSameCommand(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm);f.lose=true
            withContext(Dispatchers.Main) {edit(vm);vm.save()};val first=ready(vm);assertNotNull(first.unresolved)
            withContext(Dispatchers.Main) {vm.clear();vm.discard();vm.line("p") {it.copy(quantity="99")};vm.save()}
            assertEquals(first.form,vm.state.value.form);assertEquals(1,f.calls)
            f.failOutcome=false;withContext(Dispatchers.Main) {vm.retry()}
            val saved=ready(vm);assertNull(saved.unresolved);assertFalse(saved.dirty);assertFalse(saved.blocked);assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun knownSaveSurvivesReadFailure(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm);f.failRead=true
            withContext(Dispatchers.Main) {edit(vm);vm.save()};val saved=ready(vm)
            assertNull(saved.unresolved);assertFalse(saved.dirty);assertTrue(saved.blocked);assertEquals("24",saved.form!!.lines.single().quantity)
            f.failRead=false;withContext(Dispatchers.Main) {vm.refresh()};assertFalse(ready(vm).blocked);assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun invalidInputAndReferenceRefreshNeverDiscardRawQuantities(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {edit(vm);vm.line("p") {it.copy(quantity="-1")};vm.save();vm.refresh()}
            assertEquals(0,f.calls);assertNull(vm.state.value.unresolved);assertEquals("-1",vm.state.value.form!!.lines.single().quantity)
            withContext(Dispatchers.Main) {vm.line("p") {it.copy(quantity="24")}}
            f.s=f.s.copy(products=f.s.products.map {it.copy(revision=2,name="Новое молоко",packSize="99")})
            withContext(Dispatchers.Main) {vm.revalidate()};val refreshed=ready(vm)
            assertEquals("500",refreshed.form!!.lines.single().packSize);assertEquals("ml",refreshed.form.lines.single().contentUnit)
            assertEquals(2L,refreshed.form.lines.single().productRevision)
            f.s=f.s.copy(revision=1)
            withContext(Dispatchers.Main) {vm.revalidate()};assertEquals(0L,ready(vm).snapshot!!.revision);assertEquals(0,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun placeRequiresSavedDraftAndLostReplyNeverDuplicatesOrder(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {edit(vm);vm.place()};assertEquals(0,f.calls)
            withContext(Dispatchers.Main) {vm.save()};ready(vm)
            f.lose=true;f.barrier=CompletableDeferred()
            withContext(Dispatchers.Main) {vm.place();vm.place()};assertTrue(vm.state.value.pending)
            f.barrier!!.complete(Unit);val first=ready(vm);assertNotNull(first.unresolved);assertEquals(2,f.calls)
            withContext(Dispatchers.Main) {vm.clear();vm.supplier("s")};assertEquals(first.form,vm.state.value.form)
            f.failOutcome=false;withContext(Dispatchers.Main) {vm.retry()};val saved=ready(vm)
            assertNull(saved.form);assertFalse(saved.dirty);assertFalse(saved.blocked);assertNotNull(saved.lastPlacedId)
            assertEquals(1,saved.snapshot!!.orders.size);assertEquals(2,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun knownPlacedOrderRemainsKnownWhenHistoryReadFails(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {edit(vm);vm.save()};ready(vm);f.failRead=true
            withContext(Dispatchers.Main) {vm.place()};val saved=ready(vm)
            assertNotNull(saved.lastPlacedId);assertNull(saved.unresolved);assertNull(saved.form);assertFalse(saved.dirty);assertTrue(saved.blocked)
            f.failRead=false;withContext(Dispatchers.Main) {vm.refresh()};assertEquals(1,ready(vm).snapshot!!.orders.size);assertEquals(2,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }

}
