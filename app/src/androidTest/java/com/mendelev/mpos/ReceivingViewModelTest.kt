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
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=ReceivingViewModel(f) as T
    })[ReceivingViewModel::class.java]
    private suspend fun ready(vm: ReceivingViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
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
