package com.mendelev.mpos

import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.feature.shift.ShiftViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ShiftCashViewModelTest {
    private class Fake(val delegate: ShiftCashAccess): ShiftCashAccess {
        @Volatile var lose=false;@Volatile var unknown=false;@Volatile var readFail=false;@Volatile var failReadAfterCommit=false;@Volatile var calls=0
        override suspend fun history(id: String): List<ShiftCashMovement> {if(readFail) error("read");return delegate.history(id)}
        override suspend fun outcome(c: ShiftCashCommand): String? {if(unknown) error("outcome");return delegate.outcome(c)}
        override suspend fun commit(c: ShiftCashCommand): String {calls++;val id=delegate.commit(c);if(failReadAfterCommit) readFail=true;if(lose) {unknown=true;error("reply")};return id}
    }
    private fun model(store: ViewModelStore,repo: ShiftRepository,cash: ShiftCashAccess)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=ShiftViewModel(repo,cash) as T
    })[ShiftViewModel::class.java]
    @Test fun unknownCashLocksInputAndRetryResolvesSameCommandOnce(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build();val store=ViewModelStore()
        try {
            val repo=ShiftRepository(db);repo.initialize();val s=repo.snapshot();repo.open("open",s.revision,null,s.ownerId)
            val cash=Fake(repo.cashAccess());val vm=withContext(Dispatchers.Main) {model(store,repo,cash)};withTimeout(20_000) {vm.state.first {!it.loading}}
            cash.lose=true;withContext(Dispatchers.Main) {vm.cashOpen("deposit");vm.cashEdit(amount="25,50",note="Размен");vm.cashSave()};withTimeout(20_000) {vm.state.first {!it.pending}}
            val old=vm.state.value;assertNotNull(old.cashUnresolved);assertEquals(1,cash.calls)
            withContext(Dispatchers.Main) {vm.cashEdit(amount="100");vm.cashClose();vm.cashOpen("withdrawal");vm.cashSave()};assertEquals(old.cashForm,vm.state.value.cashForm)
            cash.unknown=false;withContext(Dispatchers.Main) {vm.cashRetry()};withTimeout(20_000) {vm.state.first {!it.pending}}
            assertNull(vm.state.value.cashUnresolved);assertNull(vm.state.value.cashForm);assertEquals(2550L,vm.state.value.snapshot!!.currentCash);assertEquals(1,cash.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()};db.close()}
    }
    @Test fun knownCommitWithReadFailureDoesNotRemainUnknownOrResubmit(): Unit=runBlocking {
        repeat(10) {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build();val store=ViewModelStore()
        try {
            val repo=ShiftRepository(db);repo.initialize();val s=repo.snapshot();repo.open("open",s.revision,null,s.ownerId)
            val cash=Fake(repo.cashAccess());val vm=withContext(Dispatchers.Main) {model(store,repo,cash)};await(vm,"initial") {!it.loading}
            cash.failReadAfterCommit=true;withContext(Dispatchers.Main) {vm.cashOpen("deposit");vm.cashEdit(amount="10");vm.cashSave()};await(vm,"commit") {!it.pending}
            assertNull(vm.state.value.cashUnresolved);assertNull(vm.state.value.cashForm);assertTrue(vm.state.value.fatal)
            cash.readFail=false;withContext(Dispatchers.Main) {vm.refresh()}
            await(vm,"refresh") {!it.loading && !it.fatal}
            assertEquals(1000L,vm.state.value.snapshot!!.currentCash);assertEquals(1,cash.calls)
        } finally {withContext(Dispatchers.Main) {store.clear()};db.close()}
        }
    }
    private suspend fun await(vm: ShiftViewModel,phase: String,predicate: suspend (com.mendelev.mpos.feature.shift.ShiftUi)->Boolean) {
        try {withTimeout(20_000) {vm.state.first(predicate)}}
        catch(e: TimeoutCancellationException) {throw AssertionError("$phase did not finish: ${vm.state.value}",e)}
    }

}
