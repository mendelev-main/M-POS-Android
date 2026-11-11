package com.mendelev.mpos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.*
import org.junit.Rule
import org.junit.Test

class AdminTopIndicatorTest {
    @get:Rule val compose=createComposeRule()
    @Test fun indicatorFollowsSessionAndRemainsVisibleWhenRouteChanges() {
        val active=mutableStateOf(false)
        val route=mutableStateOf("workspace")
        compose.setContent {MposTheme {MposTopBar(MposTopNavigation(route=route.value,shiftOpen=false,adminActive=active.value,navigate={route.value=it}))}}
        compose.onNodeWithTag("topNav-adminActive").assertDoesNotExist()
        compose.runOnIdle {active.value=true}
        compose.onNodeWithTag("topNav-adminActive").performScrollTo().assertIsDisplayed().assertContentDescriptionEquals("Панель администратора").performClick()
        org.junit.Assert.assertEquals("settingsAdmin",route.value)
        compose.runOnIdle {route.value="history"}
        compose.onNodeWithTag("topNav-adminActive").assertExists()
        compose.runOnIdle {active.value=false}
        compose.onNodeWithTag("topNav-adminActive").assertDoesNotExist()
    }
}
