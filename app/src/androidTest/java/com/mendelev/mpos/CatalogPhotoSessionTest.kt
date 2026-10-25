package com.mendelev.mpos

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.feature.products.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

class CatalogPhotoSessionTest {
    @Test fun legacyDraftSessionIsDurableBeforePickerAndCallbackWaitsForRestoration() = runBlocking {
        val context: Context=ApplicationProvider.getApplicationContext()
        val root=File(context.cacheDir,"session-${UUID.randomUUID()}").apply { mkdirs() }
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        val firstStore=ViewModelStore();val nextStore=ViewModelStore()
        try {
            val photos=ProductPhotoStore(root);val repo=CatalogRepository(db,photos=photos);repo.initialize()
            val legacy=JSONObject(ProductDraft(name="Старый черновик",dirty=true).json()).apply { remove("sessionId") }
            repo.saveDraft(DraftEntity(payload=legacy.toString()))
            suspend fun model(store: ViewModelStore)=withContext(Dispatchers.Main) {
                ViewModelProvider(store,object:ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=CatalogViewModel(repo) as T
                })[CatalogViewModel::class.java]
            }
            val initial=model(firstStore)
            val restored=withTimeout(20_000) { initial.state.first { !it.loading } }.draft!!
            assertEquals(restored.sessionId,JSONObject(repo.dao.draft()!!.payload).getString("sessionId"))
            withContext(Dispatchers.Main) { firstStore.clear() }
            val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
            val bytes=java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle() }.toByteArray()
            val next=model(nextStore)
            withContext(Dispatchers.Main) { next.importPhoto(restored.sessionId) { ByteArrayInputStream(bytes) } }
            val imported=withTimeout(20_000) { next.state.first { !it.loading && !it.busy && it.draft?.photoId!=null } }.draft!!
            assertEquals(restored.sessionId,imported.sessionId)
            assertEquals(imported.photoId,ProductDraft.parse(repo.dao.draft()!!.payload).photoId)
            assertTrue(photos.file(imported.photoId!!).isFile)
        } finally {
            withContext(Dispatchers.Main) { firstStore.clear();nextStore.clear() }
            db.close();root.deleteRecursively()
        }
    }
}
