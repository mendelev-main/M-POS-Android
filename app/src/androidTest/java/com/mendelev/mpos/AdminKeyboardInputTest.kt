package com.mendelev.mpos

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.access.AdminSession
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.access.PasswordVerifier
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.feature.access.AdminControl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdminKeyboardInputTest {
    @get:Rule val compose=createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun maskedHardwareShiftDigitsAndBackspaceSubmitOriginalCharacters() {
        val previous=Administration.session
        var submitted=""
        val session=AdminSession(PasswordVerifier { submitted=it.concatToString(); submitted=="Ab9" })
        Administration.session=session
        try {
            compose.setContent { MposTheme { AdminControl() } }
            compose.onNodeWithTag("adminControl").performClick()
            fun exportedPassword(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if(node==null) return false
                if(node.viewIdResourceName=="adminPassword") return node.isPassword
                return (0 until node.childCount).any {exportedPassword(node.getChild(it))}
            }
            compose.waitUntil(10_000) {
                exportedPassword(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
            }
            compose.onNodeWithTag("adminPassword").performClick().performKeyInput {
                keyDown(Key.ShiftLeft); pressKey(Key.A); keyUp(Key.ShiftLeft)
                pressKey(Key.B); pressKey(Key.Zero); pressKey(Key.Backspace); pressKey(Key.Nine)
            }
            compose.onNodeWithText("Показать").assertDoesNotExist()
            compose.onNodeWithTag("adminPassword").performTextReplacement("••X")
            compose.onNodeWithTag("adminPassword").performTextReplacement("••9")
            compose.onNodeWithTag("adminPassword").assert(
                SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            compose.onNodeWithTag("adminLogin").performClick()
            compose.waitUntil(10_000) {session.state.value.active}
            assertEquals("Ab9",submitted)
        } finally { Administration.session=previous }
    }
}
