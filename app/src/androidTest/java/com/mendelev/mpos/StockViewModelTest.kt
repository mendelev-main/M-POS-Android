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
}
