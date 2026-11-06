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
    private suspend fun make(store: ViewModelStore,access: BackupInspectionAccess,native: NativeBackupAccess?=null,recovery: NativeRecoveryAccess?=null,web: WebRestoreAccess?=null)=withContext(Dispatchers.Main) {
        ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>)=BackupViewModel(access,native,recovery,web) as T
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

    @Test fun oldRecoveryChooserCannotBorrowAuthorityAndInventoryErrorsAreSanitized()=runBlocking {
        val store=ViewModelStore();var calls=0
        val recovery=object: NativeRecoveryAccess {
            override suspend fun prepare(uri: String): PreparedNativeRestore {calls++;error("SECRET $uri")}
            override suspend fun inventory(): NativeRecoveryInventory=error("SECRET PATH")
        }
        val model=make(store,BackupInspectionAccess {error("unused")},recovery=recovery)
        try {
            withContext(Dispatchers.Main) {assertTrue(model.beginPreparation());model.selected("wrong-kind");assertTrue(model.state.value.picking)}
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open();assertFalse(model.beginPreparation());model.preparationSelected("old");assertTrue(model.beginPreparation());model.preparationSelected("private-new")}
            withTimeout(20_000) {while(model.state.value.error==null) delay(10)}
            assertEquals(1,calls);assertNull(model.state.value.prepared);assertFalse(model.state.value.error!!.contains("private"))
            withContext(Dispatchers.Main) {model.loadRecovery()}
            withTimeout(20_000) {while(model.state.value.error==null) delay(10)}
            assertFalse(model.state.value.error!!.contains("SECRET"));assertNull(model.state.value.recovery)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun latePreparedCopyNeedsExplicitInventoryReadInNewSession()=runBlocking {
        val store=ViewModelStore();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val prepared=recoveryUiFixture()
        val recovery=object: NativeRecoveryAccess {
            override suspend fun prepare(uri: String): PreparedNativeRestore {withContext(NonCancellable) {entered.complete(Unit);release.await()};return prepared}
            override suspend fun inventory()=NativeRecoveryInventory(listOf(prepared.protection),listOf(prepared),0)
        }
        val model=make(store,BackupInspectionAccess {error("unused")},recovery=recovery)
        try {
            withContext(Dispatchers.Main) {assertTrue(model.beginPreparation());model.preparationSelected("uri")};entered.await()
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open()};release.complete(Unit);delay(100)
            assertNull(model.state.value.prepared);assertNull(model.state.value.recovery)
            withContext(Dispatchers.Main) {model.loadRecovery()}
            withTimeout(20_000) {while(model.state.value.recovery==null) delay(10)}
            assertEquals(listOf(prepared),model.state.value.recovery!!.prepared)
        } finally {release.complete(Unit);withContext(Dispatchers.Main) {store.clear()}}
    }

    @Test fun oldApplyConfirmationCannotBorrowNewLogin()=runBlocking {
        val store=ViewModelStore();var calls=0;val prepared=recoveryUiFixture()
        val recovery=object: NativeRecoveryAccess {
            override val activationEnabled=true
            override suspend fun prepare(uri: String)=prepared
            override suspend fun inventory()=NativeRecoveryInventory(listOf(prepared.protection),listOf(prepared),0)
            override suspend fun apply(id: String,confirmed: Boolean,activeConfirmed: Boolean): com.mendelev.mpos.data.recovery.NativeActivationResult {calls++;return com.mendelev.mpos.data.recovery.NativeActivationResult("new")}
        }
        val model=make(store,BackupInspectionAccess {error("unused")},recovery=recovery)
        try {
            withContext(Dispatchers.Main) {model.requestApply(prepared)}
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open();model.confirmApply(true)}
            delay(100);assertEquals(0,calls);assertNull(model.state.value.confirmation);assertFalse(model.state.value.activated)
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }
    @Test fun activationFailureDoesNotClaimReplacementOrLeakExceptionPayload()=runBlocking {
        val store=ViewModelStore();val prepared=recoveryUiFixture()
        val recovery=object: NativeRecoveryAccess {
            override val activationEnabled=true
            override suspend fun prepare(uri: String)=prepared
            override suspend fun inventory()=NativeRecoveryInventory(listOf(prepared.protection),listOf(prepared),0)
            override suspend fun apply(id: String,confirmed: Boolean,activeConfirmed: Boolean): com.mendelev.mpos.data.recovery.NativeActivationResult=throw BackupFailure("staleTarget","SECRET SOURCE")
        }
        val model=make(store,BackupInspectionAccess {error("unused")},recovery=recovery)
        try {
            withContext(Dispatchers.Main) {model.requestApply(prepared);model.confirmApply(true)}
            withTimeout(20_000) {while(model.state.value.error==null) delay(10)}
            assertFalse(model.state.value.activated);assertNull(model.state.value.prepared);assertFalse(model.state.value.error!!.contains("SECRET"));assertTrue(model.state.value.error!!.contains("изменились"))
        } finally {withContext(Dispatchers.Main) {store.clear()}}
    }

    @Test fun webPreparationRequiresExplicitConfirmationAndLateResultCannotBorrowLogin()=runBlocking {
        val store=ViewModelStore();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var prepares=0
        val source=WebBackupReader.read(backupSourceFixture().byteInputStream()).preview
        val access=object: WebRestoreAccess {
            override suspend fun inspect(uri: String)=WebRestorePreview(source,"candidate","hash")
            override suspend fun prepare(preview: WebRestorePreview,unsupportedConfirmed: Boolean): PreparedNativeRestore {
                assertTrue(unsupportedConfirmed);prepares++
                withContext(NonCancellable) {entered.complete(Unit);release.await()}
                val report=NativeBackupReport(1,"a".repeat(64),0,1,1,0)
                return PreparedNativeRestore("prepared",report,NativeSnapshotActivity(1,1,4000),ProtectedNativeCopy("protection",1,report,"b".repeat(64),NativeSnapshotActivity(1,1,3000)))
            }
        }
        val model=make(store,BackupInspectionAccess {error("inspection fallback must not run")},web=access)
        try {
            withContext(Dispatchers.Main) {assertTrue(model.beginSelection());model.selected("source")}
            withTimeout(20_000) {while(model.state.value.web==null)delay(10)}
            withContext(Dispatchers.Main) {model.prepareWeb(false)};assertEquals(0,prepares)
            withContext(Dispatchers.Main) {model.prepareWeb(true)};entered.await()
            Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
            withContext(Dispatchers.Main) {model.open()};release.complete(Unit);delay(100)
            assertEquals(1,prepares);assertNull(model.state.value.prepared);assertNull(model.state.value.web)
        } finally {release.complete(Unit);withContext(Dispatchers.Main) {store.clear()}}
    }

}
