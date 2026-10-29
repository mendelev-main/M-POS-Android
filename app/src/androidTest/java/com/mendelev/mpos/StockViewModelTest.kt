package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.feature.stock.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class StockViewModelTest {
    @Test fun failedReadDoesNotBecomeEmptySuccessfulSnapshotAndRetryRecovers(): Unit=runBlocking {
        var fail=false
        val access=StockReadAccess {id->if(fail) error("read");StockSnapshot(listOf(StockRow("a","Товар","Категория","piece","7",null,1)),id,emptyList(),1)}
        val vm=withContext(Dispatchers.Main) {StockViewModel(access)}
        withTimeout(20_000) {vm.state.first {!it.pending}}
        val before=vm.state.value.snapshot;fail=true
        withContext(Dispatchers.Main) {vm.load("a")};withTimeout(20_000) {vm.state.first {!it.pending}}
        assertNotNull(vm.state.value.error);assertEquals(before,vm.state.value.snapshot)
        fail=false;withContext(Dispatchers.Main) {vm.load("a")};withTimeout(20_000) {vm.state.first {!it.pending}}
        assertNull(vm.state.value.error);assertEquals("a",vm.state.value.snapshot!!.selectedId);assertEquals("7",vm.state.value.snapshot!!.rows.single().quantity)
    }
    @Test fun periodFiltersMovementTimeKeepsCurrentBalanceAndRejectsInvalidRange(): Unit=runBlocking {
        val access=StockReadAccess {id->StockSnapshot(listOf(StockRow("a","Товар","Категория","piece","7",null,1)),id,listOf(MovementEntity("old","a","1","sale","old",1),MovementEntity("now","a","-1","sale","now",172800001)),172800002,"UTC")}
        val vm=withContext(Dispatchers.Main) {StockViewModel(access)};withTimeout(20_000) {vm.state.first {!it.pending}}
        withContext(Dispatchers.Main) {vm.preset("today")};assertEquals(listOf("now"),vm.state.value.movements.map {it.id});assertEquals("7",vm.state.value.snapshot!!.rows.single().quantity)
        val before=vm.state.value.period
        withContext(Dispatchers.Main) {vm.period("2099-01-01","2099-01-02")}
        assertNotNull(vm.state.value.filterError);assertEquals(before,vm.state.value.period)
        withContext(Dispatchers.Main) {vm.period(null)};assertEquals(2,vm.state.value.movements.size)
    }
    @Test fun documentFailureRetriesSameLinkAndCloseRestoresHistoryContext(): Unit=runBlocking {
        var fail=true;val calls=mutableListOf<String>()
        val access=object: StockReadAccess {
            override suspend fun snapshot(id: String?)=StockSnapshot(emptyList(),id,listOf(MovementEntity("m","a","1","inventory","c",1,"d")),2,"UTC")
            override suspend fun document(id: String): StockDocument {calls+=id;if(fail) error("read");return StockDocument("История",1,listOf("Факт1"))}
        }
        val vm=withContext(Dispatchers.Main) {StockViewModel(access)};withTimeout(20_000) {vm.state.first {!it.pending}}
        withContext(Dispatchers.Main) {vm.openDocument("m")};withTimeout(20_000) {vm.state.first {!it.pending}}
        assertNotNull(vm.state.value.documentError);assertNull(vm.state.value.document)
        fail=false;withContext(Dispatchers.Main) {vm.openDocument("m")};withTimeout(20_000) {vm.state.first {!it.pending}}
        assertEquals(listOf("m","m"),calls);assertNotNull(vm.state.value.document)
        withContext(Dispatchers.Main) {vm.closeDocument()};assertNull(vm.state.value.documentId);assertEquals(listOf("m"),vm.state.value.movements.map {it.id})
    }

}
