package com.mendelev.mpos.data.backup

import android.content.Context
import android.util.AtomicFile
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.recovery.AppDataset
import com.mendelev.mpos.domain.backup.WebSettingsMapping
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.telegram.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Roll forward before opening application UI. An interrupted apply has no marker and is repeated. */
internal object RestoredDeviceSettings {
    private fun marker(c: Context)=File(c.noBackupFilesDir,"restored-settings-applied-"+AppDataset.paths(c).id)
    private fun write(file: File,bytes: ByteArray) {
        file.parentFile?.mkdirs();val a=AtomicFile(file);val out=a.startWrite()
        try {out.write(bytes);a.finishWrite(out)} catch(e:Exception) {a.failWrite(out);throw e}
    }
    suspend fun apply(context: Context) {
        if(marker(context).exists()) return
        val db=CatalogDatabase.get(context)
        val raw=db.workspace().document(WebSettingsMapping.DEVICE)?.payload ?: return
        WebSettingsMapping.validate(raw)
        val j=WebSettingsMapping.decode(raw)
        val backend=WebSettingsMapping.backend(j,AppDataset.paths(context).id)
        val telegram=WebSettingsMapping.telegram(j)
        val printers=WebSettingsMapping.printers(j)
        write(File(context.noBackupFilesDir,"backend-settings-v1.enc"),TelegramKeystoreCipher("mpos.backend.settings.v1").encrypt(BackendSettingsCodec.encode(backend).toByteArray()))
        write(File(context.noBackupFilesDir,"telegram-settings-v1.enc"),TelegramKeystoreCipher().encrypt(TelegramSettingsCodec.encode(telegram).toByteArray()))
        if(backend.url.isNotBlank() && backend.deviceKey.isNotBlank()) BackendPublication(context).storeDemand(backend,j.optBoolean("demandOverload"))
        PrinterSettingsStore(File(context.filesDir,"printer-configurations-v1.json")) {false}.restore(printers)
        check(context.getSharedPreferences("mpos-appearance",Context.MODE_PRIVATE).edit().putString("theme",j.getString("theme")).commit())
        // Restoration is not a new shift/month event. Do not send a historical monthly report on login.
        val recipient=java.security.MessageDigest.getInstance("SHA-256").digest("${telegram.botToken}:${telegram.chatId}:${telegram.threadId}".toByteArray()).joinToString("") {"%02x".format(it)}
        FileTelegramJournal(File(context.noBackupFilesDir,"telegram-delivery-v1")).put("month:${TelegramMessages.previousMonth(java.time.LocalDate.now())}:$recipient","restored")
        write(marker(context),byteArrayOf(1))
    }
    /** Capture current edited settings in native backups, without mutating the active DB. */
    fun capture(context: Context,base: String): String {
        val j=WebSettingsMapping.decode(base)
        j.put("theme",context.getSharedPreferences("mpos-appearance",Context.MODE_PRIVATE).getString("theme","light"))
        val b=File(context.noBackupFilesDir,"backend-settings-v1.enc")
        if(b.exists()) {
            val backend=BackendSettingsStore(b,TelegramKeystoreCipher("mpos.backend.settings.v1")) {false}.read()
            j.put("backendNative",JSONObject(BackendSettingsCodec.encode(backend)))
            if(backend.url.isNotBlank() && backend.deviceKey.isNotBlank()) j.put("demandOverload",BackendPublication(context).demand(backend))
        }
        val t=File(context.noBackupFilesDir,"telegram-settings-v1.enc")
        if(t.exists()) j.put("telegramNative",JSONObject(TelegramSettingsCodec.encode(TelegramSettingsStore(t,TelegramKeystoreCipher()) {false}.read())))
        val p=File(context.filesDir,"printer-configurations-v1.json")
        if(p.exists()) j.put("printersNative",JSONArray(p.readText()))
        return WebSettingsMapping.envelope(j).also(WebSettingsMapping::validate)
    }
}
