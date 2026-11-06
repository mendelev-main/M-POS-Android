package com.mendelev.mpos.domain.backup

import org.junit.Assert.*
import org.junit.Test

class WebProductMetadataTest {
    private fun project(raw: String)=WebProductMetadataCodec.project((BackupJsonParser(raw).parse() as BackupJson.Obj).fields)
    @Test fun roundtripPreservesTextEscapesUnicodeFalseNullAndNumericRepresentation() {
        val f=project("""{"sku":"  A\\1\"  ","internalNote":"Строка\n👨‍🍳\tзаметка","tileSymbol":"☕","availableOnline":false,"description":null,"sortOrder":1e2,"recipeYieldManual":true}""")
        assertEquals(f,WebProductMetadataCodec.decode(f.payload));assertEquals("100",f.sortOrder)
        assertTrue(f.payload.contains("1e2"));assertTrue(f.payload.contains("\"description\":null"))
        assertTrue(f.payload.contains("\"availableOnline\":false"));assertEquals("☕",f.tileSymbol)
        assertFalse(f.toString().contains("заметка"));assertFalse(f.toString().contains("A\\1"))
    }
    @Test fun absentNullFalseAndEmptyAreDistinctAcrossSerialization() {
        val absent=project("{}");val nil=project("""{"availableOnline":null}""")
        val no=project("""{"availableOnline":false}""");val empty=project("""{"sku":""}""")
        assertEquals(4,setOf(absent.payload,nil.payload,no.payload,empty.payload).size)
        for(f in listOf(absent,nil,no,empty)) assertEquals(f,WebProductMetadataCodec.decode(f.payload))
        assertTrue(absent.presentFields.isEmpty());assertEquals(setOf("availableOnline"),nil.presentFields)
    }
    @Test fun sourceOrderDoesNotChangePayloadAndRecognizedFieldsRemainRetainedNotActivated() {
        val a=project("""{"sku":"x","sortOrder":"-2.5","imageUploadPending":true,"unit":"piece"}""")
        val b=project("""{"unit":"piece","imageUploadPending":true,"sortOrder":"-2.5","sku":"x"}""")
        assertEquals(a,b);assertEquals("-2.5",a.sortOrder)
        assertEquals(setOf("sku","imageUploadPending","unit"),a.retainedOnlyFields)
    }
    @Test fun unknownAndSecretFieldsAreNotAcceptedIntoMetadataEnvelope() {
        val f=project("""{"sku":"x","botToken":"secret","customField":17}""")
        assertEquals(setOf("sku"),f.presentFields);assertFalse(f.payload.contains("secret"))
        assertThrows(BackupFailure::class.java) {WebProductMetadataCodec.decode("""{"version":1,"values":{"botToken":"secret"}}""")}
    }
    @Test fun malformedTypesFutureVersionAndDuplicateKeysRefuse() {
        for(raw in listOf("""{"availableOnline":"false"}""","""{"sku":123}""","""{"recipeYieldManual":1}""","""{"sortOrder":true}""","""{"internalNote":{}}""","""{"tileSymbol":"1234"}""")) {
            assertThrows(BackupFailure::class.java) {project(raw)}
        }
        for(raw in listOf("""{"version":2,"values":{}}""","""{"version":1,"values":{"sku":"x","sku":"y"}}""","""{"version":1,"values":{},"extra":0}""")) {
            assertThrows(BackupFailure::class.java) {WebProductMetadataCodec.decode(raw)}
        }
    }
    @Test fun oversizedMetadataRefusesWithoutTruncation() {
        assertThrows(BackupFailure::class.java) {project("{\"internalNote\":\""+"я".repeat(40000)+"\"}")}
        assertThrows(BackupFailure::class.java) {WebProductMetadataCodec.decode(" ".repeat(WebProductMetadataCodec.MAX_BYTES+1))}
    }
}
