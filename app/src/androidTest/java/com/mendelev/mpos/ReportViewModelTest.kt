package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.SalesReportAccess
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.feature.report.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReportViewModelTest {
    private val clock=Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"),ZoneId.of("Europe/Minsk"))
    private suspend fun waitFor(test: ()->Boolean)=withTimeout(20_000) {while(!test()) delay(10)}
    private suspend fun create(store: ViewModelStore,access: SalesReportAccess,saved: SavedStateHandle=SavedStateHandle())=withContext(Dispatchers.Main) {
        ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>)=ReportViewModel(access,saved,clock) as T
        })[ReportViewModel::class.java]
    }
    @Test fun newestPeriodWinsEvenIfOldReadIgnoresCancellationAndFailureCanRetry(): Unit=runBlocking {
        val store=ViewModelStore();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        var calls=0;var fail=false
        val access=object: SalesReportAccess {override suspend fun snapshot(period: ReportPeriod): SalesReport {
            val number=++calls
            if(number==1) withContext(NonCancellable) {entered.complete(Unit);release.await()}
            if(fail) error("read fault")
            return SalesReports.build(period,clock.millis(),emptyList(),emptyList())
        }}
        try {
            val model=create(store,access);withContext(Dispatchers.Main) {model.open()};entered.await()
            withContext(Dispatchers.Main) {model.preset(7)};waitFor {model.state.value.report?.period?.from==LocalDate.parse("2026-10-25")}
            release.complete(Unit);delay(100)
            assertEquals(LocalDate.parse("2026-10-25"),model.state.value.report!!.period.from)
            fail=true;withContext(Dispatchers.Main) {model.refresh()};waitFor {!model.state.value.pending}
            assertNull(model.state.value.report);assertNotNull(model.state.value.error)
            fail=false;withContext(Dispatchers.Main) {model.refresh()};waitFor {model.state.value.report!=null};assertNull(model.state.value.error)
        } finally {release.complete(Unit);withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun logoutAndReloginNeverPublishOldFinancialRead(): Unit=runBlocking {
        val store=ViewModelStore();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val access=object: SalesReportAccess {override suspend fun snapshot(period: ReportPeriod): SalesReport {
            withContext(NonCancellable) {entered.complete(Unit);release.await()}
            return SalesReports.build(period,clock.millis(),emptyList(),emptyList())
        }}
        try {
            val model=create(store,access);withContext(Dispatchers.Main) {model.open()};entered.await()
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            release.complete(Unit);delay(200);assertNull(model.state.value.report)
            withContext(Dispatchers.Main) {model.open()};waitFor {model.state.value.report!=null}
            Administration.session.logout();waitFor {model.state.value.report==null};assertNull(model.state.value.error)
        } finally {release.complete(Unit);withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun invalidDatesPreserveAppliedPeriodAndSavedStateNeverRestoresMoneyWhileLocked(): Unit=runBlocking {
        var calls=0;val access=object: SalesReportAccess {override suspend fun snapshot(period: ReportPeriod): SalesReport {calls++;return SalesReports.build(period,clock.millis(),emptyList(),emptyList())}}
        val saved=SavedStateHandle();val first=ViewModelStore();val second=ViewModelStore()
        try {
            val model=create(first,access,saved);withContext(Dispatchers.Main) {model.open();model.preset(30)}
            waitFor {model.state.value.report!=null};val before=model.state.value.report
            withContext(Dispatchers.Main) {assertFalse(model.dates("2026-11-01","2026-10-31"))}
            assertEquals(before,model.state.value.report);assertNotNull(model.state.value.filterError)
            assertEquals(setOf("reportFrom","reportThrough","reportZone"),saved.keys())
            Administration.session.logout();withContext(Dispatchers.Main) {first.clear()}
            val count=calls;val restored=create(second,access,SavedStateHandle(saved.keys().associateWith {saved.get<Any?>(it)}))
            withContext(Dispatchers.Main) {restored.open()};delay(100)
            assertEquals(count,calls);assertNull(restored.state.value.report);assertEquals("2026-10-02",restored.state.value.from)
        } finally {withContext(Dispatchers.Main) {first.clear();second.clear()}}
    }
}
