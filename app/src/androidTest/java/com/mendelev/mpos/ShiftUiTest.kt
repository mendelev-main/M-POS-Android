package com.mendelev.mpos

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
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
            WorkspaceRepository(CatalogDatabase.get(compose.activity.applicationContext)).initialize();repo.initialize()
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
    }
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun screenshot(name: String) {
        compose.waitForIdle();val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
