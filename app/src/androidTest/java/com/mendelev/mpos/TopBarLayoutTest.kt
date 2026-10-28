package com.mendelev.mpos

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TopBarLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun narrowPanelScrollsToSettingsAndCommandLockPreventsNavigation() {
        var locked by mutableStateOf(false)
        var selected by mutableStateOf("workspace")
        var calls=0
        compose.setContent { MposTheme {
            Box(Modifier.width(520.dp)) {
                MposTopBar(MposTopNavigation(selected,false) {selected=it;calls++}, enabled=!locked)
            }
        } }
        compose.onNodeWithTag("mposTopBar").assertHeightIsEqualTo(62.dp)
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        assertEquals("settings",selected)
        compose.onNodeWithTag("topNav-settings").assertIsSelected()
        compose.runOnIdle {locked=true}
        compose.onNodeWithTag("topNav-history").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(1,calls)
        compose.runOnIdle {locked=false}
        compose.onNodeWithTag("topNav-history").performClick().assertIsSelected()
        assertEquals("history",selected)
    }
    @Test fun tabletGeometryAndLargeTextKeepEveryDestinationReachable() {
        var large by mutableStateOf(false)
        var selected by mutableStateOf("workspace")
        compose.setContent { MposTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if(large) 2f else 1f)) {
                Box(Modifier.width(1280.dp)) {
                    MposTopBar(MposTopNavigation(selected,true) {selected=it})
                }
            }
        } }
        compose.onNodeWithTag("mposTopBar").assertHeightIsEqualTo(68.dp)
        compose.onNodeWithTag("topNav-settings").assertIsDisplayed()
        compose.runOnIdle {large=true}
        // Android applies nonlinear font scaling: verify growth and reachability, not 2× pixel arithmetic.
        compose.onNodeWithTag("mposTopBar").assertHeightIsAtLeast(69.dp)
        compose.onNodeWithTag("topNav-settings").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("topNav-settings").assertIsSelected()
        compose.onNodeWithTag("topNav-workspace").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("workspace",selected)
    }

    @Test fun inventoryReminderIsConditionalReachableAndRespectsCommandLock() {
        var shown by mutableStateOf(false);var locked by mutableStateOf(false);var selected by mutableStateOf("workspace");var calls=0
        compose.setContent {MposTheme {Box(Modifier.width(520.dp)) {MposTopBar(MposTopNavigation(selected,false,inventoryReminder=shown) {selected=it;calls++},enabled=!locked)}}}
        compose.onNodeWithTag("topNav-inventory").assertDoesNotExist()
        compose.runOnIdle {shown=true}
        compose.onNodeWithTag("topNav-inventory").performScrollTo().assertIsDisplayed().performClick().assertIsSelected();assertEquals("inventory",selected)
        compose.runOnIdle {locked=true}
        compose.onNodeWithTag("topNav-inventory").assertIsNotEnabled().performClick();assertEquals(1,calls)
        compose.runOnIdle {locked=false;shown=false};compose.onNodeWithTag("topNav-inventory").assertDoesNotExist()
    }

}
