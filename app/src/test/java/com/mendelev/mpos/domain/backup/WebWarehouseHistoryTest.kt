package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebWarehouseHistoryTest {
    private fun root()=JSONObject().put("version",1).put("products",JSONArray()).put("employees",JSONArray()).put("shifts",JSONArray()).put("orders",JSONArray())
    private fun map(j: JSONObject)=WebWarehouseHistory.map(WebBackupReader.read(j.toString().byteInputStream()))
    private fun line(qty: Any=3)=JSONObject().put("productId","deleted-p").put("productName","Название тогда").put("qty",qty).put("unitCost","3.333333333333333").put("totalCost",10).put("stockUnit","kg").put("invoiceQty",qty).put("invoiceUnit","kg").put("invoiceUnitPrice","3.333333333333333")
    private fun receiving()=JSONObject().put("id","r").put("type","purchaseOrder").put("purchaseOrderId","o").put("supplierName","Поставщик тогда").put("timestamp",30).put("totalCost",10).put("items",JSONArray().put(line()))
    private fun purchase()=JSONObject().put("id","o").put("status","received").put("timestamp",10).put("receivedAt",30).put("requestVersion",2)
        .put("items",JSONArray().put(JSONObject().put("productId","deleted-p").put("requestedQty",10).put("expectedQty",10).put("qty",10).put("stockUnit","kg"))).put("receivedItems",JSONArray().put(line()))
    private fun inventoryLine(id: String="p",fixed: Boolean=true)=JSONObject().put("productId",id).put("name","Тогда").put("unit","kg").put("expected","1.0004").put("actual","1").put("difference","-0.0004").apply {if(fixed) put("fixedAt",20)}
    @Test fun closedShortOrderKeepsTenRequestedThreeReceivedAndOriginalCostWithoutAnotherPosting() {
        val j=root().put("purchaseOrders",JSONArray().put(purchase())).put("receivings",JSONArray().put(receiving()))
        val f=map(j);assertEquals("received",f.purchases.single().status);assertEquals("10",f.purchases.single().lines.single().expected)
        assertEquals("3",f.receivings.single().lines.single().quantity);assertEquals("3",f.purchases.single().receivedLines!!.single().quantity)
        assertEquals("3.333333333333333",f.receivings.single().lines.single().unitCost);assertEquals("Поставщик тогда",f.receivings.single().supplierName)
        assertTrue(f.linkCoverage.isEmpty());assertEquals(0,j.getJSONArray("products").length())
    }
    @Test fun administrativeDeletionIsNonPostingHistoryEvenWhenItsRowsContainOrderedQuantity() {
        val j=root().put("receivings",JSONArray().put(receiving().put("adminDeleted",true).put("totalCost",0).put("items",JSONArray().put(line(10).put("totalCost",0)))))
        val f=map(j).receivings.single();assertTrue(f.adminDeleted);assertEquals("10",f.lines.single().quantity)
        assertTrue(f.coverage.contains("nonPostingDeletionMarker"))
    }
    @Test fun receivingDecimalsAndHeaderRoundingAreEvidenceNotNewNativeInvoiceCalculation() {
        val r=receiving().put("totalCost","3.53").put("items",JSONArray().put(line("0.30000000000000004").put("totalCost","3.525").put("invoiceUnitPrice","2.35")))
        val f=map(root().put("receivings",JSONArray().put(r))).receivings.single()
        assertEquals("3.53",f.totalCost);assertEquals("3.525",f.lines.single().totalCost);assertEquals("2.35",f.lines.single().invoiceUnitPrice)
        assertEquals("0.30000000000000004",f.lines.single().quantity)
        assertTrue(f.coverage.containsAll(setOf("sourceFractionalMoney","documentLineTotalReconciliationPending")))
    }
    @Test fun unknownPackExpectationRemainsNullInsteadOfZeroPlaceholder() {
        val p=purchase().put("status","pending").put("items",JSONArray().put(JSONObject().put("productId","p").put("requestedQty",2).put("requestedUnit","box").put("qty",0).put("expectedQty",JSONObject.NULL)))
        val f=map(root().put("purchaseOrders",JSONArray().put(p))).purchases.single()
        assertNull(f.lines.single().expected);assertEquals("0",f.lines.single().quantity);assertEquals("2",f.lines.single().requested)
        assertTrue(f.coverage.contains("expectedQuantityUnknown"))
    }
    @Test fun cancelledInventoryKeepsFixedRowsAndUnfixedRowsAsDifferentFacts() {
        val i=JSONObject().put("id","i").put("type","scheduled").put("status","cancelled").put("completedAt",40).put("cancelledAt",40)
            .put("items",JSONArray().put(inventoryLine())).put("unfixedItems",JSONArray().put(inventoryLine("q",false)))
        val f=map(root().put("inventoryHistory",JSONArray().put(i))).inventories.single()
        assertEquals("cancelled",f.state);assertEquals("-0.0004",f.lines.single().difference);assertEquals(20L,f.lines.single().fixedAt)
        assertNull(f.unfixedLines.single().fixedAt);assertTrue(f.coverage.contains("fixedRowsAlreadyApplied"))
    }
    @Test fun importedInventoryDraftPreservesEnteredCountButDiscardsOldRuntimeAuthority() {
        val row=inventoryLine("q",false).put("_androidCountBaseline",JSONObject().put("runtime","old-token").put("revision",7))
        val d=JSONObject().put("id","draft").put("type","scheduled").put("items",JSONArray().put(inventoryLine()).put(row))
        val j=root().put("inventoryDraft",d);val f=map(j).inventoryDraft!!
        assertFalse(f.lines[0].requiresRecount);assertTrue(f.lines[1].requiresRecount);assertEquals("1",f.lines[1].actual)
        assertTrue(f.coverage.containsAll(setOf("sourceRuntimeBaselineDiscarded","unfixedRowsRequireRecount","fixedRowsAlreadyApplied")))
        assertFalse(f.lines[1].deferredFields.contains("_androidCountBaseline"));assertEquals("old-token",row.getJSONObject("_androidCountBaseline").getString("runtime"))
    }
    @Test fun rawUnfinishedInvoiceInputsKeepMissingNullEmptyInvalidTextAndNumericShape() {
        val row=JSONObject().put("productId","p").put("qtyInput","-").put("priceInput",JSONObject.NULL).put("totalInput","").put("packSize",3)
        val d=JSONObject().put("version",1).put("orderId",JSONObject.NULL).put("lines",JSONArray().put(row))
        val f=map(root().put("receivingDraft",d)).receivingDrafts.single().lines.single()
        assertEquals(WebDraftScalar.Text("-"),f.quantity);assertEquals(WebDraftScalar.Nil,f.price)
        assertEquals(WebDraftScalar.Text(""),f.total);assertEquals(WebDraftScalar.Number("3"),f.packSize)
    }
    @Test fun terminalOrderDraftIsNotResumableAndBrokenOrderReferencesAreVisible() {
        val d=JSONObject().put("version",1).put("orderId","o").put("lines",JSONArray())
        val f=map(root().put("purchaseOrders",JSONArray().put(purchase().put("receivingDraftV2",d))).put("receivings",JSONArray().put(receiving().put("purchaseOrderId","missing"))))
        assertTrue(f.receivingDrafts.single().coverage.contains("draftForTerminalOrder"));assertTrue(f.linkCoverage.contains("receivingOrderReferenceMissing"))
    }
    @Test fun legacyFlatReceivingAndAnonymousDocumentStayExplicitAndDeterministic() {
        val j=root().put("receivings",JSONArray().put(line("0.0004").put("timestamp",10)))
        val a=map(j);val b=map(j);assertEquals(a,b);assertNull(a.receivings.single().sourceId)
        assertTrue(a.receivings.single().coverage.containsAll(setOf("legacyFlatReceiving","sourceDocumentIdUnknown")))
        assertEquals("0.0004",a.receivings.single().lines.single().quantity)
    }
    @Test fun equalDecimalValuesDoNotCreateFalseReconciliationFlagsFromDifferentScales() {
        val r=receiving().put("items",JSONArray().put(line(1).put("totalCost","3.5")).put(line(2).put("totalCost","6.5")))
        val i=JSONObject().put("id","i").put("completedAt",30).put("items",JSONArray().put(inventoryLine().put("expected","0.5").put("actual","10.5").put("difference",10)))
        val f=map(root().put("receivings",JSONArray().put(r)).put("inventoryHistory",JSONArray().put(i)))
        assertFalse(f.receivings.single().coverage.contains("documentLineTotalReconciliationPending"))
        assertFalse(f.inventories.single().coverage.contains("differenceReconciliationPending"))
    }
    @Test fun conflictingReceivedCopiesAndTerminalDraftHeadersRemainVisibleBeforeResume() {
        val p=purchase().put("receivedItems",JSONArray().put(line(4)))
        val f=map(root().put("purchaseOrders",JSONArray().put(p)).put("receivings",JSONArray().put(receiving())))
        assertTrue(f.linkCoverage.contains("receivedSnapshotMismatch"))
        val d=JSONObject().put("id","i").put("completedAt",30).put("items",JSONArray())
        assertTrue(map(root().put("inventoryDraft",d)).inventoryDraft!!.coverage.contains("draftCarriesTerminalTimes"))
    }
    @Test fun malformedDraftVersionsNegativeFactsAndDuplicateIdentitiesRefuse() {
        for(j in listOf(root().put("receivingDraft",JSONObject().put("version",2).put("lines",JSONArray())),
            root().put("receivings",JSONArray().put(receiving()).put(receiving())),
            root().put("receivings",JSONArray().put(receiving().put("totalCost",-1))),
            root().put("inventoryDraft",JSONObject().put("id","i").put("items",JSONArray().put(inventoryLine()).put(inventoryLine()))))) {
            assertThrows(BackupFailure::class.java) {map(j)}
        }
    }
}
