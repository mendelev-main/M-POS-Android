package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.backup.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class WebPendingPurchasesTest {
    private val product=WebIdentity.mapped("product","p")
    private val supplier=Supplier(WebIdentity.mapped("supplier","s"),"Supplier",1,setOf(product))
    private val products=listOf(ProductEntity(product,null,"Now","now","100","20",1,unit="piece"))
    private fun source(pack: Boolean=true): JSONObject=JSONObject().put("version",1)
        .put("products",JSONArray().put(JSONObject().put("id","p").put("name","Now").put("type","simple").put("price",100).put("cost",20).put("stock",10).put("stockUnit","piece")))
        .put("employees",JSONArray()).put("shifts",JSONArray()).put("orders",JSONArray())
        .put("purchaseOrders",JSONArray().put(JSONObject().put("id","o").put("status","pending").put("timestamp",1).put("requestVersion",5).put("supplierId","s").put("supplierName","Historical supplier")
            .put("items",JSONArray().put(JSONObject().put("productId","p").put("productName","Historical product").put("qty",if(pack)6 else 0).put("expectedQty",if(pack)6 else JSONObject.NULL).put("requestedQty",3).put("requestedUnit","box").put("stockUnit","piece").put("contentUnit","piece").put("packSize",if(pack)2 else JSONObject.NULL)))))
    private fun map(j: JSONObject)=WebPendingPurchases.map(WebBackupReader.read(j.toString().byteInputStream()),products,listOf(supplier))
    @Test fun packagingAndSourceVersionSurviveWithoutRepricingOrFakeHistory() {
        val j=source();val before=j.toString();val order=map(j).single()
        assertEquals(listOf("6"),order.expectedQuantities);assertEquals("3",order.source.lines.single().quantity)
        assertEquals("Historical product",order.source.lines.single().productName);assertEquals("Historical supplier",order.source.supplierName)
        assertEquals(5L,order.importedOrigin!!.requestVersion);assertEquals(1L,order.requestVersion);assertTrue(order.history.isEmpty());assertEquals(before,j.toString())
    }
    @Test fun unknownPackKeepsUnknownExpectationAndLegacyQuantityUsesRecordedUnit() {
        assertNull(map(source(false)).single().expectedQuantities.single())
        val j=source();val l=j.getJSONArray("purchaseOrders").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        l.remove("requestedQty");l.remove("requestedUnit");l.remove("expectedQty");l.remove("packSize");l.put("qty","0.0004")
        assertEquals(listOf("0.0004"),map(j).single().expectedQuantities)
    }
    @Test fun mismatchedExpectationMissingUnitAndDuplicateProductRefuse() {
        for(change in listOf<(JSONObject)->Unit>({it.put("expectedQty",7)},{it.remove("stockUnit")},{it.put("requestedQty",0)})) {
            val j=source();change(j.getJSONArray("purchaseOrders").getJSONObject(0).getJSONArray("items").getJSONObject(0))
            assertThrows(Exception::class.java) {map(j)}
        }
        val j=source();val rows=j.getJSONArray("purchaseOrders").getJSONObject(0).getJSONArray("items");rows.put(JSONObject(rows.getJSONObject(0).toString()))
        assertThrows(BackupFailure::class.java) {map(j)}
    }
    @Test fun recordedKilogramsConvertExactlyToCurrentGramBaseWithoutChangingOrderUnits() {
        val j=source();j.getJSONArray("products").getJSONObject(0).put("stockUnit","g")
        val line=j.getJSONArray("purchaseOrders").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        line.put("stockUnit","kg").put("contentUnit","kg")
        fun converted()=WebPendingPurchases.map(WebBackupReader.read(j.toString().byteInputStream()),products.map {it.copy(unit="g")},listOf(supplier)).single()
        val modern=converted();assertEquals(listOf("6000"),modern.expectedQuantities)
        assertEquals("3",modern.source.lines.single().quantity);assertEquals("box",modern.source.lines.single().unit);assertEquals("kg",modern.source.lines.single().contentUnit)
        line.remove("requestedQty");line.remove("requestedUnit");line.remove("expectedQty");line.remove("packSize")
        val legacy=converted();assertEquals(listOf("6000"),legacy.expectedQuantities);assertEquals("6",legacy.source.lines.single().quantity);assertEquals("kg",legacy.source.lines.single().unit)
    }

}
