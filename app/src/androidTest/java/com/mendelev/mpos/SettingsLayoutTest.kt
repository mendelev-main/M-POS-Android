package com.mendelev.mpos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.feature.settings.SettingsHome
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SettingsLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun narrowLargeTextKeepsInformationAndWarehouseRoutesReachable() {
        var updates=0
        compose.setContent {val d=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density,1.5f)) {
                MposTheme {Box(Modifier.width(520.dp)) {
                    SettingsHome({updates++},{},{},{},{},null,{},false)
                }}
            }
        }
        val panel=compose.onNodeWithTag("adminControl").getUnclippedBoundsInRoot()
        val updatesBounds=compose.onNodeWithTag("settingsUpdatesInfo").getUnclippedBoundsInRoot()
        val themeBounds=compose.onNodeWithTag("settingsThemeControl").getUnclippedBoundsInRoot()
        assertEquals(panel.top,updatesBounds.top);assertEquals(panel.bottom-panel.top,updatesBounds.bottom-updatesBounds.top)
        assertEquals(panel.top,themeBounds.top);assertEquals(panel.bottom-panel.top,themeBounds.bottom-themeBounds.top)
        assertTrue(panel.right<=updatesBounds.left && updatesBounds.right<=themeBounds.left)
        compose.onNodeWithTag("settingsUpdatesInfo").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settingsEmployees").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("employeeName").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("employeeName").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("settingsSuppliers").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("supplierName").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("supplierName").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        assertEquals(1,updates)
        compose.onNodeWithTag("settingsBackup").performScrollTo().performClick()
        compose.onNodeWithTag("settingsPlannedNotice").assertIsDisplayed()
    }
}
