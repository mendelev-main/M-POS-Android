package com.mendelev.mpos.domain.backup

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class WebPaymentMappingTest {
    private fun source()=JSONObject(javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {it.readText()})
    private fun mapped(j: JSONObject)=WebPaymentMapping.map(WebBackupReader.read(j.toString().byteInputStream()))
    private fun draft(j: JSONObject)=j.getJSONObject("currentOrderSession").getJSONObject("paymentDraft")
    @Test fun paidCash40Given50Change10KeepsRemainingCard60WithoutMovingCash() {
        val j=source();val original=j.toString();val facts=mapped(j).single()
        assertEquals("current",facts.location);assertEquals(10000L,facts.total)
        assertEquals(4000L,facts.paid);assertEquals(6000L,facts.unpaid)
        assertEquals(4000L,facts.paidCash);assertEquals(0L,facts.paidCard)
        assertEquals(5000L,facts.parts[0].given);assertEquals(1000L,facts.parts[0].change)
        assertFalse(facts.parts[1].paid);assertTrue(facts.blockers.isEmpty());assertEquals(original,j.toString())
        assertEquals(facts,mapped(j).single())
    }
    @Test fun missingPaidTenderRemainsUnknownAndBlocksFutureActivation() {
        val j=source();val part=draft(j).getJSONArray("parts").getJSONObject(0)
        part.remove("cashGiven");part.remove("change")
        val f=mapped(j).single();assertNull(f.parts[0].given);assertNull(f.parts[0].change)
        assertEquals(4000L,f.paidCash);assertEquals(setOf("paidCashTenderUnknown"),f.blockers)
    }
    @Test fun parkedAndCurrentPaymentsRemainSeparateNotMerged() {
        val j=source();val parked=JSONObject(j.getJSONObject("currentOrderSession").toString())
        parked.getJSONObject("paymentDraft").getJSONArray("parts").getJSONObject(0).put("method","card").put("cashGiven",JSONObject.NULL).put("change",JSONObject.NULL)
        j.put("parked",JSONArray().put(parked))
        val f=mapped(j);assertEquals(listOf("current","parked:0"),f.map {it.location})
        assertEquals(listOf(4000L,4000L),f.map {it.paid});assertEquals(4000L,f[1].paidCard);assertEquals(0L,f[1].paidCash)
    }
    @Test fun allPaidNeedsReceiptReconciliationInsteadOfChargingAgain() {
        val j=source();draft(j).getJSONArray("parts").getJSONObject(1).put("paid",true)
        val f=mapped(j).single();assertEquals(10000L,f.paid);assertEquals(0L,f.unpaid)
        assertEquals(setOf("allPartsPaidReceiptReconciliation"),f.blockers)
    }
    @Test fun selectedLoyaltyIsNeverSilentlyRemovedFromPaidOrder() {
        val j=source();j.getJSONObject("currentOrderSession").put("loyaltyRedemptions",JSONObject().put("reward",1))
        assertTrue(mapped(j).single().blockers.contains("paidLoyaltyEntitlement"))
        j.getJSONObject("currentOrderSession").getJSONObject("loyaltyRedemptions").put("reward",0)
        assertTrue(mapped(j).single().blockers.isEmpty())
    }
    @Test fun sourceMoneyIsExactNotRoundedOrCoercedToZero() {
        for(value in listOf("40.001","-40","NaN","1e999",true)) {
            val j=source();draft(j).getJSONArray("parts").getJSONObject(0).put("amount",value)
            assertThrows(BackupFailure::class.java) {mapped(j)}
        }
        val j=source();draft(j).getJSONArray("parts").getJSONObject(0).put("amount","4e1")
        assertEquals(4000L,mapped(j).single().paid)
    }
    @Test fun noDraftDoesNotCreatePaymentsAndUnknownDraftVersionRefuses() {
        val j=source();j.getJSONObject("currentOrderSession").put("paymentDraft",JSONObject.NULL)
        assertTrue(mapped(j).isEmpty())
        val invalid=source();draft(invalid).put("version",2)
        assertThrows(BackupFailure::class.java) {mapped(invalid)}
    }
}
