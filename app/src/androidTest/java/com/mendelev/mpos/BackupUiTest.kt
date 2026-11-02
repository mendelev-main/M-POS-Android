package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.feature.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class BackupUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun previewShowsPaidPartsWithoutRestoreActionAndLogoutRemovesIt() {
        val preview=WebBackupReader.read(backupSourceFixture().byteInputStream()).preview
        val store=ViewModelStore();lateinit var model: BackupViewModel
        compose.runOnIdle {model=ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>)=BackupViewModel(BackupInspectionAccess {preview}) as T
        })[BackupViewModel::class.java];model.open()}
        compose.setContent {MposTheme {BackupScreen(model,{},{})}}
        compose.runOnIdle {check(model.beginSelection());model.selected("fixture-uri")}
        compose.waitUntil(20_000) {model.state.value.preview!=null}
        compose.onNodeWithTag("backupPaid").performScrollTo().assertTextEquals("Отмечено оплаченным: 40,00 BYN")
        compose.onNodeWithText("Восстановить").assertDoesNotExist()
        val i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap=checkNotNull(i.uiAutomation.takeScreenshot());val file=java.io.File(i.targetContext.filesDir,"qa/backup-preview.png")
        file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        runBlocking {Administration.session.logout()}
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("backupLocked").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("backupPreview").assertDoesNotExist();compose.onNodeWithTag("backupPaid").assertDoesNotExist()
        compose.runOnIdle {store.clear()}
    }
}
