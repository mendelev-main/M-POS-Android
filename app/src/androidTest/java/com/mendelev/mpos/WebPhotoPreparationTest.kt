package com.mendelev.mpos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.WebPhotoPreparation
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

class WebPhotoPreparationTest {
    private val context: Context=ApplicationProvider.getApplicationContext()
    private fun document(bytes: ByteArray): WebBackupDocument {
        val p=JSONObject().put("id","p").put("type","simple").put("name","Фото").put("price",1).put("cost",1).put("stock",2).put("stockUnit","piece").put("localImageId","../../opaque-key")
        val j=JSONObject().put("version",1).put("products",JSONArray().put(p).put(JSONObject(p.toString()).put("id","q"))).put("employees",JSONArray()).put("shifts",JSONArray()).put("orders",JSONArray()).put("productImages",JSONObject().put("../../opaque-key",Base64.getEncoder().encodeToString(bytes)))
        return WebBackupReader.read(j.toString().byteInputStream())
    }
    private fun png(): ByteArray {
        val bitmap=Bitmap.createBitmap(1600,800,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.GREEN)
        return try {ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()} finally {bitmap.recycle()}
    }
    @Test fun sharedOpaqueKeyPublishesOnlyIsolatedJpegAndFreshAdapterVerifiesIt()=runBlocking {
        val permit=AdminForms(this).permit();val bytes=png();val doc=document(bytes)
        val f=withContext(permit) {WebPhotoPreparation(context).prepare(doc)}
        try {
            assertEquals(setOf("../../opaque-key"),f.files.keys)
            val photo=File(File(f.mediaRoot,"product-photos"),f.files.values.single())
            assertTrue(photo.canonicalPath.startsWith(File(context.noBackupFilesDir,"web-conversion-photos").canonicalPath+"/"))
            val b=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(photo.absolutePath,b)
            assertEquals("image/jpeg",b.outMimeType);assertEquals(1200,b.outWidth);assertEquals(600,b.outHeight)
            assertArrayEquals(bytes,doc.images.getValue("../../opaque-key"))
            val reopened=withContext(permit) {WebPhotoPreparation(context).open(f.id,doc.preview.sha256)}
            assertEquals(f,reopened)
            photo.writeBytes(byteArrayOf(1,2,3))
            try {withContext(permit) {WebPhotoPreparation(context).open(f.id,doc.preview.sha256)};fail()} catch(_:BackupFailure) {}
        } finally {f.mediaRoot.parentFile!!.deleteRecursively()}
    }
    @Test fun preparationFaultsRemoveOnlyUnpublishedStageAndKeepPublishedAsset()=runBlocking {
        val permit=AdminForms(this).permit();val doc=document(png());val root=File(context.noBackupFilesDir,"web-conversion-photos")
        for(point in listOf("photoPrepared","manifestWritten","beforePublish","published")) {
            val before=root.listFiles()?.map {it.name}?.toSet() ?: emptySet()
            try {
                try {withContext(permit) {WebPhotoPreparation(context) {if(it==point) error("injected")}.prepare(doc)};fail()} catch(_:IllegalStateException) {}
                val added=root.listFiles()?.filter {it.name !in before} ?: emptyList()
                assertTrue(added.none {it.name.endsWith(".stage")})
                if(point=="published") {
                    assertEquals(1,added.size)
                    withContext(permit) {WebPhotoPreparation(context).open(added.single().name,doc.preview.sha256)}
                } else assertTrue(added.isEmpty())
            } finally {root.listFiles()?.filter {it.name !in before}?.forEach {it.deleteRecursively()}}
        }
    }
    @Test fun malformedImageAndOldAdminPermitCannotPublish()=runBlocking {
        val permit=AdminForms(this).permit()
        try {withContext(permit) {WebPhotoPreparation(context).prepare(document(byteArrayOf(1,2,3)))};fail()} catch(_:BackupFailure) {}
        Administration.session.logout();Administration.session.login("fixture-admin".toCharArray())
        try {withContext(permit) {WebPhotoPreparation(context).prepare(document(png()))};fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
    }
}
