package com.mendelev.mpos

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.feature.shift.ShiftViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ShiftUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun closeDifferenceCarryAndRecreationPreserveCurrentAndParked() {
        val repo=ShiftRepository(CatalogDatabase.get(compose.activity.applicationContext));val suffix=UUID.randomUUID().toString()
        val documents=runBlocking {
            WorkspaceRepository(CatalogDatabase.get(compose.activity.applicationContext)).initialize();repo.initializeCashierFixture()
            val s=repo.snapshot();s.active?.let {repo.close("ui-clean-$suffix",it.id,s.revision,ShiftRules.token(s),0,s.ownerId)}
            CatalogDatabase.get(compose.activity.applicationContext).workspace().documents()
        }
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("topNav-shift").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-shift").performClick()
        lateinit var model: ShiftViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[ShiftViewModel::class.java]}
        compose.waitUntil(20_000) {!model.state.value.loading && model.state.value.snapshot?.active==null}
        compose.onNodeWithTag("shiftOpen").performClick();compose.onNodeWithTag("shiftConfirm").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.snapshot?.active!=null}
        compose.onNodeWithTag("shiftClose").performClick()
        compose.onNodeWithTag("shiftCounted").performTextReplacement("-1")
        hideKeyboard();compose.onNodeWithTag("shiftConfirm").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.message!=null}
        compose.onNodeWithTag("shiftCounted").assertExists();assertNotNull(model.state.value.snapshot!!.active)
        compose.onNodeWithTag("shiftCounted").performTextReplacement("140,00");hideKeyboard()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("shiftCounted").assertTextContains("140,00")
        compose.onNodeWithTag("shiftConfirm").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.snapshot?.last?.countedCash==14000L}
        compose.onNodeWithTag("shiftCountedSummary").assertTextEquals("Пересчитано: 140,00 BYN")
        screenshot("shift-closed")
        compose.onNodeWithTag("shiftOpen").performClick();compose.onNodeWithTag("shiftConfirm").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.snapshot?.active?.openingCash==14000L}
        compose.activityRule.scenario.recreate();compose.onNodeWithTag("shiftStatus").assertTextEquals("Смена открыта")
        assertEquals(documents,runBlocking {CatalogDatabase.get(compose.activity.applicationContext).workspace().documents()})
        val qa=File(compose.activity.filesDir,"qa").apply {mkdirs()};qa.resolve("shiftId.txt").writeText(model.state.value.snapshot!!.active!!.id)
        screenshot("shift-open")
        val cashBefore=runBlocking {ShiftCashRepository(CatalogDatabase.get(compose.activity.applicationContext)).history(model.state.value.snapshot!!.active!!.id).size}
        compose.onNodeWithTag("shiftDeposit").performScrollTo().performClick()
        compose.onNodeWithTag("shiftCashAmount").performTextInput("25,50")
        compose.onNodeWithTag("shiftCashNote").performTextInput("Размен");hideKeyboard()
        compose.activityRule.scenario.recreate();compose.onNodeWithTag("shiftCashAmount").assertTextContains("25,50")
        compose.onNodeWithTag("topNav-workspace").assertIsNotEnabled();screenshot("shift-cash-confirm")
        compose.onNodeWithTag("shiftCashSave").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.cashForm==null && model.state.value.snapshot?.currentCash==16550L}
        compose.onNodeWithTag("shiftWithdraw").performScrollTo().performClick()
        compose.onNodeWithTag("shiftCashAmount").performTextInput("999");hideKeyboard()
        compose.onNodeWithTag("shiftCashSave").performClick();compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.message!=null}
        compose.onNodeWithTag("shiftCashAmount").performTextReplacement("25,50");hideKeyboard()
        compose.onNodeWithTag("shiftCashSave").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.cashForm==null && model.state.value.snapshot?.currentCash==14000L && model.state.value.cashHistory.size==cashBefore+2}
        compose.onNodeWithTag("shiftCash:${model.state.value.cashHistory.first().id}").performScrollTo().assertIsDisplayed()
        screenshot("shift-cash-history")
        assertEquals(documents.filterNot {it.id==ShiftCashCodec.KEY},runBlocking {CatalogDatabase.get(compose.activity.applicationContext).workspace().documents()}.filterNot {it.id==ShiftCashCodec.KEY})

    }
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun screenshot(name: String) {
        hideKeyboard();compose.waitForIdle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(200,5_000)
        compose.waitUntil(10_000) {automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID}
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val bitmap=checkNotNull(automation.takeScreenshot())
        val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
