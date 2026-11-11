package com.mendelev.mpos.feature.workspace

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.workspace.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ParkedAppearanceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun webCardGeometryActionsEmptyAndLockedStateInBothThemes() {
        val dark=mutableStateOf(false);val busy=mutableStateOf(false);val empty=mutableStateOf(false)
        val order=ParkedOrder("atlas",1,CurrentCart(lines=listOf(
            CartLine("coffee","coffee","Капучино","6.5",false,"6.5","2","шт",emptyList(),emptyList(),emptyMap()),
            CartLine("cake","cake","Чизкейк классический","8",false,"8","1","шт",emptyList(),emptyList(),emptyMap())),details=OrderDetails("Заказ у окна","takeaway")),java.time.Instant.parse("2026-11-07T09:50:00Z").toEpochMilli())
        var opened=0;var deleted=0;var closed=0
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window,false)
            WindowCompat.getInsetsController(compose.activity.window,compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides config) {MposTheme {Surface(Modifier.fillMaxSize()) {
                ParkedOrdersWindow(if(empty.value) emptyList() else listOf(order),busy.value,null,{false},{closed++},{opened++},{deleted++})
            }}}
        }
        compose.onNodeWithTag("parkedWindow").assertWidthIsEqualTo(820.dp).assertHeightIsEqualTo(178.dp)
        compose.onNodeWithTag("parkedCard:atlas").assertHeightIsEqualTo(68.dp)
        compose.onNodeWithTag("parkedDelete:atlas").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        compose.onNodeWithText("21,00 BYN").assertExists();compose.onNodeWithText("С собой").assertExists()
        shot("C09-native-light")
        compose.onNodeWithTag("parkedOpen:atlas").performClick();assertEquals(1,opened)
        compose.onNodeWithTag("parkedDelete:atlas").performClick();assertEquals(1,deleted)
        compose.onNodeWithContentDescription("Закрыть отложенные чеки").performClick();assertEquals(1,closed)
        compose.runOnIdle {dark.value=true};shot("C09-native-dark")
        compose.runOnIdle {busy.value=true}
        compose.onNodeWithTag("parkedOpen:atlas").assertIsNotEnabled();compose.onNodeWithTag("parkedDelete:atlas").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Закрыть отложенные чеки").assertIsNotEnabled()
        compose.runOnIdle {busy.value=false;empty.value=true}
        compose.onNodeWithText("Нет отложенных чеков").assertExists()
        compose.onNodeWithTag("parkedOpen:atlas").assertDoesNotExist();shot("C09-empty-native-dark")
    }
    @Test fun orderTypeBadgesHaveEqualWidthAndHeight() {
        compose.setContent {MposTheme {Column {listOf("here","takeaway","delivery").forEach {ParkedType(it)}}}}
        listOf("here","takeaway","delivery").forEach {
            compose.onNodeWithTag("parkedType:$it").assertWidthIsEqualTo(110.dp).assertHeightIsEqualTo(28.dp)
        }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"qa").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
