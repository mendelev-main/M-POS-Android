package com.mendelev.mpos.backup

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MPosBackupImagesTest {
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            try { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray() }
            finally { bitmap.recycle() }
        }
    }
    private fun product(id: String) = JSONObject().put("id", id).put("name", "Кофе")
        .put("localImageId", "old-photo").put("imageUploadPending", true).put("stock", 12.5)
    private fun document(vararg products: JSONObject) = JSONObject().put("version", 13)
        .put("products", JSONArray(products.toList())).put("orders", JSONArray().put(JSONObject().put("total", 42.5)))
        .put("extension", JSONObject().put("unchanged", true))
    private fun encoded(bytes: ByteArray) = JSONObject().put("old-photo", Base64.encodeToString(bytes, Base64.NO_WRAP))

    @Test fun roundTripPreservesBusinessFieldsAndUsesFreshPhotoIds() {
        val bytes = image()
        val saved = mutableMapOf<String, ByteArray>()
        val module = MPosBackupImages({ if (it == "old-photo") bytes else null }, { saved["new-photo"] = it; "new-photo" }, { saved.remove(it); Unit })
        val source = document(product("p1"))
        val before = source.toString()
        val exported = module.prepareExport(source)
        assertEquals(before, source.toString())
        assertEquals(1, exported.getInt("imageCount"))
        val prepared = module.prepareImport(exported.toString())
        assertEquals(listOf("new-photo"), prepared.imageIds)
        assertArrayEquals(bytes, saved["new-photo"])
        val restored = prepared.document.getJSONArray("products").getJSONObject(0)
        assertEquals("new-photo", restored.getString("localImageId"))
        assertTrue(restored.getBoolean("imageUploadPending"))
        assertEquals(12.5, restored.getDouble("stock"), 0.0)
        assertEquals(source.getJSONArray("orders").toString(), prepared.document.getJSONArray("orders").toString())
        assertEquals(source.getJSONObject("extension").toString(), prepared.document.getJSONObject("extension").toString())
        assertFalse(prepared.document.has("productImages"))
        assertTrue(exported.has("productImages"))
    }

    @Test fun sharedExportImageIsDeduplicatedButImportKeepsExistingPerProductStaging() {
        val bytes = image()
        var reads = 0
        var saves = 0
        val module = MPosBackupImages({ reads++; bytes }, { "fresh-${++saves}" }, {})
        val exported = module.prepareExport(document(product("p1"), product("p2")))
        assertEquals(1, reads)
        assertEquals(1, exported.getInt("imageCount"))
        val imported = module.prepareImport(exported.toString())
        assertEquals(listOf("fresh-1", "fresh-2"), imported.imageIds)
        assertEquals(2, imported.document.getInt("imageCount"))
    }

    @Test fun absentImagesRemoveOnlyImageReferenceAndPendingFlag() {
        val module = MPosBackupImages({ null }, { error("unexpected save") }, {})
        val source = document(product("p1"))
        val exported = module.prepareExport(source)
        assertEquals(0, exported.getInt("imageCount"))
        val prepared = module.prepareImport(exported.toString())
        val product = prepared.document.getJSONArray("products").getJSONObject(0)
        assertFalse(product.has("localImageId"))
        assertFalse(product.has("imageUploadPending"))
        assertEquals("p1", product.getString("id"))
        assertEquals(12.5, product.getDouble("stock"), 0.0)
        assertTrue(prepared.imageIds.isEmpty())
    }

    @Test fun corruptedLaterImageRollsBackEarlierStagedFilesWithoutMutatingInput() {
        val first = product("p1")
        val second = product("p2").put("localImageId", "bad")
        val source = document(first, second).put("productImages", encoded(image()).put("bad", "bm90LWFuLWltYWdl"))
        val before = source.toString()
        val removed = mutableListOf<String>()
        val module = MPosBackupImages({ null }, { "fresh-1" }, { removed += it })
        val error = assertThrows(IllegalArgumentException::class.java) { module.prepareImport(source.toString()) }
        assertEquals("Повреждена фотография товара в резервной копии", error.message)
        assertEquals(listOf("fresh-1"), removed)
        assertEquals(before, source.toString())
    }

    @Test fun saveFailureRollsBackEarlierFilesAndPreservesOriginalError() {
        val failure = java.io.IOException("disk full")
        var saves = 0
        val removed = mutableListOf<String>()
        val module = MPosBackupImages({ null }, { if (++saves == 2) throw failure else "fresh-1" }, { removed += it })
        val source = document(product("p1"), product("p2")).put("productImages", encoded(image()))
        assertSame(failure, assertThrows(java.io.IOException::class.java) { module.prepareImport(source.toString()) })
        assertEquals(listOf("fresh-1"), removed)
    }

    @Test fun fatalPreparationFailureAlsoCleansAlreadyStagedFiles() {
        val failure = AssertionError("synthetic fatal storage failure")
        var saves = 0
        val removed = mutableListOf<String>()
        val module = MPosBackupImages({ null }, { if (++saves == 2) throw failure else "fresh-1" }, { removed += it })
        val source = document(product("p1"), product("p2")).put("productImages", encoded(image()))
        assertSame(failure, assertThrows(AssertionError::class.java) { module.prepareImport(source.toString()) })
        assertEquals(listOf("fresh-1"), removed)
    }

    @Test fun malformedProductsAndOversizedImageNeverReachSave() {
        val module = MPosBackupImages({ null }, { error("unexpected save") }, {})
        assertThrows(IllegalStateException::class.java) { module.prepareImport("{}") }
        val source = document(product("p1")).put("productImages", encoded(ByteArray(2_000_001)))
        assertThrows(IllegalArgumentException::class.java) { module.prepareImport(source.toString()) }
    }

    @Test fun cleanupAttemptsEveryIdDespiteRemovalFailure() {
        val attempted = mutableListOf<String>()
        val module = MPosBackupImages({ null }, { "unused" }, { attempted += it; if (it == "first") error("removal failed") })
        module.discard(listOf("first", "second"))
        assertEquals(listOf("first", "second"), attempted)
    }
}
