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
        var updates=0;var stock=0;var suppliers=0
        compose.setContent {val d=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density,1.5f)) {
                MposTheme {Box(Modifier.width(520.dp)) {
                    SettingsHome({updates++},{},{suppliers++},{},{stock++},null,{},false)
                }}
            }
        }
        compose.onNodeWithTag("settingsUpdatesInfo").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settingsStock").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("settingsSuppliers").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1,updates);assertEquals(1,stock);assertEquals(1,suppliers)
        compose.onNodeWithTag("settingsBackup").performScrollTo().performClick()
        compose.onNodeWithTag("settingsPlannedNotice").assertIsDisplayed()
    }
}
