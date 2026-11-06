package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Navigation acceptance without adding products, opening shifts or issuing financial commands. */
class TopNavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun allSourceTabsReachTheirRouteAndSurviveRecreation() {
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Добавить товар").fetchSemanticsNodes().isNotEmpty()}
        // The external APK-upgrade driver must target this control independently of employee names.
        compose.waitUntil(10_000) {
            fun exposed(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if(node==null) return false
                if(node.viewIdResourceName=="topNav-shift") return true
                return (0 until node.childCount).any {exposed(node.getChild(it))}
            }
            exposed(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
        }
        for(route in listOf("purchaseOrders","receiving","analytics","events")) {
            compose.onNodeWithTag("topNav-$route").performScrollTo().performClick()
            if(route=="events") compose.onNodeWithTag("topNav-$route").assertIsNotSelected()
            else compose.onNodeWithTag("topNav-$route").assertIsSelected()
            if(route=="purchaseOrders") compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("purchaseNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            else if(route=="receiving") compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("receivingNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            else if(route=="analytics") {
                compose.waitUntil(20_000) {compose.onAllNodesWithTag("reportNet").fetchSemanticsNodes().isNotEmpty()}
                compose.activityRule.scenario.recreate()
                compose.onNodeWithTag("topNav-analytics").assertIsSelected()
                compose.waitUntil(20_000) {compose.onAllNodesWithTag("reportNet").fetchSemanticsNodes().isNotEmpty()}
            }
            else compose.onNodeWithText("Раздел будет подключён на соответствующем этапе плана. Сейчас доступна навигация; данные и операции этого модуля ещё не реализованы.").assertIsDisplayed()
        }
        compose.onNodeWithTag("topNav-workspace").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-workspace").assertIsSelected()
        compose.onNodeWithTag("topNav-history").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("receiptHistory").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-history").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("topNav-history").assertIsSelected()
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        compose.onNodeWithTag("topNav-bookings").assertDoesNotExist()
        compose.onNodeWithTag("settingsStock").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("stockSearch").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("stockSearch").assertExists()
        compose.onNodeWithTag("stockBack").performClick()
        compose.onNodeWithText("Товары").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Добавить товар").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Добавить товар").performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("topNav-workspace").assertIsNotEnabled()
        compose.onNodeWithText("Назад").performClick()
        compose.onNodeWithTag("topNav-shift").performScrollTo().performClick()
        compose.onNodeWithTag("shiftStatus").assertExists()
        compose.onNodeWithTag("topNav-workspace").performScrollTo().performClick()
        compose.waitForIdle()
        val folder=java.io.File(compose.activity.filesDir,"qa").apply {mkdirs()}
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(folder,"top-navigation.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
}
