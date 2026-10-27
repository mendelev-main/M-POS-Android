package com.mendelev.mpos

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.workspace.CurrentCart
import com.mendelev.mpos.feature.workspace.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated render: never opens or writes the installed application's database. */
class WorkspaceAppearanceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun sourceCartWidthAndFutureActionsDoNotIssueOrderCommands() {
        var soon by mutableStateOf<String?>(null)
        var writes=0
        val empty=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),CurrentCart(),0,emptyList(),0)
        compose.setContent {MposTheme {
            Column(Modifier.width(880.dp).height(650.dp)) {
                WorkspaceToolbar("Рабочая зона",false,false,false,0,{}, {}, {}, {}, {soon=it})
                WorkspaceCartPanel(empty,0,Modifier.width(380.dp).weight(1f),{writes++},{_,_->writes++},{writes++},{writes++},{writes++},{writes++},{writes++},{writes++},{soon=it},false)
            }
            if(soon!=null) MposDialog("Скоро будет подключено",{soon=null}) {androidx.compose.material3.Text(soon!!)}
        }}
        compose.onNodeWithTag("workspaceCartPanel").assertWidthIsEqualTo(380.dp)
        compose.onNodeWithTag("workspaceCheckout").assertIsNotEnabled()
        compose.onNodeWithTag("workspaceCustomer").performClick()
        compose.onNodeWithText("Скоро будет подключено").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("workspaceDemand").performClick()
        compose.onNodeWithText("Скоро будет подключено").assertIsDisplayed()
        assertEquals(0,writes)
    }
}
