package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.receiving.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class StandaloneReceivingViewModelTest {
    private class Fake: ReceivingAccess {
        val p=ReceivingProduct("p","Молоко","l",1,1,"10","2",false)
        val supplier=ReceivingSupplier("s","Поставка",1)
        var s=ReceivingSnapshot("owner",emptyList(),emptyList(),listOf(p),listOf(supplier))
        val results=mutableMapOf<String,String>();var calls=0;var lose=false;var failOutcome=false
        override suspend fun snapshot()=s
        override suspend fun outcome(c: ReceivingCommand): String? {if(failOutcome) error("outcome");return results[c.commandId]}
        override suspend fun commit(c: ReceivingCommand): String {
            calls++;s=s.copy(records=s.records.filterNot {it.orderId==c.orderId}+ReceivingRecord(c.orderId,c.revision+1,c.draft))
            val id=c.draft?.id ?: c.orderId;results[c.commandId]=id
            if(lose) {failOutcome=true;error("reply")};return id
        }
    }
    private fun model(store: ViewModelStore,f: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=ReceivingViewModel(f) as T
    })[ReceivingViewModel::class.java]
    private suspend fun ready(vm: ReceivingViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    @Test fun emptyOpenAddSupplierSaveResumeAndRemoveKeepStandaloneSlot(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.standalone()};val opened=ready(vm);val id=opened.form!!.id
            assertNull(opened.form.order);assertFalse(opened.dirty);assertEquals(1,f.calls)
            withContext(Dispatchers.Main) {vm.add("p");vm.add("p");vm.supplier("s");vm.line("p") {it.copy(quantity="10",total="40")}}
            assertEquals(1,vm.state.value.form!!.lines.size)
            withContext(Dispatchers.Main) {vm.save()};ready(vm)
            withContext(Dispatchers.Main) {vm.close();vm.standalone()};assertEquals(id,ready(vm).form!!.id);assertEquals(2,f.calls)
            withContext(Dispatchers.Main) {vm.remove("p");vm.discard()};assertEquals(1,vm.state.value.form!!.lines.size)
            withContext(Dispatchers.Main) {vm.remove("p");vm.save()};assertTrue(ready(vm).form!!.lines.isEmpty());assertEquals(3,f.calls)
            assertTrue(f.s.orders.isEmpty())
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun unknownStandaloneSaveLocksSupplierRowsAndSameCommandRetryWritesOnce(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.standalone()};ready(vm);f.lose=true
            withContext(Dispatchers.Main) {vm.add("p");vm.supplier("s");vm.save()};val unknown=ready(vm)
            assertNotNull(unknown.unresolved)
            withContext(Dispatchers.Main) {vm.supplier("");vm.remove("p");vm.add("p");vm.standalone();vm.clear();vm.close()}
            assertEquals(unknown.form,vm.state.value.form);assertEquals(2,f.calls)
            f.failOutcome=false;withContext(Dispatchers.Main) {vm.retry()};assertNull(ready(vm).unresolved);assertEquals(2,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun supplierAndStockRebaseRetainsFactsAndConcurrentSlotRevisionRefuses(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.standalone()};ready(vm)
            withContext(Dispatchers.Main) {vm.add("p");vm.supplier("s");vm.line("p") {it.copy(quantity="10",total="40")}}
            f.s=f.s.copy(products=listOf(f.p.copy(stock="8",stockRevision=2)),suppliers=listOf(f.supplier.copy(name="Поставка2",revision=2)))
            withContext(Dispatchers.Main) {vm.preview()};assertNull(ready(vm).preview)
            withContext(Dispatchers.Main) {vm.rebase()};val rebased=ready(vm)
            assertEquals("10",rebased.form!!.lines.single().quantity);assertEquals("40",rebased.form.lines.single().total);assertEquals("Поставка2",rebased.form.supplierName)
            withContext(Dispatchers.Main) {vm.preview()};assertEquals("18",ready(vm).preview!!.lines.single().newStock)
            f.s=f.s.copy(records=f.s.records.map {it.copy(revision=2)})
            withContext(Dispatchers.Main) {vm.rebase()};assertEquals(1L,ready(vm).snapshot!!.records.single().revision);assertEquals(rebased.form,vm.state.value.form)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
}
