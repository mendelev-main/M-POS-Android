package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebImportedPaymentEvidenceTest {
    private fun source(): JSONObject {
        val j=JSONObject(javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {it.readText()})
        j.getJSONObject("currentOrderSession").put("createdAt",2).put("orderType","На месте").getJSONObject("paymentDraft").put("updatedAt",3)
        j.getJSONArray("products").getJSONObject(0).put("stockUnit","piece")
        return j
    }
    private fun doc(j: JSONObject)=WebBackupReader.read(j.toString().byteInputStream())
    private fun encode(j: JSONObject)=WebImportedPaymentEvidenceCodec.encode(doc(j))
    @Test fun paid40Given50Change10AndRemaining60RoundtripWithoutNativeCommandsOrPrivateFields() {
        val j=source();j.getJSONObject("currentOrderSession").put("customer",JSONObject().put("phone","private-contact")).put("botToken","private-token")
        val a=encode(j);val r=a.records.single()
        assertEquals(4000L,r.paid);assertEquals(6000L,r.remaining);assertEquals(5000L,r.parts[0].given);assertEquals(1000L,r.parts[0].change)
        assertEquals(a.records,WebImportedPaymentEvidenceCodec.decode(a.payload).records)
        assertFalse(a.payload.contains("private-contact"));assertFalse(a.payload.contains("private-token"));assertFalse(a.payload.contains("commandId"))
        assertTrue(r.blockers.contains("unadoptedSourceEvidence"));WebImportedPaymentEvidenceCodec.requireSource(a,doc(j))
        assertEquals(a.payload,encode(j).payload)
    }
    @Test fun missingTenderAndAllPaidArePreservedWithMandatoryRefusalMarkers() {
        val j=source();val draft=j.getJSONObject("currentOrderSession").getJSONObject("paymentDraft")
        draft.getJSONArray("parts").getJSONObject(0).remove("cashGiven");draft.getJSONArray("parts").getJSONObject(0).remove("change")
        val a=encode(j);assertNull(a.records.single().parts[0].given);assertTrue(a.records.single().blockers.contains("paidCashTenderUnknown"))
        draft.getJSONArray("parts").getJSONObject(1).put("paid",true)
        val b=encode(j);assertEquals(10000L,b.records.single().paid);assertEquals(0L,b.records.single().remaining)
        assertTrue(b.records.single().blockers.contains("allPartsPaidReceiptReconciliation"))
    }
    @Test fun alteredBindingOrSourceRequiresNewPreparationNotAReusedProof() {
        val j=source();val a=encode(j);val changed=JSONObject(a.payload)
        changed.getJSONArray("records").getJSONObject(0).put("bindingSha256","0".repeat(64))
        assertEquals("importedPaymentBinding",assertThrows(BackupFailure::class.java) {WebImportedPaymentEvidenceCodec.decode(changed.toString())}.kind)
        j.getJSONArray("products").getJSONObject(0).put("cost",21)
        assertThrows(BackupFailure::class.java) {WebImportedPaymentEvidenceCodec.requireSource(a,doc(j))}
    }
    @Test fun unknownFieldsPurposeVersionAndCoercedMoneyCannotImpersonateNativeAcknowledgement() {
        val a=encode(source())
        for(mutate in listOf<(JSONObject)->Unit>(
            {it.put("version",3)}, {it.put("sourceVersion",4294967297L)}, {it.put("purpose","native-confirmed")},
            {it.getJSONArray("records").getJSONObject(0).put("commandId","invented")},
            {it.getJSONArray("records").getJSONObject(0).getJSONArray("parts").getJSONObject(0).put("amount","4000")},
            {it.getJSONArray("records").getJSONObject(0).getJSONArray("parts").getJSONObject(0).put("amount",4000.5)},
            {it.getJSONArray("records").getJSONObject(0).put("blockers",JSONArray().put("unadoptedSourceEvidence"))}
        )) {val j=JSONObject(a.payload);mutate(j);assertThrows(BackupFailure::class.java) {WebImportedPaymentEvidenceCodec.decode(j.toString())}}
    }
    @Test fun duplicateOriginsLocationsWrongPartPositionsAndCashChangeRefuse() {
        val a=encode(source())
        for(mutate in listOf<(JSONObject)->Unit>(
            {it.getJSONArray("records").put(JSONObject(it.getJSONArray("records").getJSONObject(0).toString()))},
            {it.getJSONArray("records").getJSONObject(0).put("origin","web-open-"+"0".repeat(64))},
            {it.getJSONArray("records").getJSONObject(0).getJSONArray("parts").getJSONObject(1).put("position",0)},
            {it.getJSONArray("records").getJSONObject(0).getJSONArray("parts").getJSONObject(0).put("change",999)},
            {it.getJSONArray("records").getJSONObject(0).getJSONArray("parts").getJSONObject(0).put("paid",false)}
        )) {val j=JSONObject(a.payload);mutate(j);assertThrows(BackupFailure::class.java) {WebImportedPaymentEvidenceCodec.decode(j.toString())}}
    }
    @Test fun largeRecordSetsUseBoundedChunksWithHashAndStrictCompleteness() {
        val j=source();val parked=j.getJSONArray("parked")
        repeat(80) {parked.put(JSONObject(j.getJSONObject("currentOrderSession").toString()).put("id","p$it"))}
        val a=encode(j);val rows=WebImportedPaymentDocuments.encode(a)
        assertTrue(rows.size>2);assertTrue(rows.values.all {it.toByteArray().size<=256*1024})
        assertEquals(a.payload,WebImportedPaymentDocuments.decode(rows)!!.payload)
        val part=rows.keys.first {it!=WebImportedPaymentDocuments.KEY}
        for(bad in listOf(rows-part,rows-WebImportedPaymentDocuments.KEY,rows+(part to "{\"version\":1,\"content\":\"broken\"}"),rows+(WebImportedPaymentDocuments.KEY+":9999" to rows.getValue(part)))) {
            assertThrows(BackupFailure::class.java) {WebImportedPaymentDocuments.decode(bad)}
        }
        assertNull(WebImportedPaymentDocuments.decode(emptyMap()))
    }
    @Test fun snapshotPreservesBatchPricingAndPhysicalRequestsAcrossChunkRoundtrip() {
        val j=source();val products=j.getJSONArray("products")
        products.put(JSONObject().put("id","batch").put("type","composite").put("name","Batch").put("price",999).put("recipeUnit","kg").put("recipeYield",3)
            .put("components",JSONArray().put(JSONObject().put("productId","p").put("qty","0.0004"))))
        j.getJSONObject("currentOrderSession").getJSONArray("items").getJSONObject(0).put("productId","batch").put("price",100)
        val a=encode(j);val b=WebImportedPaymentDocuments.decode(WebImportedPaymentDocuments.encode(a))!!
        val frozen=JSONObject(b.records.single().cartSnapshot!!);val l=frozen.getJSONArray("lines").getJSONObject(0)
        assertEquals("recipe-batch",l.getString("basis"));assertEquals("100",l.getString("price"));assertEquals("3",l.getString("physicalPerSold"))
        assertEquals("3",l.getJSONArray("requests").getJSONObject(0).getString("qty"));assertEquals(4000L,b.records.single().paid);assertEquals(6000L,b.records.single().remaining)
    }
    @Test fun version1RemainsReadableWithoutInventingMissingCart() {
        val old=JSONObject(encode(source()).payload).put("version",1)
        old.getJSONArray("records").getJSONObject(0).remove("cartSnapshot")
        val a=WebImportedPaymentEvidenceCodec.decode(old.toString());assertNull(a.records.single().cartSnapshot);assertEquals(4000L,a.records.single().paid)
    }
    @Test fun rewrittenSnapshotStillRequiresTheVerifiedOriginalSource() {
        val j=source();val raw=JSONObject(encode(j).payload);val r=raw.getJSONArray("records").getJSONObject(0)
        val frozen=JSONObject(r.getString("cartSnapshot"));frozen.getJSONArray("lines").getJSONObject(0).put("price","200")
        val text=frozen.toString();r.put("cartSnapshot",text).put("bindingSha256",WebLegacyHistoryArchiveCodec.digest(text))
        val changed=WebImportedPaymentEvidenceCodec.decode(raw.toString())
        assertThrows(BackupFailure::class.java) {WebImportedPaymentEvidenceCodec.requireSource(changed,doc(j))}
        frozen.put("extra","unknown");assertThrows(BackupFailure::class.java) {WebFrozenCartSnapshot.validate(frozen.toString())}
    }
    @Test fun noSourceDraftNeverCreatesAcceptedPayments() {
        val j=source();j.getJSONObject("currentOrderSession").remove("paymentDraft")
        assertTrue(encode(j).records.isEmpty())
    }
}
