package com.mendelev.mpos.core.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LocalMposDarkTheme=staticCompositionLocalOf<Boolean?> {null}
internal data class ThemeChoice(val dark: Boolean,val busy: Boolean=false,val error: String?=null,val change: (Boolean)->Unit={})
internal val LocalMposThemeChoice=staticCompositionLocalOf {ThemeChoice(false)}
@Composable fun isMposDarkTheme()=LocalMposDarkTheme.current ?: isSystemInDarkTheme()
/** Device preference, independent of the active sales dataset and administrator session. */
@Composable internal fun rememberThemeChoice(): ThemeChoice {
    val context=LocalContext.current.applicationContext
    val prefs=remember(context) {context.getSharedPreferences("mpos-appearance",Context.MODE_PRIVATE)}
    var dark by remember(prefs) {mutableStateOf(prefs.getString("theme","light")=="dark")}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    return ThemeChoice(dark,busy,error) {next->
        if(!busy) {busy=true;error=null;scope.launch {
            val saved=withContext(Dispatchers.IO) {runCatching {prefs.edit().putString("theme",if(next) "dark" else "light").commit()}.getOrDefault(false)}
            if(saved) dark=next else error="Не удалось сохранить тему. Повторите переключение."
            busy=false
        }}
    }
}
