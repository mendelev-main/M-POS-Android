package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebOpenOperationsTest {
    private fun source(): JSONObject {
        val j=JSONObject(javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {it.readText()})
        session(j).put("orderType","На месте").put("createdAt",2).put("updatedAt",3)
        draft(j).put("updatedAt",3)
        j.getJSONArray("shifts").getJSONObject(0).put("employeeName","Кассир")
        return j
    }
    private fun session(j: JSONObject)=j.getJSONObject("currentOrderSession")
    private fun draft(j: JSONObject)=session(j).getJSONObject("paymentDraft")
    private fun item(j: JSONObject)=session(j).getJSONArray("items").getJSONObject(0)
    private fun map(j: JSONObject)=WebOpenOperations.map(WebBackupReader.read(j.toString().byteInputStream()))
    private fun noPayment(j: JSONObject) {session(j).remove("paymentDraft")}
    @Test fun paid40Given50Change10KeepsFrozen100AndUnpaid60WithoutRepricingOrWrites() {
        val j=source();j.getJSONArray("products").getJSONObject(0).put("price",200).put("name","Новое название")
        val original=j.toString();val a=map(j).single()
        assertEquals(10000L,a.comparedTotal);assertEquals(10000L,a.savedTotal)
        assertEquals(4000L,a.payment!!.paidCash);assertEquals(6000L,a.payment.unpaid)
        assertEquals(5000L,a.payment.parts[0].given);assertEquals(1000L,a.payment.parts[0].change)
        assertEquals("Синтетический товар",a.lines.single().name)
        assertEquals(WebIdentity.mapped("shift","s"),a.candidateShiftId)
        assertEquals(WebIdentity.mapped("employee","cashier"),a.candidateActorId)
        assertEquals(setOf("operationalCartMappingPending","cashHistoryReconciliationPending","shiftBindingInferred","sourceQuoteNotPersisted"),a.blockers)
        assertEquals(a,map(j).single());assertEquals(original,j.toString());assertFalse(a.toString().contains("Синтетический"))
    }
    @Test fun fixedDiscountPerUnitAndDeliveryProduce23WithoutDoubleAddingModifierPrice() {
        val j=source();noPayment(j)
        j.put("discounts",JSONArray().put(JSONObject().put("id","d").put("name","Скидка").put("type","fixed").put("value",1)))
        item(j).put("price",10).put("qty",2).put("discountId","d").put("basePrice",8).put("manualPrice",true)
            .put("selectedModifiers",JSONArray().put(JSONObject().put("groupId","g").put("optionId","o").put("productId","m").put("name","Добавка").put("qty","0.0004").put("priceDelta",2)))
        session(j).put("orderType","Доставка").put("deliveryFee",5).put("deliveryTariffSelected",true)
        val a=map(j).single();assertEquals(1800L,a.lines.single().comparedNet);assertEquals(2300L,a.comparedTotal)
        assertEquals("0.0004",a.lines.single().modifiers.single().quantity);assertEquals("8",a.lines.single().basePrice)
        assertEquals("1",a.lines.single().discount!!.value);assertNull(a.payment)
    }
    @Test fun rowHalfUpComparisonDoesNotRewriteSourceTotalOrAcceptedParts() {
        val j=source();item(j).put("price","0.05").put("qty",3).put("discountId","d")
        j.put("discounts",JSONArray().put(JSONObject().put("id","d").put("type","percent").put("value",10)))
        session(j).getJSONArray("items").put(JSONObject(item(j).toString()).put("cartLineId","b"))
        // Web aggregate unrounded rows: .135+.135=.27; native policy compares .14+.14=.28.
        draft(j).put("totalCents",27);draft(j).getJSONArray("parts").getJSONObject(0).put("amount","0.10").put("cashGiven","0.10").put("change",0)
        draft(j).getJSONArray("parts").getJSONObject(1).put("amount","0.17")
        val a=map(j).single();assertEquals(28L,a.comparedTotal);assertEquals(27L,a.savedTotal)
        assertEquals(10L,a.payment!!.paid);assertEquals(17L,a.payment.unpaid);assertTrue(a.blockers.contains("quoteTotalMismatch"))
    }
    @Test fun missingDiscountPriceAndQuantityRemainUnknownNotZeroOrCatalogFallback() {
        val j=source();item(j).put("discountId","deleted-discount")
        val a=map(j).single();assertNull(a.comparedTotal);assertEquals(4000L,a.payment!!.paid)
        assertTrue(a.blockers.contains("discountDefinitionUnknown"));assertEquals(10000L,a.savedTotal)
        item(j).remove("price");item(j).remove("qty")
        val b=map(j).single();assertNull(b.lines.single().price);assertNull(b.lines.single().quantity);assertNull(b.comparedTotal)
    }
    @Test fun selectedLoyaltyNeverBecomesZeroDiscountOrChargesAnIncreasedBalance() {
        val j=source();session(j).put("loyaltyRedemptions",JSONObject().put("reward",1))
        val a=map(j).single();assertNull(a.comparedTotal);assertEquals(6000L,a.payment!!.unpaid)
        assertTrue(a.blockers.containsAll(setOf("paidLoyaltyEntitlement","loyaltyQuoteUnavailable")))
        session(j).put("loyaltyRedemptions",JSONObject.NULL);assertEquals(10000L,map(j).single().comparedTotal)
    }
    @Test fun sourceFileScopedOriginsSeparateCurrentAndParkedAndNeverImpersonateCommandIds() {
        val j=source();val parked=JSONObject(session(j).toString()).put("id","parked-a").put("total",100).put("employeeName","Автор отложенного").put("timestamp",2)
        j.put("parked",JSONArray().put(parked));val a=map(j)
        assertEquals(2,a.size);assertNotEquals(a[0].origin,a[1].origin)
        assertEquals("Автор отложенного",a[1].sourceActorName);assertEquals(2L,a[1].sourceTimestamp)
        assertTrue(a[1].blockers.contains("parkedPaidDraftNotProducedByPinnedWeb"))
        assertEquals(a,map(j));assertTrue(a.all {it.origin.startsWith("web-open-")})
        j.put("exportedAt",2);assertNotEquals(a[0].origin,map(j)[0].origin)
        j.getJSONArray("parked").put(JSONObject(parked.toString()))
        assertEquals("openIdentity",assertThrows(BackupFailure::class.java) {map(j)}.kind)
    }
    @Test fun allPaidAndPossibleHistoryOverlapRemainExplicitNeverDroppedOrChargedAgain() {
        val j=source();draft(j).getJSONArray("parts").getJSONObject(1).put("paid",true)
        j.getJSONArray("orders").put(JSONObject().put("id","old").put("shiftId","s").put("timestamp",3).put("total",100).put("method","card").put("items",JSONArray()))
        val a=map(j).single();assertEquals(10000L,a.payment!!.paid);assertEquals(0L,a.payment.unpaid)
        assertTrue(a.blockers.containsAll(setOf("allPartsPaidReceiptReconciliation","possibleCompletedReceiptOverlap")))
    }
    @Test fun absentOrLaterShiftNeverAssignsThePartToAnUnprovenCashier() {
        val j=source();j.getJSONArray("shifts").getJSONObject(0).put("openedAt",4)
        val a=map(j).single();assertTrue(a.blockers.contains("sessionShiftTimeUnproven"));assertTrue(a.blockers.contains("paymentShiftTimeUnproven"))
        j.getJSONArray("shifts").getJSONObject(0).put("status","closed").put("closedAt",5)
        val b=map(j).single();assertNull(b.candidateShiftId);assertNull(b.candidateActorId);assertTrue(b.blockers.contains("openShiftUnavailable"))
    }
    @Test fun emptyUnpaidSessionsIgnoredButEmptyPaidEvidenceSurvivesAndCannotResume() {
        val j=source();session(j).put("items",JSONArray());val a=map(j).single()
        assertEquals(4000L,a.payment!!.paid);assertNull(a.comparedTotal);assertTrue(a.blockers.contains("paidSessionHasNoItems"))
        noPayment(j);assertTrue(map(j).isEmpty())
    }
    @Test fun malformedMoneyDuplicateRowsUnknownCurrencyAndTimeRefuse() {
        for(change in listOf<(JSONObject)->Unit>(
            {item(it).put("price",true)}, {item(it).put("qty",-1)}, {item(it).put("qty",0)},
            {item(it).put("price","1e128")}, {session(it).put("currency","USD")}, {draft(it).put("updatedAt",1)},
            {val line=item(it).put("cartLineId","same");session(it).getJSONArray("items").put(JSONObject(line.toString()))}
        )) {val j=source();change(j);assertThrows(BackupFailure::class.java) {map(j)}}
    }
    @Test fun discountCapsAndLegacyPrecisionAreExplicitWithoutQuantityRounding() {
        val j=source();noPayment(j);item(j).put("price",10).put("qty",2).put("discountId","d")
        val discount=JSONObject().put("id","d").put("type","percent").put("value",150)
        j.put("discounts",JSONArray().put(discount));assertEquals(0L,map(j).single().comparedTotal)
        discount.put("type","fixed").put("value",15);assertEquals(0L,map(j).single().comparedTotal)
        item(j).remove("discountId");item(j).put("price",100).put("qty","0.30000000000000004").put("unit","kg").put("category","Историческая")
        val a=map(j).single();assertEquals("0.30000000000000004",a.lines.single().quantity)
        assertEquals(3000L,a.comparedTotal);assertEquals("kg",a.lines.single().unit);assertEquals("Историческая",a.lines.single().category)
        assertTrue(a.blockers.contains("quantityPrecisionNeedsReconciliation"))
    }
    @Test fun duplicateDiscountAndModifierIdentitiesCannotProduceAmbiguousQuotes() {
        val j=source();j.put("discounts",JSONArray().put(JSONObject().put("id","d")).put(JSONObject().put("id","d")))
        assertEquals("openIdentity",assertThrows(BackupFailure::class.java) {map(j)}.kind)
        j.put("discounts",JSONArray())
        val modifier=JSONObject().put("groupId","g").put("optionId","o").put("productId","m").put("qty",1).put("priceDelta",2)
        item(j).put("selectedModifiers",JSONArray().put(modifier).put(JSONObject(modifier.toString())))
        assertEquals("openModifier",assertThrows(BackupFailure::class.java) {map(j)}.kind)
    }
    @Test fun deliveryRequiresConfirmedTariffAndParkedTotalIsComparedIndependently() {
        val j=source();noPayment(j);val p=JSONObject(session(j).toString()).put("id","p").put("total",99).put("orderType","Доставка").put("deliveryFee",5)
        j.put("parked",JSONArray().put(p));session(j).put("items",JSONArray())
        val a=map(j).single();assertEquals(10500L,a.comparedTotal);assertEquals(9900L,a.savedTotal)
        assertTrue(a.blockers.containsAll(setOf("deliveryTariffUnconfirmed","quoteTotalMismatch")))
    }
}
