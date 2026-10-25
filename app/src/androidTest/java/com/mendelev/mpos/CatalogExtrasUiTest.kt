package com.mendelev.mpos

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.feature.products.CatalogViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import java.io.ByteArrayInputStream
import java.util.UUID

class CatalogExtrasUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepareCatalog() { CatalogUiTestSupport.prepare(compose) }
    private fun waitText(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun modifierAndPhotoEditorRetainsDraftCancelsPickerAndSavesConfiguration() {
        waitText("Добавить товар")
        val suffix=UUID.randomUUID().toString().take(6)
        val milk="Молоко $suffix";val coffee="Кофе $suffix"
        compose.onNodeWithText("Добавить товар").performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("productName").performTextInput(milk)
        compose.onNodeWithTag("saveProduct").performClick()
        waitText("Добавить товар")
        compose.onNodeWithText("Добавить товар").performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.onNodeWithTag("productName").performTextInput(coffee)
        compose.onNodeWithTag("productPrice").performTextReplacement("3")
        compose.onNodeWithText("Добавить группу").performScrollTo().performClick()
        compose.onNodeWithTag("modifierGroup0").performScrollTo().performTextInput("Молоко на выбор")
        compose.onNodeWithText("Выбрать вариант 1").performScrollTo().performClick()
        compose.onNodeWithText(milk).performClick()
        compose.onNodeWithTag("modifierQty0-0").performScrollTo().performTextReplacement("0.05")
        compose.onNodeWithTag("modifierPrice0-0").performScrollTo().performTextReplacement("1.50")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val monitor=instrumentation.addMonitor(IntentFilter(Intent.ACTION_GET_CONTENT).apply { addDataType("image/*");addCategory(Intent.CATEGORY_OPENABLE) },Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        try {
            compose.onNodeWithText("Добавить фото").performScrollTo().performClick()
            compose.waitForIdle()
            compose.waitUntil(10_000) { monitor.hits==1 }
            assertEquals(1,monitor.hits)
        } finally { instrumentation.removeMonitor(monitor) }
        lateinit var model: CatalogViewModel
        compose.runOnIdle { model=ViewModelProvider(compose.activity)[CatalogViewModel::class.java] }
        assertNull(model.state.value.draft!!.photoId)
        val bitmap=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val bytes=java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle() }.toByteArray()
        val session=model.state.value.draft!!.sessionId
        compose.runOnIdle { model.importPhoto(session) { ByteArrayInputStream(bytes) } }
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.draft?.photoId!=null }
        val prepared=model.state.value.draft!!.photoId
        val groupId=model.state.value.draft!!.modifierGroups.single().id
        compose.runOnIdle { model.importPhoto("stale-session") { throw AssertionError("Stale picker must not open the stream") } }
        assertEquals(prepared,model.state.value.draft!!.photoId)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("modifierPrice0-0").performScrollTo().assertTextContains("1.50")
        compose.onNodeWithText("Удалить фото").performScrollTo().assertExists()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("productPhoto").fetchSemanticsNodes().isNotEmpty() }
        val folder=java.io.File(compose.activity.filesDir,"qa").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            java.io.File(folder,"extras-editor.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            screenshot.recycle()
        }
        compose.onNodeWithTag("saveProduct").performClick()
        waitText("Добавить товар")
        compose.onNodeWithText(coffee).performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.waitUntil(20_000) { model.state.value.draft!=null && !model.state.value.busy }
        assertEquals(prepared,model.state.value.draft!!.photoId)
        assertEquals(groupId,model.state.value.draft!!.modifierGroups.single().id)
        compose.onNodeWithText("Удалить фото").performScrollTo().performClick()
        assertNull(model.state.value.draft!!.photoId)
        compose.onNodeWithText("Назад").performClick()
        compose.onNodeWithText("Отбросить").performClick()
        waitText("Добавить товар")
        compose.onNodeWithText(coffee).performClick()
        CatalogUiTestSupport.waitEditor(compose)
        compose.waitUntil(20_000) { model.state.value.draft!=null && !model.state.value.busy }
        assertEquals(prepared,model.state.value.draft!!.photoId)
        compose.onNodeWithText("Назад").performClick()
        waitText("Добавить товар")
    }
}
