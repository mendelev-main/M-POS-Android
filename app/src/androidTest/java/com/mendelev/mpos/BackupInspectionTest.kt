package com.mendelev.mpos

import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

internal fun backupSourceFixture()=InstrumentationRegistry.getInstrumentation().context.assets.open("backup/web-v13-source-export.json").bufferedReader().use {it.readText()}
class BackupInspectionTest {
    @Test fun actualDecoderAuthorityMissingPhotoAndSourceFileRemainUnchanged()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"backup-${UUID.randomUUID()}.mposbackup")
        val access=WebBackupInspection(context.contentResolver)
        try {
            val j=JSONObject(backupSourceFixture());val bitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888)
            val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
            val id="00000000-0000-0000-0000-000000000001"
            j.getJSONArray("products").getJSONObject(0).put("localImageId",id)
            j.put("productImages",JSONObject().put(id,android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP))).put("imageCount",1)
            file.writeText(j.toString());val original=file.readBytes();val uri=android.net.Uri.fromFile(file).toString()
            try {access.inspect(uri);fail()} catch(e: CatalogFailure) {assertEquals("access",e.kind)}
            val permit=AdminForms(this).permit()
            val preview=permit.write {access.inspect(uri)}
            assertEquals(1,preview.imageCount);assertEquals(4000.toBigInteger(),preview.paidMinor)
            assertArrayEquals(original,file.readBytes())
            j.getJSONObject("productImages").put(id,android.util.Base64.encodeToString(byteArrayOf(1,2,3),android.util.Base64.NO_WRAP));file.writeText(j.toString())
            try {permit.write {access.inspect(uri)};fail()} catch(e: BackupFailure) {assertEquals("photo",e.kind)}
            Administration.session.logout()
            try {permit.write {access.inspect(uri)};fail()} catch(e: CatalogFailure) {assertEquals("access",e.kind)}
        } finally {file.delete()}
    }
}
