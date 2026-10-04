package com.mendelev.mpos.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.mendelev.mpos.MainActivity
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class ProductPhotoManager(
    private val activity: MainActivity,
    private val store: ProductImageStore,
) {
    fun handle(payload: JSONObject) {
        when (payload.optString("action", "pick")) {
            "pick" -> activity.pickProductPhoto()
            "remove" -> store.remove(payload.optString("id"))
            "read" -> sendRead(payload.optString("id"), payload.optString("requestId"))
        }
    }

    fun accept(bytes: ByteArray) {
        val prepared = prepare(bytes) ?: return activity.nativeMessage("Не удалось обработать фотографию")
        val id = runCatching { store.save(prepared) }.getOrElse {
            return activity.nativeMessage("Не удалось сохранить фотографию")
        }
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(prepared, Base64.NO_WRAP)
        activity.callJavaScript("window.handleNativeProductImage&&window.handleNativeProductImage(${JSONObject.quote(dataUrl)},${JSONObject.quote(id)});")
    }

    private fun sendRead(id: String, requestId: String) {
        val bytes = store.read(id) ?: return
        val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        activity.callJavaScript("window.handleNativeProductImageRead&&window.handleNativeProductImageRead(${JSONObject.quote(requestId)},${JSONObject.quote(dataUrl)});")
    }

    private fun prepare(source: ByteArray): ByteArray? {
        val original = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        var maxDimension = 1200
        repeat(8) {
            val longest = max(original.width, original.height)
            val scale = minOf(1f, maxDimension.toFloat() / longest.toFloat())
            val width = max(1, (original.width * scale).roundToInt())
            val height = max(1, (original.height * scale).roundToInt())
            val bitmap = if (width == original.width && height == original.height) original else Bitmap.createScaledBitmap(original, width, height, true)
            for (quality in intArrayOf(82, 70, 55)) {
                val stream = ByteArrayOutputStream()
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    val data = stream.toByteArray()
                    if (data.size <= 600_000) {
                        if (bitmap !== original) bitmap.recycle()
                        original.recycle()
                        return data
                    }
                }
            }
            if (bitmap !== original) bitmap.recycle()
            maxDimension = (maxDimension * 0.75).roundToInt()
        }
        original.recycle()
        return null
    }
}

