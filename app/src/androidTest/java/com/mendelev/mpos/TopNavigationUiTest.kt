package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Navigation acceptance without adding products, opening shifts or issuing financial commands. */
class TopNavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun openAdminPanel() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("adminPanelScreen").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasTestTag("adminControl") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        // Returning from a settings module may restore the already opened panel.
        if(compose.onAllNodesWithTag("adminPanelScreen").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("adminControl").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("adminPanelScreen").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("adminPanelScreen").assertIsDisplayed()
    }
    @Test fun globalIconsOpenRootsInsteadOfRememberedSettingsSubpage() {
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("topNav-settings").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-adminActive").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("adminPanelScreen").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("settingsManagement").performScrollTo().performClick()
        compose.onNodeWithTag("managementScreen").assertExists()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("managementPhone").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("managementEmail").assertExists()
        compose.onNodeWithTag("managementTelegram").assertExists()
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        compose.onNodeWithTag("settingsHeader").assertExists()
        compose.onNodeWithTag("managementScreen").assertDoesNotExist()
        compose.onNodeWithTag("topNav-adminActive").performScrollTo().performClick()
        compose.onNodeWithTag("settingsNetwork").performScrollTo().performClick()
        compose.onNodeWithTag("networkSettingsScreen").assertExists()
        // Same underlying settings route must still reset the nested screen.
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        compose.onNodeWithTag("settingsHeader").assertExists()
        compose.onNodeWithTag("networkSettingsScreen").assertDoesNotExist()
        compose.onNodeWithTag("topNav-adminActive").performScrollTo().performClick()
        compose.onNodeWithTag("topNav-workspace").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-workspace").assertIsSelected()
    }
    @Test fun allSourceTabsReachTheirRouteAndSurviveRecreation() {
        CatalogUiTestSupport.openCatalog(compose)
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
        for(route in listOf("purchaseOrders","receiving","events")) {
            compose.onNodeWithTag("topNav-$route").performScrollTo().performClick()
            compose.onNodeWithTag("topNav-$route").assertIsSelected()
            if(route=="purchaseOrders") compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("purchaseNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            else if(route=="receiving") compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("receivingNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            else {
                compose.onNodeWithTag("onlineOrdersScreen").assertIsDisplayed()
                compose.onNodeWithText("Новых событий нет").assertIsDisplayed()
                compose.onNodeWithContentDescription("Закрыть").performClick()
            }
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
        openAdminPanel()
        compose.onNodeWithTag("topNav-analytics").assertDoesNotExist()
        compose.onNodeWithTag("settingsAnalytics").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("reportNet").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("reportNet").assertExists()
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        openAdminPanel()
        compose.onNodeWithTag("settingsStock").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("stockSearch").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("stockSearch").assertExists()
        compose.onNodeWithTag("stockBack").performClick()
        compose.onNodeWithTag("settingsCatalog").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Добавить товар").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Добавить товар").performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("topNav-workspace").assertExists()
        compose.onNodeWithTag("productEditorBack").performClick()
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
