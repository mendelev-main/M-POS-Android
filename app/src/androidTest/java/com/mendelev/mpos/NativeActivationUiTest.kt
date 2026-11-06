package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.recovery.NativeActivationResult
import com.mendelev.mpos.feature.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NativeActivationUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun replacingActiveShiftRequiresAnExtraAcknowledgementAndLogoutClosesDialog() {
        val prepared=recoveryUiFixture();var applied=0
        val recovery=object: NativeRecoveryAccess {
            override val activationEnabled=true
            override suspend fun prepare(uri: String)=prepared
            override suspend fun inventory()=NativeRecoveryInventory(listOf(prepared.protection),listOf(prepared),0)
            override suspend fun apply(id: String,confirmed: Boolean,activeConfirmed: Boolean): NativeActivationResult {check(id==prepared.id && confirmed && activeConfirmed);applied++;return NativeActivationResult("new")}
        }
        val store=ViewModelStore();lateinit var model: BackupViewModel
        compose.runOnIdle {model=ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>)=BackupViewModel(BackupInspectionAccess {error("unused")},recoveryAccess=recovery) as T
        })[BackupViewModel::class.java];model.open()}
        compose.setContent {MposTheme {BackupScreen(model,{},{})}}
        compose.runOnIdle {check(model.beginPreparation());model.preparationSelected("fixture")}
        compose.waitUntil(20_000) {model.state.value.prepared!=null}
        compose.onNodeWithTag("recoveryApply").performScrollTo().performClick()
        compose.onNodeWithTag("recoveryConfirmApply").assertIsNotEnabled()
        compose.onNodeWithTag("recoveryActiveConfirmation").performClick()
        compose.onNodeWithTag("recoveryConfirmApply").assertIsEnabled()
        val i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();val bitmap=checkNotNull(i.uiAutomation.takeScreenshot())
        val file=java.io.File(i.targetContext.filesDir,"qa/native-restore-confirm.png");file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        runBlocking {Administration.session.logout()}
        compose.waitUntil(20_000) {model.state.value.confirmation==null}
        compose.onNodeWithTag("recoveryConfirmApply").assertDoesNotExist();assertEquals(0,applied)
        compose.runOnIdle {store.clear()}
    }
}
