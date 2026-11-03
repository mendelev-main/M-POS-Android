package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebLegacyHistoryArchiveTest {
    private fun source(comment: String="historical")=WebBackupReader.read(JSONObject().put("version",1).put("products",JSONArray()).put("employees",JSONArray()).put("shifts",JSONArray())
        .put("orders",JSONArray().put(JSONObject().put("id","r").put("timestamp",1).put("total",10).put("method","card").put("items",JSONArray().put(JSONObject().put("productId","gone").put("name","Before").put("qty","0.0004").put("price",10).put("cost","3.333333333333333").put("comment",comment))).put("customer",JSONObject().put("phone","private-contact")).put("botToken","do-not-copy"))).toString().byteInputStream())
    @Test fun frozenBusinessFactsRoundtripWithoutSecretsOrActivatingCustomerModules() {
        val a=WebLegacyHistoryArchiveCodec.encode(source())
        assertEquals("3.333333333333333",a.receipts.single().lines.single().cost)
        assertFalse(a.payload.contains("do-not-copy"));assertFalse(a.payload.contains("private-contact"))
        assertEquals(1,a.omitted["orders.customer"]);assertEquals(1,a.omitted["orders.botToken"])
        val b=WebLegacyHistoryArchiveCodec.decode(a.payload)
        assertEquals(a.payload,b.payload);assertEquals(a.sourceSha256,b.sourceSha256)
        assertEquals(a.receipts,b.receipts);assertFalse(a.toString().contains("Before"))
    }
    @Test fun largeUnicodeHistorySplitsIntoBoundedRowsAndReassemblesExactly() {
        val a=WebLegacyHistoryArchiveCodec.encode(source("👨‍🍳".repeat(40000)))
        val rows=WebLegacyHistoryDocuments.encode(a)
        assertTrue(rows.size>3);assertTrue(rows.values.all {it.toByteArray().size<=256*1024})
        assertEquals(a.payload,WebLegacyHistoryDocuments.decode(rows)!!.payload)
        assertEquals(rows,WebLegacyHistoryDocuments.encode(WebLegacyHistoryArchiveCodec.decode(a.payload)))
    }
    @Test fun missingExtraReorderedOrCorruptedPartsRefuseInsteadOfReturningEmptyHistory() {
        val rows=WebLegacyHistoryDocuments.encode(WebLegacyHistoryArchiveCodec.encode(source("x".repeat(100000))))
        val part=rows.keys.first {it!=WebLegacyHistoryDocuments.KEY}
        for(bad in listOf(rows-part,rows-WebLegacyHistoryDocuments.KEY,rows+(WebLegacyHistoryDocuments.PREFIX+"9999" to rows.getValue(part)),rows+(part to "{\"version\":1,\"content\":\"changed\"}"))) {
            assertThrows(BackupFailure::class.java) {WebLegacyHistoryDocuments.decode(bad)}
        }
        assertNull(WebLegacyHistoryDocuments.decode(emptyMap()))
    }
    @Test fun forgedSensitiveFieldsFutureProfileAndSourceMismatchAreRejected() {
        val a=WebLegacyHistoryArchiveCodec.encode(source())
        val raw=JSONObject(a.payload);raw.getJSONObject("data").getJSONArray("orders").getJSONObject(0).put("botToken","forged")
        assertThrows(BackupFailure::class.java) {WebLegacyHistoryArchiveCodec.decode(raw.toString())}
        assertThrows(BackupFailure::class.java) {WebLegacyHistoryArchiveCodec.decode(JSONObject(a.payload).put("version",2).toString())}
        val rows=WebLegacyHistoryDocuments.encode(a).toMutableMap()
        rows[WebLegacyHistoryDocuments.KEY]=JSONObject(rows.getValue(WebLegacyHistoryDocuments.KEY)).put("sourceSha256","0".repeat(64)).toString()
        assertThrows(BackupFailure::class.java) {WebLegacyHistoryDocuments.decode(rows)}
    }
    @Test fun unsupportedCurrencyCannotBeHiddenByAllowlisting() {
        val raw=JSONObject().put("version",1).put("products",JSONArray()).put("employees",JSONArray())
            .put("orders",JSONArray()).put("shifts",JSONArray().put(JSONObject().put("id","s").put("currency","USD")))
        assertThrows(BackupFailure::class.java) {WebLegacyHistoryArchiveCodec.encode(WebBackupReader.read(raw.toString().byteInputStream()))}
    }
    @Test fun inventoryBaselineIsExcludedButEnteredValuesStillRequireRecount() {
        val j=JSONObject().put("version",1).put("products",JSONArray()).put("employees",JSONArray()).put("shifts",JSONArray()).put("orders",JSONArray())
            .put("inventoryDraft",JSONObject().put("id","count").put("items",JSONArray().put(JSONObject().put("productId","p").put("actual",7).put("_androidCountBaseline",JSONObject().put("runtime","unsafe-runtime")))))
        val a=WebLegacyHistoryArchiveCodec.encode(WebBackupReader.read(j.toString().byteInputStream()))
        assertFalse(a.payload.contains("unsafe-runtime"));assertTrue(a.warehouse.inventoryDraft!!.lines.single().requiresRecount)
        assertEquals("7",a.warehouse.inventoryDraft!!.lines.single().actual)
    }
}
