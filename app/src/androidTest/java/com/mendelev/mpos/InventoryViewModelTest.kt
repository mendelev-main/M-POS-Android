package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class InventoryViewModelTest {
    private class Fake: InventoryAccess {
        val p=InventoryProduct("p","Молоко","Сырьё","l",1,1,"10","2")
        var s=InventorySnapshot("owner",0,InventoryDocument(),listOf(p),"2026-10-27","runtime")
        var calls=0;var lose=false;var unknown=false;var readFail=false
        val results=mutableMapOf<String,String>()
        override suspend fun snapshot(): InventorySnapshot {if(readFail) error("read");return s}
        override suspend fun outcome(c: InventoryCommand): String? {if(unknown) error("outcome");return results[c.commandId]}
        override suspend fun commit(c: InventoryCommand): String {
            calls++
            s=s.copy(revision=s.revision+1,document=when(val a=c.action) {is InventoryAction.Configure->s.document.copy(config=a.config);is InventoryAction.Start->s.document.copy(draft=InventoryDraft(a.id,a.type,1,s.today,listOf(p)));is InventoryAction.Count->s.document.copy(draft=s.document.draft!!.copy(counts=s.document.draft!!.counts.filterNot {it.product.id==a.product.id}+InventoryCount(a.product,a.raw,a.generation)));is InventoryAction.Fix->error("not used")})
            val id=s.document.draft?.id ?: InventoryCodec.KEY;results[c.commandId]=id
            if(lose) {unknown=true;error("reply")};return id
        }
    }
    private fun model(store: ViewModelStore,f: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=com.mendelev.mpos.feature.inventory.InventoryViewModel(f) as T
    })[com.mendelev.mpos.feature.inventory.InventoryViewModel::class.java]
    private suspend fun ready(vm: com.mendelev.mpos.feature.inventory.InventoryViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    @Test fun dirtySelectionCannotStartAndDiscardRestoresSavedConfiguration(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.change {it.copy(productIds=setOf("p"))};vm.start("adhoc")}
            assertTrue(vm.state.value.dirty);assertEquals(0,f.calls)
            withContext(Dispatchers.Main) {vm.save()};assertFalse(ready(vm).dirty);assertTrue(vm.state.value.form!!.enabled)
            withContext(Dispatchers.Main) {vm.change {it.copy(frequency="weekly")};vm.discard();vm.start("adhoc")}
            assertNotNull(ready(vm).snapshot!!.document.draft);assertEquals("monthly",vm.state.value.form!!.frequency);assertEquals(2,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun unknownSaveLocksEditsAndRetryResolvesOriginalCommandWithoutSecondWrite(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm);f.lose=true
            withContext(Dispatchers.Main) {vm.change {it.copy(productIds=setOf("p"))};vm.save()};val before=ready(vm)
            assertNotNull(before.unresolved)
            withContext(Dispatchers.Main) {vm.change {it.copy(frequency="quarterly")};vm.discard();vm.refresh();vm.start("adhoc")}
            assertEquals(before.form,vm.state.value.form);assertEquals(1,f.calls)
            f.unknown=false;withContext(Dispatchers.Main) {vm.retry()};assertNull(ready(vm).unresolved);assertEquals(1,f.calls);assertFalse(vm.state.value.dirty)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun successfulWriteWithFailedReadDoesNotRemainUnknownOrResubmit(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm);f.readFail=true
            withContext(Dispatchers.Main) {vm.change {it.copy(productIds=setOf("p"))};vm.save()}
            val s=ready(vm);assertNull(s.unresolved);assertTrue(s.blocked);assertNull(s.form);assertEquals(1,f.calls)
            f.readFail=false;withContext(Dispatchers.Main) {vm.refresh()};assertFalse(ready(vm).blocked);assertEquals(1,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun openingCountReadsFreshBaselineAndStartsBlankWhileRecreationKeepsForm(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.change {it.copy(productIds=setOf("p"))};vm.save()};ready(vm)
            withContext(Dispatchers.Main) {vm.start("adhoc")};ready(vm)
            f.s=f.s.copy(products=listOf(f.p.copy(stock="8",stockRevision=2)))
            withContext(Dispatchers.Main) {vm.openCount("p")};val form=ready(vm).countForm!!
            assertEquals("8",form.product.stock);assertEquals("",form.raw)
            withContext(Dispatchers.Main) {vm.countRaw("0,0004")}
            assertEquals("0,0004",withContext(Dispatchers.Main) {model(store,f)}.state.value.countForm!!.raw)
            withContext(Dispatchers.Main) {vm.saveCount()};val saved=ready(vm);assertNull(saved.countForm);assertEquals("0,0004",saved.snapshot!!.document.draft!!.counts.single().raw)
            withContext(Dispatchers.Main) {vm.openCount("p")};assertEquals("",ready(vm).countForm!!.raw)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun unknownCountSaveLocksRawRecountCloseConfigAndResolvesSameCommand(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.change {it.copy(productIds=setOf("p"))};vm.save()};ready(vm)
            withContext(Dispatchers.Main) {vm.start("adhoc")};ready(vm)
            withContext(Dispatchers.Main) {vm.openCount("p")};ready(vm);f.lose=true
            withContext(Dispatchers.Main) {vm.countRaw("0.0004");vm.saveCount()};val unknown=ready(vm);assertNotNull(unknown.unresolved)
            withContext(Dispatchers.Main) {vm.countRaw("1");vm.closeCount(true);vm.recount();vm.change {it.copy(frequency="weekly")};vm.fix("p")}
            assertEquals(unknown.countForm,vm.state.value.countForm);assertEquals(3,f.calls)
            f.unknown=false;withContext(Dispatchers.Main) {vm.retry()};assertNull(ready(vm).countForm);assertEquals(3,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun emptyCountCannotSaveAndClosingNonemptyInputRequiresExplicitDiscard(): Unit=runBlocking {
        val store=ViewModelStore();val f=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,f)};ready(vm)
            withContext(Dispatchers.Main) {vm.change {it.copy(productIds=setOf("p"))};vm.save()};ready(vm)
            withContext(Dispatchers.Main) {vm.start("adhoc")};ready(vm)
            withContext(Dispatchers.Main) {vm.openCount("p")};ready(vm)
            withContext(Dispatchers.Main) {vm.saveCount()};assertEquals(2,f.calls);assertNotNull(vm.state.value.message)
            withContext(Dispatchers.Main) {vm.countRaw("0");vm.closeCount()};assertTrue(vm.state.value.countForm!!.discarding)
            withContext(Dispatchers.Main) {vm.continueCount();vm.closeCount(true)};assertNull(vm.state.value.countForm);assertEquals(2,f.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }

}
