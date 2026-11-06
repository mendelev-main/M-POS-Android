package com.mendelev.mpos.domain.backup

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class WebSourceDocumentsTest {
    private fun raw()="\uFEFF  "+javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().readText()+"\n"
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
