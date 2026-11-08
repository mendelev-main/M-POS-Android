package com.mendelev.mpos.domain.backup

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.util.Base64

class WebBackupReaderTest {
    private fun fixture()=javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {it.readText()}
    private fun read(text: String)=WebBackupReader.read(text.byteInputStream())
    private fun bad(text: String)=assertThrows(BackupFailure::class.java) {read(text)}
    @Test fun pinnedSourceExportPreservesOpenShiftAndExactPaidPartialWithoutChangingInput() {
        val text=fixture();val doc=read(text)
        assertEquals(13,doc.preview.version);assertEquals(1,doc.preview.openShifts);assertEquals(1,doc.preview.paymentDrafts)
        assertEquals(4000.toBigInteger(),doc.preview.paidMinor);assertEquals(6000.toBigInteger(),doc.preview.unpaidMinor)
        assertEquals(1,doc.preview.sections["products"]);assertFalse(doc.toString().contains("Синтетический"))
        assertEquals(text,fixture())
    }
    @Test fun legacyProfilesAndUnknownVersionsNeverReuseNativeCurrentState() {
        for(version in 1..13) {
            val j=if(version<12) JSONObject().put("version",version).put("products",org.json.JSONArray()).put("employees",org.json.JSONArray()).put("shifts",org.json.JSONArray()).put("orders",org.json.JSONArray()) else JSONObject(fixture()).put("version",version)
            assertEquals(version,read(j.toString()).preview.version)
            if(version<12) assertFalse(read(j.toString()).preview.currentSessionPresent)
        }
        assertEquals("version",bad(JSONObject(fixture()).put("version",14).toString()).kind)
        bad(JSONObject(fixture()).put("version",Long.MAX_VALUE).toString())
        bad(JSONObject(fixture()).apply {remove("receivingDraft")}.toString())
        bad(JSONObject(fixture()).put("parked",JSONObject()).toString())
    }
    @Test fun strictJsonRejectsDuplicatesUnsafeIdsTrailingBytesAndMalformedUtf8() {
        for(text in listOf("{\"products\":[],\"\\u0070roducts\":[]}","/*comment*/{}","{'products':[]}",fixture()+" true","{\"version\":01}","{\"version\":NaN}")) bad(text)
        assertThrows(BackupFailure::class.java) {WebBackupReader.read(byteArrayOf(0xc3.toByte(),0x28).inputStream())}
        val j=JSONObject(fixture());j.getJSONArray("products").getJSONObject(0).put("id",9007199254740992L);bad(j.toString())
        j.getJSONArray("products").getJSONObject(0).put("id","9007199254740992");assertEquals(1,read(j.toString()).preview.sections["products"])
        val duplicate=JSONObject(fixture());duplicate.getJSONArray("products").put(JSONObject(duplicate.getJSONArray("products").getJSONObject(0).toString()))
        assertEquals("duplicate",bad(duplicate.toString()).kind)
    }
    @Test fun partialPaymentsRejectMismatchChangeAndHiddenRounding() {
        for(change in listOf<(JSONObject)->Unit>(
            {it.getJSONObject("currentOrderSession").getJSONObject("paymentDraft").put("totalCents",9999)},
            {it.getJSONObject("currentOrderSession").getJSONObject("paymentDraft").getJSONArray("parts").getJSONObject(0).put("change",9)},
            {it.getJSONObject("currentOrderSession").getJSONObject("paymentDraft").getJSONArray("parts").getJSONObject(0).put("amount","40.0000001")})) {
            val j=JSONObject(fixture());change(j);assertEquals("payment",bad(j.toString()).kind)
        }
    }
    @Test fun photoReferencesAndPrivateFieldsAreNotSilentlyDroppedOrExposed() {
        val j=JSONObject(fixture());val id="00000000-0000-0000-0000-000000000001"
        j.getJSONArray("products").getJSONObject(0).put("localImageId",id)
        assertEquals("missingPhoto",bad(j.toString()).kind)
        j.put("productImages",JSONObject().put(id,Base64.getEncoder().encodeToString(byteArrayOf(1,2,3)))).put("imageCount",1)
        j.getJSONObject("network").put("deviceKey","SENTINEL-NOT-A-SECRET")
        val doc=read(j.toString());assertEquals(1,doc.preview.imageCount);assertEquals(3L,doc.preview.photoBytes)
        assertFalse(doc.toString().contains("SENTINEL"));assertFalse(doc.preview.toString().contains("SENTINEL"))
        j.getJSONObject("productImages").put(id,"!not-base64!");assertEquals("photo",bad(j.toString()).kind)
        val inline=JSONObject(fixture());inline.getJSONArray("products").getJSONObject(0).put("image","data:image/jpeg;base64,AQID")
        assertEquals(1,read(inline.toString()).preview.inlineImages);assertEquals(0,read(inline.toString()).preview.externalImages)
        inline.getJSONArray("products").getJSONObject(0).put("image","https://example.invalid/SENTINEL")
        val remote=read(inline.toString());assertEquals(1,remote.preview.externalImages);assertFalse(remote.preview.toString().contains("SENTINEL"))
    }
    @Test fun boundedInputAndDepthRefuseOversizeBeforeAnyRestore() {
        var count=0
        val stream=object: InputStream() {
            override fun read(): Int {count++;return 32}
            override fun read(b: ByteArray,off: Int,len: Int): Int {b.fill(32,off,off+len);count+=len;return len}
        }
        assertEquals("size",assertThrows(BackupFailure::class.java) {WebBackupReader.read(stream)}.kind)
        assertTrue(count<=WebBackupReader.MAX_SOURCE_BYTES+8192)
        bad("[".repeat(66)+"0"+"]".repeat(66));bad("{\"version\":1e10000}")
    }
}
