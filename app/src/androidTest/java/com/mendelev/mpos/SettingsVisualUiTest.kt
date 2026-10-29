package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsVisualUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun infoOpensUpdatesRecreationRestoresAndPlannedActionIsExplicit() {
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("topNav-settings").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        compose.onNodeWithTag("settingsUpdatesInfo").assertIsDisplayed()
        compose.onNodeWithTag("updatesWindow").assertDoesNotExist()
        screenshot("settings-home")
        compose.onNodeWithTag("settingsUpdatesInfo").performClick()
        compose.onNodeWithTag("updatesWindow").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("updatesWindow").assertExists()
        screenshot("settings-updates")
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun hasResource(node: android.view.accessibility.AccessibilityNodeInfo?,id: String): Boolean {
            if(node==null) return false
            if(node.viewIdResourceName==id) return true
            return (0 until node.childCount).any {hasResource(node.getChild(it),id)}
        }
        val oldFlags=automation.serviceInfo.flags
        automation.serviceInfo=automation.serviceInfo.apply {flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS}
        try {compose.waitUntil(10_000) {hasResource(automation.rootInActiveWindow,"updatesBack")}}
        finally {automation.serviceInfo=automation.serviceInfo.apply {flags=oldFlags}}
        compose.onNodeWithTag("updatesBack").performScrollTo().performClick()
        compose.onNodeWithTag("updatesWindow").assertDoesNotExist()
        compose.onNodeWithTag("settingsBackup").performScrollTo().performClick()
        compose.onNodeWithTag("settingsPlannedNotice").assertTextEquals("Будет реализовано")
        screenshot("settings-planned")
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithTag("settingsPlannedNotice").assertDoesNotExist()
        compose.onNodeWithTag("settingsAdminPanel").performScrollTo().performClick()
        screenshot("settings-admin")
        compose.onNodeWithContentDescription("Закрыть").performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation;automation.waitForIdle(200,5_000)
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val bitmap=checkNotNull(automation.takeScreenshot())
        val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
