package com.mendelev.mpos.domain.backup

import org.json.*
import org.junit.Assert.*
import org.junit.Test

class WebProductMetadataDocumentsTest {
    private fun source(): WebBackupDocument {
        val j=JSONObject(javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().readText())
        val product=j.getJSONArray("products").getJSONObject(0).put("stockUnit","piece").put("sku","SKU-1").put("internalNote",JSONObject.NULL).put("tileSymbol","★").put("sortOrder",1.5).put("recipeYieldManual",false)
        j.getJSONArray("products").put(JSONObject(product.toString()).put("id","q"))
        return WebBackupReader.read(j.toString().byteInputStream())
    }
    @Test fun perProductFieldsRetainNullFalseAndFractionalOrderInsideStringEnvelope() {
        val source=source();val rows=WebProductMetadataDocuments.encode(source);val id=WebIdentity.mapped("product","p")
        val metadata=WebProductMetadataDocuments.read(rows.getValue(WebProductMetadataDocuments.PREFIX+id),id)
        assertEquals("★",metadata.tileSymbol);assertEquals("1.5",metadata.sortOrder)
        assertTrue(metadata.payload.contains("\"internalNote\":null"));assertTrue(metadata.payload.contains("\"recipeYieldManual\":false"));assertTrue(metadata.payload.contains("SKU-1"))
        WebProductMetadataDocuments.validate(rows,source)
        val envelope=BackupJsonParser(rows.getValue(WebProductMetadataDocuments.PREFIX+id)).parse() as BackupJson.Obj
        assertEquals(setOf("version"),envelope.fields.filterValues {it is BackupJson.Num}.keys)
    }
    @Test fun missingChangedAndUnboundFieldsRefuseInsteadOfSilentlyLosingProductData() {
        val source=source();val rows=WebProductMetadataDocuments.encode(source);val key=rows.keys.first()
        assertThrows(BackupFailure::class.java) {WebProductMetadataDocuments.validate(rows-key,source)}
        assertThrows(BackupFailure::class.java) {WebProductMetadataDocuments.validate(rows+(key to JSONObject(rows.getValue(key)).put("payload","changed").toString()),source)}
        assertThrows(BackupFailure::class.java) {WebProductMetadataDocuments.validate(rows,null)}
        WebProductMetadataDocuments.validate(emptyMap(),source) // pre-sidecar native backups retain the original.
    }
}
