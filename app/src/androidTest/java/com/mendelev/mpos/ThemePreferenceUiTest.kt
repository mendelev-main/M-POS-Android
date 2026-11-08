package com.mendelev.mpos

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemePreferenceUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun choiceChangesCustomChromeAndSurvivesThemeHostRecreation() {
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("mpos-appearance",Context.MODE_PRIVATE)
        val old=prefs.getString("theme",null)
        assertTrue(prefs.edit().putString("theme","light").commit())
        var generation by mutableStateOf(0)
        try {
            compose.setContent {key(generation) {MposTheme {
                val choice=LocalMposThemeChoice.current
                Column {
                Text(if(isMposDarkTheme()) "dark" else "light",Modifier.testTag("themeResult"))
                Switch(choice.dark,choice.change,enabled=!choice.busy,modifier=Modifier.testTag("themeToggle"))
                }
            }}}
            compose.onNodeWithTag("themeResult").assertTextEquals("light")
            compose.onNodeWithTag("themeToggle").performClick()
            compose.waitUntil(5_000) {prefs.getString("theme",null)=="dark" && compose.onAllNodesWithText("dark").fetchSemanticsNodes().isNotEmpty()}
            compose.runOnIdle {generation++}
            compose.onNodeWithTag("themeResult").assertTextEquals("dark")
            compose.onNodeWithTag("themeToggle").performClick()
            compose.waitUntil(5_000) {prefs.getString("theme",null)=="light" && compose.onAllNodesWithText("light").fetchSemanticsNodes().isNotEmpty()}
        } finally {val edit=prefs.edit();if(old==null) edit.remove("theme") else edit.putString("theme",old);assertTrue(edit.commit())}
    }
}
