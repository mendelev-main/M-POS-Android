package com.mendelev.mpos

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.workspace.LayoutItem
import com.mendelev.mpos.feature.workspace.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated UI, no application database or external side effects. */
class FolderOpenActionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun explicitOpenKeepsEditingAndDoesNotOpenFolderSettings() {
        val folder=LayoutItem("folder","f","c",name="Бар")
        var editing by mutableStateOf(true)
        var opened by mutableStateOf(false)
        var settings=0
        compose.setContent {MposTheme {
            if(opened) WorkspaceFolderWindow(folder,1,false,{opened=false}) {_,_,_->
                androidx.compose.material3.Text(if(editing)"Расстановка товаров" else "Продажа")
            }
            else WorkspaceFolderTile(folder,editing,true,Modifier.width(240.dp).height(144.dp),{settings++},{opened=true})
        }}
        compose.onNodeWithText("Бар").performClick()
        compose.runOnIdle {assertEquals(1,settings);assertFalse(opened)}
        compose.onNodeWithTag("workspaceFolderOpen:f").performClick()
        compose.onNodeWithText("Расстановка товаров").assertIsDisplayed()
        compose.onNodeWithTag("folderEditToggle").assertDoesNotExist()
        compose.runOnIdle {assertTrue(editing);assertEquals(1,settings)}
        compose.onNodeWithText("Закрыть").performClick()
        compose.runOnIdle {editing=false}
        compose.onNodeWithTag("workspaceFolderOpen:f").assertDoesNotExist()
        compose.onNodeWithText("Бар").performClick()
        compose.onNodeWithText("Продажа").assertIsDisplayed()
        compose.runOnIdle {assertEquals(1,settings)}
    }
}
