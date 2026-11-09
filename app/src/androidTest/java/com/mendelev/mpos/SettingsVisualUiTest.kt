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
        compose.onNodeWithTag("settingsEmployees").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("employeeName").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("settingsHeader").assertExists()
        compose.onNodeWithTag("employeeList").assertDoesNotExist()
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        compose.onNodeWithTag("employeeName").assertDoesNotExist()
        compose.onNodeWithTag("settingsUpdatesInfo").performScrollTo()
        compose.onNodeWithTag("settingsUpdatesInfo").performClick()
        compose.onNodeWithTag("updatesWindow").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("updatesWindow").assertExists()
        screenshot("settings-updates")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("updatesWindow").assertDoesNotExist()
        compose.onNodeWithTag("settingsBackup").performScrollTo().performClick()
        compose.onNodeWithTag("backupInspectionOnly").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("backupInspectionOnly").assertExists()
        screenshot("settings-backup")
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        compose.onNodeWithTag("settingsAdd:RECEIPT_LABEL").performScrollTo().performClick()
        compose.onNodeWithTag("settingName").assertIsDisplayed()
        screenshot("settings-template-editor")
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("settingName").assertDoesNotExist()
        compose.onNodeWithTag("adminControl").performScrollTo().performClick()
        compose.onNodeWithTag("adminPanelScreen").assertExists()
        compose.onNodeWithTag("settingsAnalytics").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("adminLogout").performScrollTo().assertIsDisplayed()
        screenshot("settings-admin")
        compose.onNodeWithTag("settingsNetwork").performScrollTo().performClick()
        compose.onNodeWithTag("networkSettingsScreen").assertExists()
        compose.onNodeWithTag("networkBackend").performScrollTo().performClick()
        compose.onNodeWithTag("networkConfig:backend").assertExists()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("backendUrl").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("backendUrl").assertExists()
        compose.onNodeWithTag("backendKey").assertExists()
        compose.onNodeWithTag("backendUrl").performTextReplacement("http://invalid.example")
        compose.onNodeWithTag("backendSave").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Укажите HTTPS адрес backend без параметров и пароля").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Закрыть").performClick()
        compose.onNodeWithTag("networkNotifications").assertDoesNotExist()
        compose.onNodeWithText("Открыть кассовый ящик").assertDoesNotExist()
        compose.onNodeWithText("Чеки оплаты и заказы на кухню").assertDoesNotExist()
        compose.onNodeWithTag("networkPrinters").assertContentDescriptionEquals("Подключить принтер").performScrollTo().performClick()
        compose.onNodeWithTag("printerConfiguration").assertExists()
        compose.onNodeWithTag("networkSettingsScreen").assertExists()
        compose.onNodeWithTag("printerName").performScrollTo().performTextInput("Тестовая касса")
        compose.onNodeWithTag("printerIp").performScrollTo().performTextInput("256.1.1.1")
        compose.onNodeWithTag("printerSave").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithText("Введите IP-адрес, например 192.168.1.100").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithTag("printerConfiguration").assertDoesNotExist()
        compose.onNodeWithTag("networkTelegram").performScrollTo().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("telegramToken").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("telegramOwnerShift").assertExists()
        compose.onNodeWithTag("telegramOwnerMonth").assertExists()
        compose.onNodeWithTag("telegramToken").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("telegramTest").performClick()
        compose.onNodeWithText("Введите токен бота").assertExists()
        compose.onNodeWithText("Закрыть").performClick()
        compose.onNodeWithTag("telegramSettings").assertDoesNotExist()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithTag("adminPanelScreen").assertExists()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithTag("adminPanelScreen").assertDoesNotExist()
        compose.onNodeWithTag("settingsHeader").assertExists()
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
