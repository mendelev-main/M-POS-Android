package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebCashReconciliationTest {
    private fun source(): JSONObject {
        val j=JSONObject(javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {it.readText()})
        shift(j).put("openedAt",10).put("employeeName","Кассир")
        session(j).put("createdAt",11).put("updatedAt",12).put("orderType","На месте")
        session(j).getJSONObject("paymentDraft").put("updatedAt",12)
        return j
    }
    private fun shift(j: JSONObject)=j.getJSONArray("shifts").getJSONObject(0)
    private fun session(j: JSONObject)=j.getJSONObject("currentOrderSession")
    private fun noDraft(j: JSONObject) {session(j).remove("paymentDraft");session(j).put("items",JSONArray())}
    private fun compare(j: JSONObject)=WebCashReconciliation.compare(WebBackupReader.read(j.toString().byteInputStream()))
    private fun move(id: String,type: String,amount: Any,subtype: String="manual",time: Long=12)=JSONObject().put("id",id).put("type",type).put("amount",amount).put("subtype",subtype).put("timestamp",time)
    private fun receipt(id: String="r",cash: Int=10,card: Int=0,shift: String="s",time: Long=11): JSONObject {
        val p=JSONArray();if(cash>0)p.put(JSONObject().put("method","cash").put("amount",cash).put("cashGiven",cash).put("change",0))
        if(card>0)p.put(JSONObject().put("method","card").put("amount",card))
        return JSONObject().put("id",id).put("shiftId",shift).put("timestamp",time).put("total",cash+card).put("method",if(card==0)"cash" else if(cash==0)"card" else "split").put("payments",p).put("deliveryFee",0).put("items",JSONArray())
    }
    @Test fun pendingCash40Given50Change10IsSeparateFromSourceDisplayAndNeverAddedAs50() {
        val j=source();val original=j.toString();val a=compare(j);val s=a.shifts.single()
        assertEquals(0L,s.sourceDisplayedCash);assertEquals(0L,s.cashFromRecordedFacts)
        assertEquals(4000L,s.pendingCash);assertEquals(0L,s.pendingCard);assertEquals(4000L,s.includingPendingCash)
        assertEquals(0L,s.sourceCompensationDifference);assertTrue(a.unassignedOrigins.isEmpty())
        assertEquals(1,s.pendingOrigins.size);assertTrue(a.blockers.contains("nativePaidProvenancePending"))
        assertEquals(a,compare(j));assertEquals(original,j.toString());assertFalse(a.toString().contains("Кассир"))
    }
    @Test fun manualMovementsAndCompletedSalesAreCountedOnceWithPendingCashSeparate() {
        val j=source();shift(j).put("openingCash",100).put("cashMovements",JSONArray().put(move("in","deposit",20)).put(move("out","withdrawal",5)))
        j.getJSONArray("orders").put(receipt(cash=10,card=15))
        val s=compare(j).shifts.single();assertEquals(1000L,s.completedCash);assertEquals(2000L,s.deposits);assertEquals(500L,s.withdrawals)
        assertEquals(12500L,s.sourceDisplayedCash);assertEquals(12500L,s.cashFromRecordedFacts);assertEquals(16500L,s.includingPendingCash)
    }
    @Test fun sameShiftMixedRefundUsesOriginalCash40AndRecordedWithdrawalExactlyOnce() {
        val j=source();noDraft(j);shift(j).put("openingCash",100).put("cashMovements",JSONArray().put(move("return","withdrawal",40,"refund",13)))
        j.getJSONArray("orders").put(receipt(cash=40,card=60).put("returnedAt",13).put("returnedShiftId","s").put("returnAmount",100))
        val s=compare(j).shifts.single();assertEquals(4000L,s.completedCash);assertEquals(0L,s.activeReceiptCash)
        assertEquals(4000L,s.refundWithdrawals);assertEquals(10000L,s.cashFromRecordedFacts);assertEquals(10000L,s.sourceDisplayedCash)
        assertFalse(s.blockers.contains("refundMovementMismatch"));assertEquals(0L,s.sourceCompensationDifference)
    }
    @Test fun crossShiftRefundPreservesYesterdaySaleAndExposesSourceCompensationToday() {
        val j=source();noDraft(j);shift(j).put("openingCash",20).put("cashMovements",JSONArray().put(move("return","withdrawal",10,"refund",12)))
        j.getJSONArray("shifts").put(JSONObject().put("id","old").put("employeeId","cashier").put("employeeName","Вчерашний").put("status","closed").put("openedAt",1).put("closedAt",9).put("openingCash",0).put("countedCash",10))
        j.getJSONArray("orders").put(receipt(shift="old",time=2).put("returnedAt",12).put("returnedShiftId","s").put("returnAmount",10))
        val a=compare(j);val today=a.shifts.first {it.sourceShiftId=="s"};val yesterday=a.shifts.first {it.sourceShiftId=="old"}
        assertEquals(2000L,today.sourceDisplayedCash);assertEquals(1000L,today.cashFromRecordedFacts)
        assertEquals(-1000L,today.sourceCompensationDifference);assertTrue(today.blockers.contains("sourceRefundCompensationDifference"))
        assertEquals(1000L,yesterday.completedCash);assertEquals(1000L,yesterday.cashFromRecordedFacts);assertEquals(0L,yesterday.countedDifference)
        assertEquals(0L,yesterday.sourceDisplayedCash);assertFalse(today.blockers.contains("refundMovementMismatch"))
    }
    @Test fun deliveryRefundDoesNotInventHistoricalCourierCreditEvenWhenNewNativePolicyDiffers() {
        val j=source();noDraft(j);shift(j).put("openingCash",10).put("cashMovements",JSONArray().put(move("courier","withdrawal",5,"delivery",11)).put(move("return","withdrawal",25,"refund",13)))
        j.getJSONArray("orders").put(receipt(cash=25).put("deliveryFee",5).put("returnedAt",13).put("returnedShiftId","s").put("returnAmount",25))
        val s=compare(j).shifts.single();assertEquals(500L,s.deliveryWithdrawals);assertEquals(3000L,s.withdrawals)
        assertEquals(500L,s.cashFromRecordedFacts);assertEquals(1000L,s.openingCash)
        assertFalse(s.blockers.contains("deliveryMovementMismatch"));assertEquals(500L,s.sourceDisplayedCash)
    }
    @Test fun unknownAllocationsAndOpeningCashDoNotBecomeZero() {
        val j=source();noDraft(j);j.getJSONArray("orders").put(receipt().put("method","split").put("payments",JSONObject.NULL))
        val s=compare(j).shifts.single();assertNull(s.completedCash);assertNull(s.cashFromRecordedFacts);assertNull(s.includingPendingCash);assertTrue(s.blockers.contains("receiptCashUnknown"))
        j.put("orders",JSONArray());shift(j).remove("openingCash");assertNull(compare(j).shifts.single().cashFromRecordedFacts)
    }
    @Test fun missingOrDuplicateRefundEvidenceIsFlaggedWithoutSynthesizingOrDeletingCashMoves() {
        val j=source();noDraft(j);shift(j).put("openingCash",100)
        j.getJSONArray("orders").put(receipt().put("returnedAt",13).put("returnedShiftId","s").put("returnAmount",10))
        val missing=compare(j).shifts.single();assertEquals(11000L,missing.cashFromRecordedFacts);assertTrue(missing.blockers.contains("refundMovementMismatch"));assertEquals(0L,missing.refundWithdrawals)
        j.getJSONArray("orders").put(receipt(id="r2").put("returnedAt",13).put("returnedShiftId","s").put("returnAmount",10))
        shift(j).put("cashMovements",JSONArray().put(move("r1","withdrawal",10,"refund",13)).put(move("r2","withdrawal",10,"refund",13)))
        val duplicate=compare(j).shifts.single();assertEquals(10000L,duplicate.cashFromRecordedFacts)
        assertTrue(duplicate.blockers.contains("refundMovementLinkAmbiguous"));assertFalse(duplicate.blockers.contains("refundMovementMismatch"))
    }
    @Test fun possibleCompletedReceiptOverlapAndAllPaidDraftNeverGetAddedToCashAutomatically() {
        val j=source();j.getJSONArray("orders").put(receipt(cash=100))
        val a=compare(j);assertEquals(4000L,a.unassignedPaidCash);assertEquals(1,a.unassignedOrigins.size)
        assertEquals(10000L,a.shifts.single().cashFromRecordedFacts);assertNull(a.shifts.single().pendingCash);assertNull(a.shifts.single().includingPendingCash)
        j.put("orders",JSONArray());session(j).getJSONObject("paymentDraft").getJSONArray("parts").getJSONObject(1).put("paid",true)
        val b=compare(j);assertEquals(4000L,b.unassignedPaidCash);assertEquals(6000L,b.unassignedPaidCard);assertNull(b.shifts.single().pendingCard)
    }
    @Test fun unprovenShiftTimeKeepsAcceptedMoneyUnassignedAndCardPartsOutOfDrawer() {
        val j=source();session(j).put("createdAt",9)
        val a=compare(j);assertEquals(4000L,a.unassignedPaidCash);assertNull(a.shifts.single().pendingCash)
        session(j).put("createdAt",11)
        session(j).getJSONObject("paymentDraft").getJSONArray("parts").getJSONObject(0).put("method","card").put("cashGiven",JSONObject.NULL).put("change",JSONObject.NULL)
        val s=compare(j).shifts.single();assertEquals(0L,s.pendingCash);assertEquals(4000L,s.pendingCard);assertEquals(0L,s.includingPendingCash)
    }
    @Test fun orphanAndOutOfPeriodMovementsRemainVisibleWithExplicitGaps() {
        val j=source();noDraft(j);shift(j).put("openingCash",20).put("cashMovements",JSONArray().put(move("orphan","withdrawal",5,"refund",1)).put(move("courier","withdrawal",2,"delivery")))
        val s=compare(j).shifts.single();assertEquals(1300L,s.cashFromRecordedFacts)
        assertTrue(s.blockers.containsAll(setOf("cashMovementShiftTimeUnproven","refundMovementMismatch","deliveryMovementMismatch")))
        j.getJSONArray("orders").put(receipt(shift="missing"));assertTrue(compare(j).blockers.contains("receiptShiftUnknown"))
    }
    @Test fun cardOnlyAndIncompleteRefundsNeverInventCashDebits() {
        val j=source();noDraft(j);shift(j).put("openingCash",20)
        j.getJSONArray("orders").put(receipt(cash=0,card=10).put("returnedAt",13).put("returnedShiftId","s").put("returnAmount",10))
        val card=compare(j).shifts.single();assertEquals(2000L,card.cashFromRecordedFacts);assertEquals(0L,card.refundWithdrawals)
        assertFalse(card.blockers.contains("refundMovementMismatch"))
        j.put("orders",JSONArray().put(receipt().put("returnedAt",13).put("returnedShiftId","s").put("returnAmount",5)))
        val partial=compare(j).shifts.single();assertTrue(partial.blockers.contains("refundCashEvidenceIncomplete"))
        assertEquals(0L,partial.refundWithdrawals);assertEquals(3000L,partial.cashFromRecordedFacts)
    }
    @Test fun negativeRecordedBalanceAndSavedExpectedMismatchStayDiagnosticNotRepaired() {
        val j=source();noDraft(j);shift(j).put("openingCash",0).put("expectedCash",1).put("countedCash",2)
            .put("cashMovements",JSONArray().put(move("out","withdrawal",5)))
        val s=compare(j).shifts.single();assertEquals(-500L,s.cashFromRecordedFacts);assertEquals(700L,s.countedDifference)
        assertTrue(s.blockers.containsAll(setOf("negativeRecordedCash","savedExpectedCashMismatch")))
        assertEquals(1,shift(j).getInt("expectedCash"));assertEquals(2,shift(j).getInt("countedCash"))
    }
    @Test fun currencyAndOverflowFailClosedAndCrossShiftMovementIdsAreNotDeduplicated() {
        val usd=source();shift(usd).put("currency","USD")
        assertEquals("cashReconciliationCurrency",assertThrows(BackupFailure::class.java) {compare(usd)}.kind)
        val j=source();noDraft(j);shift(j).put("openingCash","92233720368547758.07");j.getJSONArray("orders").put(receipt())
        assertEquals("cashReconciliationMoney",assertThrows(BackupFailure::class.java) {compare(j)}.kind)
        val duplicate=source();noDraft(duplicate);shift(duplicate).put("cashMovements",JSONArray().put(move("same","deposit",1)))
        duplicate.getJSONArray("shifts").put(JSONObject().put("id","old").put("employeeId","cashier").put("status","closed").put("openedAt",1).put("closedAt",9).put("openingCash",0).put("cashMovements",JSONArray().put(move("same","deposit",1,time=2))))
        val d=compare(duplicate);assertEquals(listOf(100L,100L),d.shifts.map {it.deposits})
        assertTrue(d.blockers.contains("cashMovementIdentityAcrossShifts"));assertTrue(d.shifts.all {it.blockers.contains("cashMovementIdentityAcrossShifts")})
    }
}
