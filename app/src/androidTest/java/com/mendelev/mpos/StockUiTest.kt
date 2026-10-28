package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.feature.stock.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StockUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun lowFilterSearchHistoryAndBackKeepExactQuantity() {
        val rows=listOf(StockRow("a","Мука","Сырьё","kg","0.0004","0.0004",2),StockRow("b","Чай","Напитки","piece","10",null,1))
        lateinit var model: StockViewModel
        compose.runOnIdle {model=StockViewModel(StockReadAccess {id->StockSnapshot(rows,id,if(id==null) emptyList() else listOf(MovementEntity("movement",id,"-9.9996","product_card","command",1)),1)})}
        compose.setContent {MposTheme {StockScreen(model) {}}}
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.snapshot!=null}
        shot("stock-list")
        compose.onNodeWithTag("stockLow").performClick()
        compose.onNodeWithTag("stockProduct:b").assertDoesNotExist()
        compose.onNodeWithTag("stockProduct:a").performScrollTo().performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.snapshot?.selectedId=="a"}
        compose.onNodeWithTag("stockQuantity").assertTextContains("0.0004",substring=true)
        compose.onNodeWithTag("stockMovement:movement").performScrollTo().assertTextContains("-9.9996",substring=true)
        shot("stock-history")
        compose.onNodeWithTag("stockBack").performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.snapshot?.selectedId==null}
        compose.onNodeWithTag("stockProduct:b").assertDoesNotExist()
        compose.onNodeWithTag("stockLow").performClick()
        compose.onNodeWithTag("stockSearch").performTextInput("чай")
        compose.onNodeWithTag("stockProduct:a").assertDoesNotExist();compose.onNodeWithTag("stockProduct:b").assertExists()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=java.io.File(instrumentation.targetContext.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }

}
