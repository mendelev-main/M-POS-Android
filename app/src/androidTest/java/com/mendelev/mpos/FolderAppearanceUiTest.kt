package com.mendelev.mpos

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.feature.workspace.*
import com.mendelev.mpos.feature.sale.ReceiptHistoryShell
import com.mendelev.mpos.domain.workspace.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Render-only fixture, no user dataset, Telegram, backend or printer. */
class FolderAppearanceUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun editorSavesExplicitStyleAndCancelDoesNotSave() {
        var open by mutableStateOf(true)
        var saved: Pair<String,FolderAppearance>?=null
        compose.setContent {MposTheme {if(open) WorkspaceFolderEditor("f",null,false,null,{open=false},{name,style->saved=name to style},{},{})}}
        compose.onNodeWithTag("workspaceFolderName").performTextInput("Бар")
        compose.onNodeWithTag("workspaceFolderSymbol").performTextInput("☕")
        compose.onNodeWithTag("categoryColor:#DDEBFF").performScrollTo().performClick()
        compose.onNodeWithTag("folderColumns:3").performScrollTo().performClick()
        compose.onNodeWithTag("folderHeight:120").performScrollTo().performClick()
        compose.onNodeWithTag("workspaceFolderSave").performScrollTo().performClick()
        compose.runOnIdle {assertEquals("Бар" to FolderAppearance("#DDEBFF","☕",3,120),saved);saved=null}
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        compose.runOnIdle {assertFalse(open);assertNull(saved)}
    }
    @Test fun historyHeaderStaysWhileBodyIsLoadingAndCanStillRespectOperationLock() {
        var loading by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        compose.setContent {MposTheme {
            CompositionLocalProvider(LocalMposTopNavigation provides MposTopNavigation("history",false) {}) {
                Box(Modifier.width(1280.dp).height(700.dp)) {
                    ReceiptHistoryShell({},enabled) {
                        if(loading) androidx.compose.material3.CircularProgressIndicator()
                        else androidx.compose.material3.Text("Чеки загружены")
                    }
                }
            }
        }}
        compose.onNodeWithTag("mposTopBar").assertIsDisplayed().assertHeightIsEqualTo(68.dp)
        compose.onNodeWithTag("topNav-history").assertIsSelected()
        val before=compose.onNodeWithTag("mposTopBar").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {loading=false;enabled=false}
        compose.onNodeWithText("Чеки загружены").assertIsDisplayed()
        compose.onNodeWithTag("topNav-history").assertIsNotEnabled()
        assertEquals(before,compose.onNodeWithTag("mposTopBar").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {loading=true;enabled=true}
        compose.onNodeWithTag("mposTopBar").assertIsDisplayed()
    }
}
