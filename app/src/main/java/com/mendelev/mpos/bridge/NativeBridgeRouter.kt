package com.mendelev.mpos.bridge

import com.mendelev.mpos.MainActivity
import com.mendelev.mpos.backup.MPosBackupManager
import com.mendelev.mpos.data.MPosStorageMirror
import com.mendelev.mpos.media.ProductPhotoManager
import com.mendelev.mpos.network.MPosNetworkTransport
import com.mendelev.mpos.settings.MPosSettingsStore
import org.json.JSONObject

class NativeBridgeRouter(
    private val activity: MainActivity,
    private val photos: ProductPhotoManager,
    private val backup: MPosBackupManager,
    private val settings: MPosSettingsStore,
    private val storageMirror: MPosStorageMirror,
    private val networkTransport: MPosNetworkTransport,
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
                "paymentScreen" -> activity.handlePaymentScreen(payload)
                "shiftScreen" -> activity.handleShiftScreen(payload)
                "network" -> networkTransport.handle(payload)
                "diagnostics" -> if (payload.optString("action") == "export") activity.exportDiagnostics()
            }
        }.onFailure { activity.nativeMessage("Не удалось обработать нативную команду") }
    }
}
