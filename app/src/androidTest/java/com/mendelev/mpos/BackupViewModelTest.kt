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
    private suspend fun make(store: ViewModelStore,access: BackupInspectionAccess)=withContext(Dispatchers.Main) {
        ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>)=BackupViewModel(access) as T
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
}
