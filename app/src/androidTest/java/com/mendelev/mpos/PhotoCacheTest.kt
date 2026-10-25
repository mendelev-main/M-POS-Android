package com.mendelev.mpos

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import com.mendelev.mpos.data.media.ProductPhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class PhotoCacheTest {
    @Test fun immutablePrivateThumbnailUsesBoundedSharedMemoryCacheAndMissingFileFails() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val root=java.io.File(context.cacheDir,"photo-cache-${UUID.randomUUID()}")
        try {
            val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.GREEN)
            val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
            val store=ProductPhotoStore(root);val id=store.prepare {ByteArrayInputStream(bytes)}
            val loader=SingletonImageLoader.get(context)
            val request=ImageRequest.Builder(context).data(store.file(id)).size(256).build()
            val first=loader.execute(request);assertTrue(first is SuccessResult)
            val second=loader.execute(request);assertTrue(second is SuccessResult)
            assertEquals(DataSource.MEMORY_CACHE,(second as SuccessResult).dataSource)
            assertTrue(loader.memoryCache!!.size<=16L*1024*1024)
            assertEquals(16L*1024*1024,loader.memoryCache!!.maxSize)
            val missing=loader.execute(ImageRequest.Builder(context).data(store.file("${UUID.randomUUID()}.jpg")).size(256).build())
            assertTrue(missing is ErrorResult)
        } finally {root.deleteRecursively()}
    }
}
