package com.mendelev.mpos

import androidx.lifecycle.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.feature.backup.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BackupViewModelTest {
    private suspend fun make(store: ViewModelStore,access: BackupInspectionAccess,native: NativeBackupAccess?=null)=withContext(Dispatchers.Main) {
        ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>)=BackupViewModel(access,native) as T
        })[BackupViewModel::class.java].also {it.open()}
    }
    @Test fun stalePickerCannotBorrowNewLoginAndErrorsNeverExposeSourceContent()=runBlocking {
        var calls=0;val store=ViewModelStore()
        val model=make(store,BackupInspectionAccess {calls++;error("SENTINEL SOURCE CONTENT")})
        try {
            withContext(Dispatchers.Main) {assertTrue(model.beginSelection())}
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open();assertFalse(model.beginSelection());model.selected("old-uri")}
            assertEquals(0,calls)
            withContext(Dispatchers.Main) {assertTrue(model.beginSelection());model.selected("new-uri")}
            withTimeout(20_000) {while(model.state.value.error==null) delay(10)}
            assertFalse(model.state.value.error!!.contains("SENTINEL"));assertNull(model.state.value.preview)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun lateReadAfterLogoutCannotPublishFinancialCounts()=runBlocking {
        val store=ViewModelStore();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val preview=WebBackupReader.read(backupSourceFixture().byteInputStream()).preview
        val model=make(store,BackupInspectionAccess {withContext(NonCancellable) {entered.complete(Unit);release.await()};preview})
        try {
            withContext(Dispatchers.Main) {assertTrue(model.beginSelection());model.selected("uri")};entered.await()
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open()};release.complete(Unit);delay(100)
            assertNull(model.state.value.preview);assertFalse(model.state.value.busy)
        } finally {release.complete(Unit);withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun nativeChooserKindOldAuthorityAndFailedExportNeverReportSuccess()=runBlocking {
        var calls=0;val store=ViewModelStore()
        val native=object: NativeBackupAccess {
            override suspend fun export(uri: String): NativeBackupReport {calls++;error("SECRET URI $uri")}
            override suspend fun inspectNative(uri: String)=NativeBackupReport(10,"a".repeat(64),0,1,1,0)
        }
        val model=make(store,BackupInspectionAccess {error("unused")},native)
        try {
            withContext(Dispatchers.Main) {assertTrue(model.beginExport());model.nativeSelected("wrong-callback");assertTrue(model.state.value.picking);assertFalse(model.beginNativeInspection())}
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open();assertFalse(model.beginExport());model.exportSelected("old");assertTrue(model.beginExport());model.exportSelected("sensitive-new")}
            withTimeout(20_000) {while(model.state.value.error==null) delay(10)}
            assertEquals(1,calls);assertNull(model.state.value.native);assertFalse(model.state.value.error!!.contains("sensitive"))
            withContext(Dispatchers.Main) {assertTrue(model.beginNativeInspection());model.nativeSelected("new")}
            withTimeout(20_000) {while(model.state.value.native==null) delay(10)}
            assertFalse(model.state.value.native!!.exported)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun lateNativeExportAfterLogoutCannotPublishSuccess()=runBlocking {
        val store=ViewModelStore();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val native=object: NativeBackupAccess {
            override suspend fun export(uri: String): NativeBackupReport {withContext(NonCancellable) {entered.complete(Unit);release.await()};return NativeBackupReport(10,"a".repeat(64),0,1,1,0)}
            override suspend fun inspectNative(uri: String)=error("unused")
        }
        val model=make(store,BackupInspectionAccess {error("unused")},native)
        try {
            withContext(Dispatchers.Main) {assertTrue(model.beginExport());model.exportSelected("uri")};entered.await()
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open()};release.complete(Unit);delay(100)
            assertNull(model.state.value.native);assertFalse(model.state.value.busy)
        } finally {release.complete(Unit);withContext(Dispatchers.Main) {store.clear()}}
    }

}
