package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.data.local.PurchaseOrderCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ImportedPurchaseOriginTest {
    private fun order()=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Supplier",listOf(PurchaseLine("p",1,"Product","kg","3","box","2","kg"))),1)
    @Test fun sourceVersionFiveDoesNotInventFourHistoricalNativeEdits() {
        val value=order().copy(importedOrigin=ImportedPurchaseOrigin("a".repeat(64),"source-order",5))
        PurchaseOrderRules.validate(value)
        val restored=PurchaseOrderCodec.decode(PurchaseOrderCodec.encode(listOf(value))).single()
        assertEquals(value,restored);assertEquals(1L,restored.requestVersion);assertEquals(5L,restored.importedOrigin!!.requestVersion)
        assertTrue(restored.history.isEmpty());assertEquals(listOf("6"),restored.expectedQuantities)
        assertThrows(IllegalArgumentException::class.java) {PurchaseOrderRules.validate(value.copy(requestVersion=5))}
    }
    @Test fun absentSourceVersionStaysUnknownAndOldNativeCodecDoesNotAcquireOrigin() {
        val value=order().copy(importedOrigin=ImportedPurchaseOrigin("a".repeat(64),"source-order",null))
        assertNull(PurchaseOrderCodec.decode(PurchaseOrderCodec.encode(listOf(value))).single().importedOrigin!!.requestVersion)
        val old=JSONObject(PurchaseOrderCodec.encode(listOf(order()))).put("version",3)
        old.getJSONArray("items").getJSONObject(0).remove("importedOrigin")
        assertEquals(order(),PurchaseOrderCodec.decode(old.toString()).single())
    }
    @Test fun invalidOriginCannotBypassNormalOrderValidation() {
        for(origin in listOf(ImportedPurchaseOrigin("bad","id",1),ImportedPurchaseOrigin("a".repeat(64),"",1),ImportedPurchaseOrigin("a".repeat(64),"id",0))) {
            assertThrows(IllegalArgumentException::class.java) {PurchaseOrderRules.validate(order().copy(importedOrigin=origin))}
        }
    }
}
