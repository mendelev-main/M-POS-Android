package com.mendelev.mpos.backup

import android.net.Uri
import com.mendelev.mpos.MainActivity
import com.mendelev.mpos.media.ProductImageStore
import org.json.JSONArray
import org.json.JSONObject

class MPosBackupManager(
    private val activity: MainActivity,
    private val images: ProductImageStore,
) {
    private val backupImages = MPosBackupImages(images::read, images::save, images::remove)
    private var pendingExport: ByteArray? = null
    private var pendingExportName = "M-POS-backup.mposbackup"

    fun handle(payload: JSONObject) {
        when (payload.optString("action")) {
            "export" -> prepareExport(payload)
            "chooseImport" -> activity.chooseBackupFile()
            "cancelImport" -> backupImages.discard(payload.optJSONArray("imageIds").strings())
            "finishImport" -> images.prune(payload.optJSONArray("activeImageIds").strings().toSet())
        }
    }

    fun writeExport(uri: Uri) {
        val data = pendingExport ?: return
        runCatching { activity.contentResolver.openOutputStream(uri, "w")!!.use { it.write(data) } }
            .onSuccess { result(true, "Резервная копия сохранена") }
            .onFailure { result(false, "Не удалось записать резервную копию") }
        pendingExport = null
    }

    fun import(uri: Uri) {
        var staged = emptyList<String>()
        runCatching {
            val raw = activity.contentResolver.openInputStream(uri)!!.use { input ->
                MPosBackupInput.read(input)
            }
            val prepared = backupImages.prepareImport(raw.toString(Charsets.UTF_8))
            val document = prepared.document
            staged = prepared.imageIds
            activity.callJavaScript(
                "window.handleNativeBackupImport&&window.handleNativeBackupImport(${document},${JSONArray(staged)});",
                onError = {
                    backupImages.discard(staged)
                    result(false, "Не удалось открыть резервную копию")
                },
            )
        }.onFailure { error ->
            backupImages.discard(staged)
            result(false, error.message ?: "Некорректный файл резервной копии")
        }
    }

    private fun prepareExport(payload: JSONObject) {
        runCatching {
            val document = backupImages.prepareExport(payload.getJSONObject("data"))
            pendingExport = document.toString(2).toByteArray(Charsets.UTF_8)
            pendingExportName = payload.optString("fileName", pendingExportName).replace('/', '-')
            activity.createBackupFile(pendingExportName)
        }.onFailure { result(false, "Не удалось подготовить резервную копию") }
    }

    private fun result(ok: Boolean, message: String) {
        activity.callJavaScript("window.handleNativeBackupResult&&window.handleNativeBackupResult({ok:$ok,message:${JSONObject.quote(message)}});")
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }
}

