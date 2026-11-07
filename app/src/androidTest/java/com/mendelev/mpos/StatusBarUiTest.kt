package com.mendelev.mpos

import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.mendelev.mpos.core.ui.*
import org.junit.Rule
import org.junit.Test

/** Observe actual Android insets, including independent modal windows and Activity recreation. */
class StatusBarUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hidden(window: Window): Boolean = ViewCompat.getRootWindowInsets(window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.statusBars())==false
    private fun waitHidden(window: ()->Window?) {
        compose.waitUntil(15_000) {var result=false;compose.runOnUiThread {result=window()?.let(::hidden)==true};result}
    }
    @Test fun statusStripIsHiddenInActivityDialogAndAfterRecreate() {
        waitHidden {compose.activity.window}
        var dialogWindow: Window?=null
        val mode=mutableIntStateOf(1)
        compose.runOnUiThread {compose.activity.setContent {MposTheme {
            if(mode.intValue==1) MposDialog("Проверка окна",{mode.intValue=0}) {
                val view=LocalView.current
                SideEffect {dialogWindow=(view.parent as DialogWindowProvider).window}
            }
        }}}
        waitHidden {dialogWindow}
        compose.runOnIdle {mode.intValue=0}
        waitHidden {compose.activity.window}
        compose.activityRule.scenario.recreate()
        waitHidden {compose.activity.window}
    }
}
