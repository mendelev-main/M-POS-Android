package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebReceiptHistoryTest {
    private fun receipt()=JSONObject().put("id","sale").put("timestamp",1000).put("shiftId","yesterday").put("employeeId","anna").put("employeeName","Анна тогда")
        .put("total",100).put("deliveryFee",0).put("method","split").put("payments",JSONArray().put(JSONObject().put("method","cash").put("amount",40).put("cashGiven",50).put("change",10)).put(JSONObject().put("method","card").put("amount",60)))
        .put("items",JSONArray().put(JSONObject().put("productId","deleted-product").put("name","Название тогда").put("category","Категория тогда").put("qty","1.5").put("price","66.666666666666").put("cost","2.35")))
        .put("stockConsumption",JSONObject().put("version",1).put("items",JSONArray().put(JSONObject().put("productId","ingredient").put("qty","0.0004"))))
    private fun doc(vararg rows: JSONObject): WebBackupDocument=WebBackupReader.read(JSONObject().put("version",1).put("products",JSONArray()).put("employees",JSONArray()).put("shifts",JSONArray()).put("orders",JSONArray(rows.toList())).toString().byteInputStream())
    private fun mapped(r: JSONObject)=WebReceiptHistory.map(doc(r)).single()
    @Test fun saleYesterdayAndRefundTodayRemainTwoEventsAndKeepHistoricalAuthor() {
        val zone=java.time.ZoneId.of("Europe/Minsk")
        val first=java.time.LocalDate.parse("2026-10-30").atStartOfDay(zone).toInstant().toEpochMilli()
        val next=java.time.LocalDate.parse("2026-10-31").atStartOfDay(zone).toInstant().toEpochMilli()
        val saleTime=first+12*60*60*1000L;val refundTime=next+12*60*60*1000L
        val r=receipt().put("timestamp",saleTime).put("returnedAt",refundTime).put("returnedShiftId","today").put("returnAmount",100)
        val f=mapped(r);val e=WebReceiptHistory.events(listOf(f))
        assertEquals(listOf(saleTime,refundTime),e.events.map {it.timestamp});assertEquals(listOf(10000L,-10000L),e.events.map {it.amount})
        assertEquals(10000L,e.events.filter {it.timestamp>=first && it.timestamp<next}.sumOf {it.amount})
        assertEquals(-10000L,e.events.filter {it.timestamp>=next && it.timestamp<next+24*60*60*1000L}.sumOf {it.amount})
        assertTrue(e.gaps.isEmpty());assertEquals("anna",f.sourceActorId);assertEquals("Анна тогда",f.actorName)
        assertEquals("yesterday",f.sourceShiftId);assertEquals("today",f.sourceReturnShiftId)
        assertTrue(f.coverage.contains("refundCashMovementReconciliationPending"))
    }
    @Test fun deletedProductsDoNotLoseHistoricalLinesOrInferCurrentStockUnitsAndRecipes() {
        val f=mapped(receipt());val line=f.lines.single();val consumption=f.consumption!!.single()
        assertEquals("deleted-product",line.sourceProductId);assertEquals("Название тогда",line.name);assertEquals("Категория тогда",line.category)
        assertEquals("1.5",line.quantity);assertEquals("2.35",line.cost);assertNull(line.unit)
        assertEquals("0.0004",consumption.quantity);assertNull(consumption.unit)
        assertTrue(f.coverage.containsAll(setOf("lineUnitUnknown","consumptionUnitUnknown","nativeReceiptGraphUnavailable","sourceUnitCostOnly")))
    }
    @Test fun exactMixedTenderAndLegacyHeaderDoNotInventPaidCommandsOrChange() {
        val r=receipt();val f=mapped(r)
        assertEquals(listOf(4000L,6000L),f.payments!!.map {it.amount});assertEquals(5000L,f.payments[0].given);assertEquals(1000L,f.payments[0].change)
        r.remove("payments");r.put("method","cash");val legacy=mapped(r)
        assertEquals(10000L,legacy.payments!!.single().amount);assertNull(legacy.payments.single().given);assertNull(legacy.payments.single().change)
        assertTrue(legacy.coverage.containsAll(setOf("paymentFromLegacyHeader","cashTenderUnknown")))
    }
    @Test fun missingHistoricalFieldsStayUnknownAndIncompleteRefundProducesGapNotZero() {
        val r=receipt().put("returnedAt",2000);r.remove("timestamp");r.remove("employeeName");r.remove("stockConsumption")
        r.getJSONArray("items").getJSONObject(0).remove("cost")
        val f=mapped(r);assertNull(f.timestamp);assertNull(f.actorName);assertNull(f.consumption);assertNull(f.lines.single().cost);assertNull(f.returnAmount)
        val e=WebReceiptHistory.events(listOf(f));assertTrue(e.events.isEmpty());assertEquals(setOf("sale","refund"),e.gaps.map {it.kind}.toSet())
    }
    @Test fun deliveryRefundNeedsExplicitReconciliationInsteadOfSynthesizingCourierCredit() {
        val r=receipt().put("deliveryFee",5).put("returnedAt",2000).put("returnedShiftId","today").put("returnAmount",100)
        val f=mapped(r);assertEquals(500L,f.delivery)
        assertTrue(f.coverage.contains("deliveryRefundReconciliationPending"));assertEquals(-10000L,WebReceiptHistory.events(listOf(f)).events.last().amount)
    }
    @Test fun inconsistentPaymentsConsumptionVersionsRefundDatesAndPrecisionsRefuse() {
        for(edit in listOf<(JSONObject)->Unit>(
            {it.getJSONArray("payments").getJSONObject(0).put("amount","40.001")},
            {it.getJSONArray("payments").getJSONObject(0).put("change",9)},
            {it.getJSONArray("payments").getJSONObject(0).put("paid",false)},
            {it.getJSONObject("stockConsumption").put("version",2)},
            {it.getJSONObject("stockConsumption").getJSONArray("items").put(JSONObject().put("productId","ingredient").put("qty",1))},
            {it.put("returnedAt",999)},
            {it.put("returnAmount",100)},
            {it.put("currency","USD")},
            {it.put("method","card")},
            {it.getJSONArray("items").getJSONObject(0).put("qty","-1")})) {
            val r=receipt();edit(r);assertThrows(BackupFailure::class.java) {mapped(r)}
        }
    }
    @Test fun partialLegacyRefundAndDiscountDetailsAreVisibleNotPromotedToNativeRefund() {
        val r=receipt().put("returnedAt",2000).put("returnAmount",40)
        r.getJSONArray("items").getJSONObject(0).put("discountType","percent").put("discountValue",10)
        val f=mapped(r);assertTrue(f.coverage.contains("partialOrMismatchedRefund"))
        assertEquals(setOf("discountType","discountValue"),f.lines.single().deferredFields)
        assertEquals(-4000L,WebReceiptHistory.events(listOf(f)).events.last().amount)
    }
    @Test fun webFloatingPointDecimalTailsArePreservedAsHistoryNotRoundedToNativeStockScale() {
        val r=receipt();r.getJSONArray("items").getJSONObject(0).put("cost","3.333333333333333")
        r.getJSONObject("stockConsumption").getJSONArray("items").getJSONObject(0).put("qty","0.30000000000000004")
        val f=mapped(r);assertEquals("3.333333333333333",f.lines.single().cost)
        assertEquals("0.30000000000000004",f.consumption!!.single().quantity)
        assertTrue(f.coverage.contains("legacyDecimalPrecision"))
    }
    @Test fun deterministicMappingDoesNotMutateSourceAndReceiptIdentityIsScoped() {
        val r=receipt();val d=doc(r);val before=r.toString()
        val a=WebReceiptHistory.map(d);assertEquals(a,WebReceiptHistory.map(d));assertEquals(before,r.toString())
        assertNotEquals(WebIdentity.mapped("product","sale"),a.single().id)
        assertFalse(a.single().toString().contains("Анна"))
        assertThrows(BackupFailure::class.java) {WebReceiptHistory.events(a+a)}
    }
}
