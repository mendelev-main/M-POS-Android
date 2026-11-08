package com.mendelev.mpos.domain.backup

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebBackupArchiveTest {
    private fun zip(vararg entries: Pair<String,ByteArray>)=ByteArrayOutputStream().also {out->ZipOutputStream(out).use {z->entries.forEach {(name,data)->z.putNextEntry(ZipEntry(name));z.write(data);z.closeEntry()}}}.toByteArray()
    private fun fixture()=javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {JSONObject(it.readText())}
    @Test fun archivePhotosAndOriginalBytesSurviveSourceRetention() {
        val source=fixture();val id="00000000-0000-0000-0000-000000000001"
        source.getJSONArray("products").getJSONObject(0).put("localImageId",id);source.put("imageCount",1)
        val bytes=zip("mpos-backup-v1.json" to source.toString().toByteArray(),"images/$id.jpg" to byteArrayOf(1,2,3))
        val doc=WebBackupReader.read(bytes.inputStream())
        assertEquals(1,doc.preview.imageCount);assertEquals(3L,doc.preview.photoBytes)
        val staged=doc.withoutPhotoPayloads()
        assertTrue(staged.images.values.all {it.isEmpty()})
        assertEquals(doc.images.keys,staged.images.keys)
        assertEquals(WebCatalogMapping.map(doc),WebCatalogMapping.map(staged))
        WebSourceDocuments.requireSource(WebSourceDocuments.encode(staged),doc)
        assertArrayEquals(bytes,doc.original)
        assertEquals(doc.preview.sha256,WebSourceDocuments.decode(WebSourceDocuments.encode(doc))!!.preview.sha256)
    }
    @Test fun rejectTraversalMissingJsonDuplicatePhotoIdentityAndOversizePhoto() {
        for(bytes in listOf(zip("../mpos-backup-v1.json" to byteArrayOf(1)),zip("images/a.jpg" to byteArrayOf(1)),
            zip("mpos-backup-v1.json" to fixture().toString().toByteArray(),"images/a.jpg" to byteArrayOf(1),"images/a.png" to byteArrayOf(2)),
            zip("images/a.jpg" to ByteArray(2_000_001)))) {
            assertThrows(BackupFailure::class.java) {WebBackupReader.read(bytes.inputStream())}
        }
    }
    @Test fun floatingPointNoiseDoesNotBecomeFractionalCash() {
        assertEquals(123L,WebValues.minor(BackupJson.Num("1.2300000000000002")))
        assertThrows(BackupFailure::class.java) {WebValues.minor(BackupJson.Num("1.235"))}
        assertEquals("1.234567890123",WebValues.number(BackupJson.Num("1.2345678901230001")))
        assertThrows(BackupFailure::class.java) {WebValues.number(BackupJson.Num("0.0000000000000001"))}
    }
}
