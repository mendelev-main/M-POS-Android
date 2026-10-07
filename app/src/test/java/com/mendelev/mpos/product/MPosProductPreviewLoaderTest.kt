package com.mendelev.mpos.product

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import com.mendelev.mpos.media.ProductImageStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class MPosProductPreviewLoaderTest {
    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.GREEN)
        val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out); bitmap.recycle(); return out.toByteArray()
    }
    @Test fun thumbnailsAreSampledAndMalformedImagesDoNotReachUi() {
        val result = MPosProductPreviewLoader.decode(jpeg())!!
        assertTrue(result.width <= 400); assertTrue(result.height <= 225); result.recycle()
        assertNull(MPosProductPreviewLoader.decode(byteArrayOf(1, 2, 3)))
        assertNull(MPosProductPreviewLoader.decode(ByteArray(MPosProductPreviewLoader.MAX_BYTES + 1)))
    }
    @Test fun previewReaderRefusesWhileReadingInsteadOfLoadingUnboundedResponse() {
        var read = 0
        val input = object : InputStream() {
            override fun read(): Int { read++; return 1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { read += len; return len }
        }
        try { MPosProductPreviewLoader.bounded(input); fail("oversized preview admitted") } catch (_: IllegalArgumentException) { }
        assertEquals(MPosProductPreviewLoader.MAX_BYTES + 8192, read)
        assertArrayEquals(byteArrayOf(1, 2, 3), MPosProductPreviewLoader.bounded(ByteArrayInputStream(byteArrayOf(1, 2, 3))))
    }
    @Test fun cancelledOldPreviewCannotRepopulateReplacedFormOrChangeLocalMedia() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val store = ProductImageStore(activity); val original = jpeg(); val id = store.save(original)
        val loader = MPosProductPreviewLoader(activity); val results = mutableListOf<Bitmap?>()
        loader.load("mpos-image://$id") { results += it }; loader.clear()
        loader.load("") { results += it }
        // The blank replacement resolves immediately; old completions retain the old epoch.
        ShadowLooper.idleMainLooper(); assertEquals(1, results.size); assertNull(results.single())
        assertArrayEquals(original, store.read(id)); loader.clear(); store.remove(id)
    }
}
