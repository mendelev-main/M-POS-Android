package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AdminUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun wrongPasswordLoginRecreationAndLogoutPreserveCashierAndShift() {
        val db=CatalogDatabase.get(compose.activity.applicationContext)
        val before=runBlocking {ShiftRepository(db).initialize();ShiftRepository(db).snapshot()}
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("topNav-settings").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-settings").performClick()
        compose.onNodeWithTag("adminControl").performScrollTo().performClick()
        compose.waitUntil(10_000) {!Administration.session.state.value.active}
        compose.onNodeWithTag("adminControl").performClick()
        compose.onNodeWithTag("adminPassword").performTextInput("incorrect")
        compose.onNodeWithTag("adminLogin").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("Неверный пароль").fetchSemanticsNodes().isNotEmpty()}
        assertFalse(Administration.session.state.value.active)
        compose.onNodeWithTag("adminPassword").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        screenshot("admin-login")
        compose.onNodeWithTag("adminPassword").performTextInput("fixture-admin")
        compose.onNodeWithTag("adminLogin").performClick()
        compose.waitUntil(10_000) {Administration.session.state.value.active}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("adminStatus").assertTextEquals("Администрирование активно")
        screenshot("admin-session")
        compose.onNodeWithTag("adminControl").performScrollTo().performClick()
        compose.waitUntil(10_000) {!Administration.session.state.value.active}
        compose.onNodeWithTag("adminStatus").assertTextEquals("Режим кассира")
        assertEquals(before,runBlocking {ShiftRepository(db).snapshot()})
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(200,5_000)
        val bitmap=checkNotNull(automation.takeScreenshot())
        val file=java.io.File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
