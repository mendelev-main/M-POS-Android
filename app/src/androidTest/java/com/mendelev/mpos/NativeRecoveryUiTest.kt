package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.feature.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

internal fun recoveryUiFixture(): PreparedNativeRestore {
    val report=NativeBackupReport(12345,"a".repeat(64),1,1,1,0)
    val protection=ProtectedNativeCopy("00000000-0000-0000-0000-000000000001",1793577600000,report,"b".repeat(64),NativeSnapshotActivity(1,1,3000))
    return PreparedNativeRestore("00000000-0000-0000-0000-000000000002",report.copy(products=2),NativeSnapshotActivity(1,1,4000),protection)
}
class NativeRecoveryUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun sourceAndProtectionStaySeparateAndFreshModelCanReadRetainedCopies() {
        val prepared=recoveryUiFixture()
        val recovery=object: NativeRecoveryAccess {
            override suspend fun prepare(uri: String)=prepared
            override suspend fun inventory()=NativeRecoveryInventory(listOf(prepared.protection),listOf(prepared),0)
        }
        val store=ViewModelStore();lateinit var model: BackupViewModel
        val active=androidx.compose.runtime.mutableStateOf<BackupViewModel?>(null)
        fun make()=ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>)=BackupViewModel(BackupInspectionAccess {error("unused")},recoveryAccess=recovery) as T
        })[BackupViewModel::class.java].also {it.open()}
        compose.runOnIdle {model=make();active.value=model}
        compose.setContent {MposTheme {active.value?.let {BackupScreen(it,{},{})}}}
        compose.onNodeWithTag("recoveryPrepare").assertIsEnabled()
        compose.runOnIdle {check(model.beginPreparation());model.preparationSelected("fixture-uri")}
        compose.waitUntil(20_000) {model.state.value.prepared!=null}
        compose.onNodeWithText("Выбранная копия — отмечено оплаченным: 40,00 BYN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Защитная копия текущих данных — отмечено оплаченным: 30,00 BYN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Восстановить").assertDoesNotExist()
        val i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap=checkNotNull(i.uiAutomation.takeScreenshot());val file=java.io.File(i.targetContext.filesDir,"qa/native-protection.png")
        file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        compose.runOnIdle {store.clear();model=make();active.value=model}
        compose.onNodeWithTag("recoveryPrepared").assertDoesNotExist()
        compose.runOnIdle {model.loadRecovery()}
        compose.waitUntil(20_000) {model.state.value.recovery!=null}
        compose.onNodeWithTag("recoveryInventoryResult").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Проверено: 1 · Подготовлено восстановлений: 1").assertIsDisplayed()
        runBlocking {Administration.session.logout()}
        compose.waitUntil(20_000) {model.state.value.recovery==null}
        compose.onNodeWithTag("recoveryInventoryResult").assertDoesNotExist();compose.onNodeWithTag("recoveryActions").assertDoesNotExist()
        compose.runOnIdle {store.clear()}
    }
}
