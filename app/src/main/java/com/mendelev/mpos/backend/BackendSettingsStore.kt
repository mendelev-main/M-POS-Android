package com.mendelev.mpos.backend

import android.util.AtomicFile
import com.mendelev.mpos.telegram.TelegramSecretCipher
import java.io.File

class BackendSettingsStore(file: File,private val cipher: TelegramSecretCipher,private val authorized: ()->Boolean) {
    companion object {private val lock=Any()}
    private val atomic=AtomicFile(file)
    fun read(): BackendSettings=synchronized(lock) {
        if(!atomic.baseFile.exists() && !File(atomic.baseFile.path+".bak").exists()) return@synchronized BackendSettings()
        BackendSettingsCodec.decode(cipher.decrypt(atomic.openRead().use {it.readBytes()}).toString(Charsets.UTF_8))
    }
    fun save(input: BackendSettings): BackendSettings=synchronized(lock) {
        check(authorized()) {"Войдите в администрирование"}
        val valid=input.validated()
        check(read().revision==valid.revision) {"Настройки backend изменились. Откройте окно заново"}
        val saved=valid.copy(revision=Math.addExact(valid.revision,1))
        val bytes=cipher.encrypt(BackendSettingsCodec.encode(saved).toByteArray())
        val stream=atomic.startWrite()
        try {stream.write(bytes);atomic.finishWrite(stream)} catch(e:Exception) {atomic.failWrite(stream);throw e}
        saved
    }
}
