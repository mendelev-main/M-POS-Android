package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LaunchTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val folder=java.io.File(compose.activity.filesDir,"qa").apply { mkdirs() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(folder,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun waitText(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun catalogSavesAndDraftSurvivesRecreation() {
        waitText("Добавить товар")
        compose.onNodeWithText("Добавить товар").performClick()
        val name="Чай ${UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithTag("productName").performTextInput(name)
        compose.onNodeWithTag("productPrice").performTextReplacement("2,50")
        compose.onNodeWithTag("productStock").performScrollTo().performTextReplacement("10")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("productName").assertTextContains(name)
        screenshot("product-editor")
        compose.onNodeWithTag("saveProduct").performClick()
        waitText("Добавить товар")
        compose.onNodeWithText(name).performClick()
        compose.onNodeWithTag("productStock").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("saveProduct").performClick()
        waitText("8 шт")
        compose.onNodeWithText("8 шт").assertIsDisplayed()
        java.io.File(compose.activity.filesDir,"qa").mkdirs()
        java.io.File(compose.activity.filesDir,"qa/productName.txt").writeText(name)
        screenshot("catalog")
        val icon=compose.activity.packageManager.getApplicationIcon(compose.activity.packageName)
        val bitmap=android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,512,512);icon.draw(android.graphics.Canvas(bitmap))
        java.io.File(compose.activity.filesDir,"qa/launcher-icon.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        compose.onNodeWithText("Компоненты").performClick()
        compose.onNodeWithText("Пример окна").performScrollTo().performClick()
        screenshot("shared-dialog")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithText("Назад").performScrollTo().performClick()
        compose.onNodeWithText("Настройки").assertIsDisplayed()
    }
}
