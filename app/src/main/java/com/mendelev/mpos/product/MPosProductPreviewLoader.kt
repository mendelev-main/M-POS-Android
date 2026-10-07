package com.mendelev.mpos.product

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.mendelev.mpos.media.ProductImageStore
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Presentation thumbnail only: never changes media, catalogue or backup state. */
internal class MPosProductPreviewLoader(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(0, 1, 15, TimeUnit.SECONDS, ArrayBlockingQueue(4), { task ->
        Thread(task, "mpos-product-preview").apply { isDaemon = true }
    })
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    private var epoch = 0
    private var pending: Future<*>? = null
    @Volatile private var call: Call? = null

    fun load(source: String, result: (Bitmap?) -> Unit) {
        clear(); val generation = epoch
        if (source.isBlank()) { result(null); return }
        pending = worker.submit {
            val bitmap = runCatching {
                val bytes = when {
                    source.startsWith("mpos-image://") -> ProductImageStore(app).response(source.removePrefix("mpos-image://"))?.data?.use { bounded(it) }
                    source.startsWith("data:image/") && source.length <= 800023 -> Base64.decode(source.substringAfter(","), Base64.DEFAULT)
                    source.startsWith("https://") || source.startsWith("http://") -> {
                        val request = client.newCall(Request.Builder().url(source).get().build()); call = request
                        if (Thread.currentThread().isInterrupted) request.cancel()
                        request.execute().use { response ->
                            if (!response.isSuccessful) null else response.body?.byteStream()?.use { bounded(it) }
                        }
                    }
                    else -> null
                } ?: return@runCatching null
                decode(bytes)
            }.getOrNull()
            main.post { if (generation == epoch) result(bitmap) else bitmap?.recycle() }
        }
    }
    fun clear() {
        epoch++; pending?.cancel(true); pending = null; call?.cancel(); call = null; worker.purge()
    }
    companion object {
        // Preview bound only; does not change the 500 MB full-backup import limit.
        internal const val MAX_BYTES = 8 * 1024 * 1024
        internal fun bounded(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream(); val chunk = ByteArray(8192)
            while (true) {
                val count = input.read(chunk); if (count < 0) return out.toByteArray()
                require(out.size() + count <= MAX_BYTES) { "preview too large" }; out.write(chunk, 0, count)
            }
        }
        internal fun decode(bytes: ByteArray): Bitmap? {
            if (bytes.size > MAX_BYTES) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = maxOf(1, ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + 319) / 320).toInt())
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
