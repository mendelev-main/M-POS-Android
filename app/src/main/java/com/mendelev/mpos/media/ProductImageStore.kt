package com.mendelev.mpos.media

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class ProductImageStore(context: Context) {
    private val directory = File(context.filesDir, "product-images").apply { mkdirs() }

    fun save(bytes: ByteArray): String {
        val id = UUID.randomUUID().toString().lowercase()
        file(id).writeBytes(bytes)
        return id
    }

    fun read(id: String): ByteArray? = if (valid(id)) file(id).takeIf(File::isFile)?.readBytes() else null

    fun remove(id: String) {
        if (valid(id)) file(id).delete()
    }

    fun prune(keeping: Set<String>) {
        directory.listFiles().orEmpty().forEach { image ->
            if (image.extension.equals("jpg", true) && image.nameWithoutExtension !in keeping) image.delete()
        }
    }

    fun response(id: String): WebResourceResponse? {
        if (!valid(id)) return null
        val image = file(id)
        if (!image.isFile) return null
        return WebResourceResponse("image/jpeg", null, FileInputStream(image))
    }

    private fun valid(id: String) = runCatching { UUID.fromString(id) }.isSuccess
    private fun file(id: String) = File(directory, "$id.jpg")
}

