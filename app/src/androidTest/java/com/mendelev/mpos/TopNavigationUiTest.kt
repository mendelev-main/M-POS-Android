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
        for(route in listOf("purchaseOrders","receiving","analytics","bookings","events")) {
            compose.onNodeWithTag("topNav-$route").performScrollTo().performClick()
            compose.onNodeWithTag("topNav-$route").assertIsSelected()
            compose.onNodeWithText("Раздел будет подключён на соответствующем этапе плана. Сейчас доступна навигация; данные и операции этого модуля ещё не реализованы.").assertIsDisplayed()
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
