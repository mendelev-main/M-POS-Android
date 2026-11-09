package com.mendelev.mpos.backend

import android.content.Context
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.telegram.TelegramKeystoreCipher
import java.io.File

internal class BackendAccess(context: Context,private val generation: Long) {
    val store=BackendSettingsStore(File(context.noBackupFilesDir,"backend-settings-v1.enc"),TelegramKeystoreCipher("mpos.backend.settings.v1"),::requireSessionBoolean)
    private fun requireSessionBoolean()=Administration.session.state.value.let {it.active && it.generation==generation}
    fun requireSession() {check(requireSessionBoolean()) {"Войдите в администрирование"}}
    fun current(expected: BackendSettings): BackendSettings {
        requireSession()
        return store.read().also {check(it==expected) {"Подключение изменилось. Откройте настройки заново"}}
    }
}
