package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.suppliers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SupplierViewModelTest {
    private class Fake: SupplierAccess {
        var rows=emptyList<Supplier>();var failRead=false;var failOutcome=false;var lose=false;var calls=0
        val results=mutableMapOf<String,String>();var barrier: CompletableDeferred<Unit>?=null
        override suspend fun snapshot(): SupplierSnapshot {if(failRead) error("read");return SupplierSnapshot("owner",rows,emptyList())}
        override suspend fun outcome(command: SupplierCommand): String? {if(failOutcome) error("outcome");return results[command.commandId]}
        override suspend fun commit(command: SupplierCommand): String {
            calls++;barrier?.await()
            rows=SupplierRules.apply(rows,command,emptyMap());val id=(command.action as SupplierAction.Save).id
            results[command.commandId]=id
            if(lose) {failOutcome=true;error("lost")}
            return id
        }
    }
    private suspend fun ready(vm: SupplierViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    private fun model(store: ViewModelStore,fake: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=SupplierViewModel(fake) as T
    })[SupplierViewModel::class.java]
    @Test fun lostReplyLocksEditorAndRecoversOriginalCommandWithoutDuplicate(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            f.lose=true
            withContext(Dispatchers.Main) {vm.edit();vm.change {it.copy(name="Поставщик")};vm.save()}
            val first=ready(vm)
            assertNotNull(first.unresolved)
            assertTrue(first.blocked);val command=first.unresolved
            withContext(Dispatchers.Main) {vm.dismiss();vm.edit();vm.save();vm.change {it.copy(name="Подмена")}}
            assertEquals(command,vm.state.value.unresolved);assertEquals(1,f.calls)
            f.failOutcome=false
            withContext(Dispatchers.Main) {vm.retry()};val saved=ready(vm)
            assertNull(saved.unresolved);assertNull(saved.form);assertFalse(saved.blocked);assertEquals(1,f.calls);assertEquals(1,f.rows.size)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun invalidInputDoesNotBecomeUnknownAndDoubleClickIsSingleCommand(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.edit();vm.save()}
            assertNull(vm.state.value.unresolved);assertFalse(vm.state.value.blocked);assertEquals(0,f.calls)
            f.barrier=CompletableDeferred()
            withContext(Dispatchers.Main) {vm.change {it.copy(name="Поставщик")};vm.save();vm.save()}
            assertTrue(vm.state.value.pending);f.barrier!!.complete(Unit)
            assertNull(ready(vm).form);assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun savedResultRemainsKnownWhenReloadFails(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.edit();vm.change {it.copy(name="Поставщик")}}
            f.failRead=true
            withContext(Dispatchers.Main) {vm.save()};val saved=ready(vm)
            assertNull(saved.form);assertNull(saved.unresolved);assertTrue(saved.blocked);assertTrue(saved.message!!.startsWith("Изменение сохранено"))
            f.failRead=false
            withContext(Dispatchers.Main) {vm.refresh()};assertEquals(1,ready(vm).snapshot!!.suppliers.size)
            assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
}
