package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class WebRecipeBindingTest {
    private fun simple(id: String="p",stock: String="10",cost: String="2")=JSONObject().put("id",id).put("type","simple").put("name",id).put("price",100).put("stockUnit","kg").put("stock",stock).put("cost",cost)
    private fun composite(id: String,child: String,qty: String,yield: String)=JSONObject().put("id",id).put("type","composite").put("name",id).put("price",999).put("recipeUnit","kg").put("recipeYield",yield)
        .put("components",JSONArray().put(JSONObject().put("productId",child).put("qty",qty)))
    private fun line(id: String="p",qty: String="1",price: String="100")=JSONObject().put("productId",id).put("name","Исходное имя").put("qty",qty).put("price",price)
    private fun source(vararg products: JSONObject): JSONObject {
        val j=JSONObject(javaClass.getResourceAsStream("/backup/web-v13-source-export.json")!!.bufferedReader().use {it.readText()})
        j.put("products",JSONArray(products.toList()));session(j).getJSONObject("paymentDraft").put("updatedAt",3)
        session(j).put("createdAt",2).put("orderType","На месте").put("items",JSONArray().put(line()))
        return j
    }
    private fun session(j: JSONObject)=j.getJSONObject("currentOrderSession")
    private fun noPaid(j: JSONObject) {session(j).remove("paymentDraft")}
    private fun bind(j: JSONObject)=WebRecipeBinding.bind(WebBackupReader.read(j.toString().byteInputStream()))
    private fun pid(id: String)=WebIdentity.mapped("product",id)
    private fun qty(a: WebRecipeBindingResult,id: String)=a.aggregateConsumption?.get(pid(id))
    private fun modifiers(p: JSONObject,child: String="m",amount: String="1",delta: String="2"): JSONObject=p.put("modifierGroups",JSONArray().put(JSONObject().put("id","g").put("name","Группа").put("min",0).put("max",1).put("options",JSONArray().put(JSONObject().put("id","o").put("productId",child).put("qty",amount).put("priceDelta",delta)))))
    private fun selected(child: String="m",amount: String="1",delta: String="2")=JSONArray().put(JSONObject().put("groupId","g").put("groupName","Исходная группа").put("optionId","o").put("productId",child).put("name","Исходная добавка").put("qty",amount).put("priceDelta",delta))
    @Test fun frozen100WithPaid40AndUnpaid60SurvivesCatalogPrice200AndNativeCalculation() {
        val j=source(simple().put("price",200));val raw=j.toString();val a=bind(j);val cart=a.carts.single();val l=cart.lines.single()
        assertEquals(4000L,cart.operation.payment!!.paidCash);assertEquals(6000L,cart.operation.payment!!.unpaid)
        assertEquals("100",l.source.price);assertEquals("Исходное имя",l.source.name);assertEquals("stock-unit",l.basis)
        assertEquals("1",l.physicalPerSoldUnit);assertEquals("1",qty(a,"p"));assertEquals(200L,cart.costMinor)
        assertTrue(cart.blockers.contains("sourceRecipeAtExportOnly"));assertTrue(a.blockers.contains("nativeCartEncodingPending"))
        assertEquals(a,bind(j));assertEquals(raw,j.toString());assertFalse(a.toString().contains("Исходное имя"))
        assertEquals(BigDecimal.ONE,RecipeCalculator(cart.graph!!).consumption(l.requestsPerSoldUnit!!).getValue(pid("p")))
    }
    @Test fun sellingOneThreeKgBatchFor10DoesNotDivideOrRepriceTheSale() {
        val j=source(simple("flour"),composite("dough","flour","0.5","3"));noPaid(j)
        session(j).put("items",JSONArray().put(line("dough","2","10")))
        val a=bind(j);val l=a.carts.single().lines.single()
        assertEquals("recipe-batch",l.basis);assertEquals("kg",l.physicalUnit);assertEquals("3",l.physicalPerSoldUnit)
        assertEquals("2",l.source.quantity);assertEquals("10",l.source.price)
        assertEquals(BigDecimal("3"),l.requestsPerSoldUnit!!.single().quantity);assertEquals("1",qty(a,"flour"))
        assertEquals(200L,a.carts.single().costMinor)
    }
    @Test fun nestedBatchYieldsAndCompositeModifiersMatchSourceMultiplicationExactly() {
        val leaf=simple("flour");val child=composite("dough","flour","0.2","7")
        val owner=modifiers(composite("pizza","dough","0.5","3"),"dough","2")
        val j=source(leaf,child,owner);noPaid(j)
        session(j).put("items",JSONArray().put(line("pizza","2","12").put("selectedModifiers",selected("dough","2"))))
        val a=bind(j);val cart=a.carts.single();val l=cart.lines.single()
        // two sold items × (.5 batches dough + 2 batches modifier) × .2 kg flour = 1 kg.
        assertEquals("1",qty(a,"flour"));assertEquals(cart.exactSourceConsumption,cart.nativeConsumption)
        assertEquals(listOf(BigDecimal("3"),BigDecimal("14")),l.requestsPerSoldUnit!!.map {it.quantity})
        assertEquals("12",l.source.price);assertFalse(l.blockers.contains("modifierDefinitionChanged"))
    }
    @Test fun tinyTrackedAndUntrackedIngredientsKeepStockFlagsAndCostSeparately() {
        val tracked=simple("a","0","2").put("allowNegativeStock",true)
        val untracked=simple("b","0","5").put("noStockTracking",true)
        val owner=composite("mix","a","0.0004","1");owner.getJSONArray("components").put(JSONObject().put("productId","b").put("qty",1))
        val j=source(tracked,untracked,owner);noPaid(j);session(j).put("items",JSONArray().put(line("mix")))
        val a=bind(j);assertEquals("0.0004",qty(a,"a"));assertFalse(a.aggregateConsumption!!.containsKey(pid("b")))
        assertTrue(a.shortages!!.isEmpty());assertEquals(500L,a.carts.single().costMinor)
        assertTrue(a.carts.single().graph!!.filterIsInstance<RecipeProduct.Simple>().first {it.id==pid("b")}.noStockTracking)
    }
    @Test fun currentAndParkedReservationsAreCombinedWithoutMutatingOrAcceptingPayments() {
        val j=source(simple(stock="1.5"));noPaid(j);j.put("parked",JSONArray().put(JSONObject().put("id","park").put("orderType","На месте").put("items",JSONArray().put(line())).put("total",100)))
        val a=bind(j);assertEquals(2,a.carts.size);assertEquals("2",qty(a,"p"));assertEquals(1,a.shortages!!.size)
        assertEquals(BigDecimal("2"),a.shortages!!.single().required);assertTrue(a.blockers.contains("stockShortageIncludingParked"))
    }
    @Test fun missingCostAndBalanceNeverBecomeFakeZero() {
        val p=simple();p.remove("cost");val j=source(p);val a=bind(j)
        assertNull(a.carts.single().graph);assertNull(a.carts.single().costMinor);assertNull(a.aggregateConsumption)
        assertEquals("1",a.carts.single().exactSourceConsumption!![pid("p")]);assertTrue(a.blockers.contains("aggregateReservationUnknown"))
        val missingStock=simple();missingStock.remove("stock");val b=bind(source(missingStock))
        assertEquals("1",qty(b,"p"));assertNull(b.shortages);assertTrue(b.blockers.contains("stockBalanceUnknown"))
    }
    @Test fun deletedSoldProductAndMissingModifierDoNotReleaseTheReservationAsEmpty() {
        val j=source(simple("other"));val a=bind(j)
        assertNull(a.aggregateConsumption);assertTrue(a.carts.single().lines.single().blockers.contains("soldProductUnavailable"))
        val k=source(simple());session(k).getJSONArray("items").getJSONObject(0).put("selectedModifiers",selected("deleted"))
        val b=bind(k);assertNull(b.aggregateConsumption);assertTrue(b.carts.single().lines.single().blockers.contains("modifierProductOrQuantityUnavailable"))
    }
    @Test fun changedModifierDefinitionKeepsSavedQuantityPriceAndNamesWithExplicitGap() {
        val j=source(modifiers(simple(),amount="2",delta="9"),simple("m"));noPaid(j)
        session(j).put("items",JSONArray().put(line(price="102").put("selectedModifiers",selected(amount="0.5",delta="2"))))
        val a=bind(j);val l=a.carts.single().lines.single()
        assertEquals("102",l.source.price);assertEquals("0.5",qty(a,"m"));assertEquals("Исходная добавка",l.source.modifiers.single().name)
        assertTrue(l.blockers.contains("modifierDefinitionChanged"));assertEquals("1",qty(a,"p"))
    }
    @Test fun perLineCostHalfUpIsSummedInsteadOfRoundingAggregateCost() {
        val j=source(simple(cost="0.005"));noPaid(j)
        session(j).getJSONArray("items").put(line().put("cartLineId","second"))
        val cart=bind(j).carts.single();assertEquals(listOf(1L,1L),cart.lines.map {it.costMinor});assertEquals(2L,cart.costMinor)
    }
    @Test fun nativeStockRoundingMismatchUnderflowAndSaleQuantityLimitsRemainExplicit() {
        val j=source(simple("leaf"),composite("c","leaf","1.333333333333","1"));noPaid(j)
        session(j).put("items",JSONArray().put(line("c","0.0000001")))
        val a=bind(j);assertNull(a.aggregateConsumption)
        assertEquals("0.0000001333333333333",a.carts.single().exactSourceConsumption!![pid("leaf")])
        assertTrue(a.carts.single().lines.single().blockers.contains("stockPrecisionMismatch"))
        val tiny=source(simple("leaf"),composite("c","leaf","0.000000000001","1"));noPaid(tiny)
        session(tiny).put("items",JSONArray().put(line("c","0.1")))
        assertTrue(bind(tiny).carts.single().lines.single().blockers.contains("recipeCalculation:precision"))
        val qty=source(simple());session(qty).put("items",JSONArray().put(line(qty="0.0000000001")))
        assertTrue(bind(qty).carts.single().lines.single().blockers.contains("nativeSaleQuantityPrecisionUnsupported"))
    }
    @Test fun legacyUnitAndSourcePricePrecisionAreNotSilentlyFixed() {
        val legacy=simple();legacy.remove("stockUnit");legacy.put("unit","kg")
        val a=bind(source(legacy));assertNull(a.aggregateConsumption);assertTrue(a.carts.single().lines.single().blockers.contains("legacyUnitUnknown"))
        val j=source(simple());session(j).put("items",JSONArray().put(line(price="1.001").put("unit","g")))
        val b=bind(j).carts.single().lines.single();assertEquals("1.001",b.source.price)
        assertTrue(b.blockers.containsAll(setOf("nativeSalePricePrecisionUnsupported","sourceLineUnitDiffersFromStock")))
    }
    @Test fun removedOptionAndRequiredSelectionKeepKnownStockButBlockNativeEncoding() {
        val owner=modifiers(simple());owner.getJSONArray("modifierGroups").getJSONObject(0).put("min",1)
        val j=source(owner,simple("m"));noPaid(j)
        val missing=bind(j).carts.single().lines.single()
        assertTrue(missing.blockers.contains("modifierSelectionOutOfBounds"));assertEquals("1",missing.nativeConsumption!![pid("p")])
        session(j).getJSONArray("items").getJSONObject(0).put("selectedModifiers",selected().apply {getJSONObject(0).put("optionId","removed")})
        val removed=bind(j);assertEquals("1",qty(removed,"m"))
        assertTrue(removed.carts.single().lines.single().blockers.contains("modifierDefinitionUnavailable"))
    }
    @Test fun emptyPaidCartIsUnknownButAnEmptyUnpaidDatasetHasNoReservations() {
        val j=source(simple());session(j).put("items",JSONArray())
        val a=bind(j);assertEquals(4000L,a.carts.single().operation.payment!!.paidCash)
        assertNull(a.carts.single().nativeConsumption);assertNull(a.carts.single().costMinor);assertNull(a.aggregateConsumption)
        noPaid(j);val empty=bind(j);assertTrue(empty.carts.isEmpty());assertTrue(empty.aggregateConsumption!!.isEmpty());assertTrue(empty.shortages!!.isEmpty())
    }
    @Test fun sharedDagExpansionIsBoundedBeforeNativeCalculatorRuns() {
        val products=mutableListOf(simple("a"),simple("b"))
        for(i in 0..30) {
            val first=if(i==0)"a" else "c${i-1}";val second=if(i<2)"b" else "c${i-2}"
            val node=composite("c$i",first,"1","1")
            node.getJSONArray("components").put(JSONObject().put("productId",second).put("qty",1));products.add(node)
        }
        val j=source(*products.toTypedArray());noPaid(j);session(j).put("items",JSONArray().put(line("c30")))
        assertEquals("recipeBindingLimit",assertThrows(BackupFailure::class.java) {bind(j)}.kind)
    }
    @Test fun cyclesMissingIngredientsAndIncompatibleUnitsRefuseWithoutInventingGraph() {
        for(products in listOf(arrayOf(composite("a","b","1","1"),composite("b","a","1","1")),
            arrayOf(composite("a","missing","1","1")),arrayOf(simple().put("stockDisplayUnit","ml")))) {
            assertThrows(BackupFailure::class.java) {bind(source(*products))}
        }
    }
}
