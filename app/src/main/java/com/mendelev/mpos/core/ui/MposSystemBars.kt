package com.mendelev.mpos.core.ui

import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Hide the notification/status strip; leave Android navigation and keyboard insets alone. */
fun Window.hideMposStatusBar() {
    WindowCompat.getInsetsController(this,decorView).apply {
        systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
    }
}

/** Compose dialogs own a separate Android Window, so they need the same policy. */
@Composable fun MposDialogStatusBar() {
    val view=LocalView.current
    val window=(view.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window,view) {
        window.hideMposStatusBar()
        val observer=view.viewTreeObserver
        val listener=ViewTreeObserver.OnWindowFocusChangeListener {focused->if(focused) window.hideMposStatusBar()}
        observer.addOnWindowFocusChangeListener(listener)
        onDispose {if(observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)}
    }
}
