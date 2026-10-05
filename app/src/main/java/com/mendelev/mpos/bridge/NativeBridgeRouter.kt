package com.mendelev.mpos.bridge

import com.mendelev.mpos.MainActivity
import com.mendelev.mpos.backup.BackupManager
import com.mendelev.mpos.data.MPosStorageMirror
import com.mendelev.mpos.media.ProductPhotoManager
import com.mendelev.mpos.network.NativeNetworkTransport
import com.mendelev.mpos.settings.NativeSettingsStore
import org.json.JSONObject

class NativeBridgeRouter(
    private val activity: MainActivity,
    private val photos: ProductPhotoManager,
    private val backup: BackupManager,
    private val settings: NativeSettingsStore,
    private val storageMirror: MPosStorageMirror,
    private val networkTransport: NativeNetworkTransport,
) {
    fun receive(raw: String) {
        runCatching {
            val envelope = JSONObject(raw)
            val payload = envelope.optJSONObject("payload") ?: JSONObject()
            when (envelope.optString("channel")) {
                "photoPicker" -> photos.handle(payload)
                "backup" -> backup.handle(payload)
                "printer" -> activity.handlePrinter(payload)
                "telegram" -> activity.handleTelegram(payload)
                "settings" -> settings.handle(payload)
                "storage" -> storageMirror.handle(payload)
                "network" -> networkTransport.handle(payload)
                "diagnostics" -> if (payload.optString("action") == "export") activity.exportDiagnostics()
            }
        }.onFailure { activity.nativeMessage("Не удалось обработать нативную команду") }
    }
}
