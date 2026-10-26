package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.refund.*
import com.mendelev.mpos.feature.sale.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class RefundViewModelTest {
    private class Fake: RefundAccess {
        var rows=listOf<RefundEntity>();var historyFails=false;var outcomesFail=false;var loseResponse=false
        var commitFails=false;var calls=0;val commands=mutableListOf<String>();var barrier: CompletableDeferred<Unit>?=null
        override suspend fun history(): List<RefundEntity> {if(historyFails) error("read");return rows}
        override suspend fun preview(receiptId: String,actor: String)=RefundPreview(receiptId,"shift",2,actor,"token",RefundAmounts(2500,2500,0,500,-2000,0),emptyList())
        override suspend fun outcome(command: String): RefundEntity? {if(outcomesFail) error("unknown");return rows.firstOrNull {it.commandId==command}}
        override suspend fun commit(command: String,preview: RefundPreview): String {
            calls++;commands+=command;barrier?.await()
            if(commitFails) error("write")
            rows=listOf(RefundEntity("refund",preview.receiptId,command,"shift","owner",1,2500,2500,0,500,"hash","{}"))
            if(loseResponse) {outcomesFail=true;error("response lost")}
            return "refund"
        }
    }
    private fun model(store: ViewModelStore,fake: Fake)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=RefundViewModel(fake) as T
    })[RefundViewModel::class.java]
    private suspend fun ready(vm: RefundViewModel)=withTimeout(20_000) {vm.state.first {!it.pending}}
    @Test fun lostResponseBlocksNewCommandUntilSameOutcomeIsRecovered(): Unit=runBlocking {
        val store=ViewModelStore();val fake=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,fake)};ready(vm)
            withContext(Dispatchers.Main) {vm.prepare("receipt","owner")};val submitted=ready(vm).review!!
            fake.loseResponse=true
            withContext(Dispatchers.Main) {vm.confirm()};val uncertain=ready(vm)
            assertEquals(submitted,uncertain.unresolved);assertTrue(uncertain.blocked);assertNull(uncertain.result)
            withContext(Dispatchers.Main) {vm.dismiss();vm.refresh();vm.prepare("other","owner")}
            assertEquals(submitted,vm.state.value.unresolved);assertEquals(1,fake.calls)
            fake.outcomesFail=false
            withContext(Dispatchers.Main) {vm.confirm()};val saved=ready(vm)
            assertEquals("refund",saved.result!!.id);assertNull(saved.unresolved);assertEquals(1,fake.calls)
            assertEquals(listOf(submitted.command),fake.commands)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun failedWriteCanRetryAndPendingDoubleClickWritesOnce(): Unit=runBlocking {
        val store=ViewModelStore();val fake=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,fake)};ready(vm)
            withContext(Dispatchers.Main) {vm.prepare("receipt","owner")};ready(vm)
            fake.commitFails=true
            withContext(Dispatchers.Main) {vm.confirm()};val failed=ready(vm)
            assertNull(failed.result);assertNull(failed.unresolved);assertTrue(failed.history.isEmpty());assertNotNull(failed.message)
            fake.commitFails=false;fake.barrier=CompletableDeferred()
            withContext(Dispatchers.Main) {vm.prepare("receipt","owner")};ready(vm)
            withContext(Dispatchers.Main) {vm.confirm();vm.confirm();vm.prepare("other","owner")}
            assertTrue(vm.state.value.pending)
            fake.barrier!!.complete(Unit)
            assertNotNull(ready(vm).result);assertEquals(2,fake.calls);assertEquals(1,fake.rows.size)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun knownSavedResultSurvivesHistoryReadFailureAndReload(): Unit=runBlocking {
        val store=ViewModelStore();val fake=Fake()
        try {
            val vm=withContext(Dispatchers.Main) {model(store,fake)};ready(vm)
            withContext(Dispatchers.Main) {vm.prepare("receipt","owner")};ready(vm)
            fake.historyFails=true
            withContext(Dispatchers.Main) {vm.confirm()};val saved=ready(vm)
            assertNotNull(saved.result);assertTrue(saved.blocked);assertNull(saved.unresolved)
            withContext(Dispatchers.Main) {vm.dismiss();vm.prepare("other","owner")}
            assertNull(vm.state.value.review);assertEquals(1,fake.calls)
            fake.historyFails=false
            withContext(Dispatchers.Main) {vm.refresh()};val reloaded=ready(vm)
            assertFalse(reloaded.blocked);assertEquals(fake.rows,reloaded.history)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
}
