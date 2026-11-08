package com.mendelev.mpos.domain.backup

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class WebSourceDocumentsTest {
    private fun raw()="\uFEFF  "+javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().readText()+"\n"
    @Test fun retainedSourceCanBeCheckedWithoutDecodingAnotherCopyAndTamperingStillFails() {
        val source=WebBackupReader.read(raw().byteInputStream());val rows=WebSourceDocuments.encode(source)
        WebSourceDocuments.requireSource(rows,source)
        assertThrows(BackupFailure::class.java) {WebSourceDocuments.requireSource(emptyMap(),source)}
        assertThrows(BackupFailure::class.java) {WebSourceDocuments.requireSource(rows-WebSourceDocuments.KEY,source)}
        val changed=WebBackupReader.read((raw()+" ").byteInputStream())
        assertThrows(BackupFailure::class.java) {WebSourceDocuments.requireSource(rows,changed)}
        val key=WebSourceDocuments.KEY+":0000"
        val part=JSONObject(rows.getValue(key));val data=java.util.Base64.getDecoder().decode(part.getString("base64"));data[0]=(data[0].toInt() xor 1).toByte()
        part.put("base64",java.util.Base64.getEncoder().encodeToString(data))
        assertThrows(BackupFailure::class.java) {WebSourceDocuments.requireSource(rows+(key to part.toString()),source)}
    }
    @Test fun multipartSourceUsesExactOffsetsAndRetainsLastPartialChunk() {
        val text=raw()+" ".repeat(100_001)
        val bytes=text.toByteArray();val source=WebBackupReader.read(bytes.inputStream())
        val rows=WebSourceDocuments.encode(source)
        assertTrue(rows.size>3)
        assertArrayEquals(bytes,WebSourceDocuments.bytes(rows))
        assertEquals(source.preview,WebSourceDocuments.decode(rows)!!.preview)
    }
    @Test fun originalSurvivesByteForByteIncludingBOMWhitespaceAndUnknownFields() {
        val bytes=raw().toByteArray();val source=WebBackupReader.read(bytes.inputStream())
        val rows=WebSourceDocuments.encode(source)
        assertArrayEquals(bytes,WebSourceDocuments.bytes(rows))
        assertEquals(source.preview,WebSourceDocuments.decode(rows)!!.preview)
        assertEquals(rows,WebSourceDocuments.encode(WebSourceDocuments.decode(rows)!!))
    }
    @Test fun missingExtraAndRewrittenPartsNeverProduceUsableSource() {
        val rows=WebSourceDocuments.encode(WebBackupReader.read(raw().byteInputStream()))
        for(changed in listOf(rows-WebSourceDocuments.KEY,rows-(WebSourceDocuments.KEY+":0000"),rows+(WebSourceDocuments.KEY+":0001" to rows.getValue(WebSourceDocuments.KEY+":0000")),rows+(WebSourceDocuments.KEY to JSONObject(rows.getValue(WebSourceDocuments.KEY)).put("sha256","0".repeat(64)).toString()))) {
            assertThrows(BackupFailure::class.java) {WebSourceDocuments.decode(changed)}
        }
    }
}
