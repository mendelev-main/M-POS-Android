package com.mendelev.mpos.backup

import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** v13 image packaging/staging only; never persists POS records or confirms an import. */
class MPosBackupImages(
    private val read: (String) -> ByteArray?,
    private val save: (ByteArray) -> String,
    private val remove: (String) -> Unit,
) {
    data class Prepared(val document: JSONObject, val imageIds: List<String>)

    fun prepareImport(serialized: String): Prepared {
        val document = JSONObject(serialized)
        val staged = mutableListOf<String>()
        try {
            val products = document.optJSONArray("products") ?: error("Некорректный файл резервной копии")
            val encoded = document.optJSONObject("productImages") ?: JSONObject()
            document.remove("productImages")
            for (index in 0 until products.length()) {
                val product = products.getJSONObject(index)
                val oldId = product.optString("localImageId")
                if (oldId.isBlank() || !encoded.has(oldId)) {
                    product.remove("localImageId")
                    product.remove("imageUploadPending")
                    continue
                }
                val image = Base64.decode(encoded.getString(oldId), Base64.DEFAULT)
                require(image.size <= 2_000_000 && BitmapFactory.decodeByteArray(image, 0, image.size) != null) {
                    "Повреждена фотография товара в резервной копии"
                }
                val fresh = save(image)
                staged += fresh
                product.put("localImageId", fresh)
            }
            document.put("imageCount", staged.size)
            return Prepared(document, staged.toList())
        } catch (error: Throwable) {
            discard(staged)
            throw error
        }
    }

    fun prepareExport(source: JSONObject): JSONObject {
        val document = JSONObject(source.toString())
        val encoded = JSONObject()
        val products = document.optJSONArray("products") ?: JSONArray()
        for (index in 0 until products.length()) {
            val id = products.optJSONObject(index)?.optString("localImageId").orEmpty()
            if (id.isNotBlank() && !encoded.has(id)) {
                read(id)?.let { encoded.put(id, Base64.encodeToString(it, Base64.NO_WRAP)) }
            }
        }
        document.put("productImages", encoded)
        document.put("imageCount", encoded.length())
        return document
    }

    /** Attempt every removal even when one provider operation fails. */
    fun discard(ids: List<String>) { ids.forEach { runCatching { remove(it) } } }
}
