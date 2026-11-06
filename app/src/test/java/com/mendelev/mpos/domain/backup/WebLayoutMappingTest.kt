package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebLayoutMappingTest {
    private fun source()=JSONObject().put("version",1).put("products",JSONArray().put(JSONObject().put("id","p").put("type","simple").put("name","Кофе").put("category","Напитки").put("stockUnit","piece").put("price",2).put("cost",1).put("stock",10)))
        .put("employees",JSONArray()).put("orders",JSONArray()).put("shifts",JSONArray())
    private fun mapped(j: JSONObject)=WebLayoutMapping.map(WebBackupReader.read(j.toString().byteInputStream()))
    private fun layout()=JSONObject().put("categoryOrder",JSONArray().put("Пустая").put("Напитки")).put("categoryColors",JSONObject().put("Напитки","#ddeBff")).put("categorySymbols",JSONObject().put("Напитки","☕"))
    @Test fun emptyCategoriesAppearanceAndFiveColumnHomePositionsAreRetained() {
        val j=source().put("layout",layout().put("tiles",JSONArray().put(JSONObject().put("type","category").put("id","Пустая").put("col",4).put("row",3)).put(JSONObject().put("type","product").put("id","p").put("col",1).put("row",2))))
        val before=j.toString();val f=mapped(j)
        assertEquals(listOf("Пустая","Напитки"),f.categories.map {it.name});assertEquals("#DDEBFF",f.categories.last().color);assertEquals("☕",f.categories.last().symbol)
        assertEquals(listOf(19,11),f.home.map {it.nativeSlot});assertEquals(0,f.normalizedPositions)
        assertEquals(before,j.toString());assertEquals(f,mapped(j))
    }
    @Test fun foldersKeepOrderAndUnplacedProductsAreAppendedInSourceSortOrder() {
        val j=source().put("layout",layout())
        j.getJSONArray("products").put(JSONObject(j.getJSONArray("products").getJSONObject(0).toString()).put("id","q").put("sortOrder",-1))
        val items=JSONArray().put(JSONObject().put("type","folder").put("id","f").put("name","Горячее")).put(JSONObject().put("type","product").put("id","p").put("parentId","f"))
        j.put("posNavigation",JSONObject().put("version",1).put("categories",JSONArray().put(JSONObject().put("category","Напитки").put("items",items))))
        val f=mapped(j);assertEquals(listOf("folder","product","product"),f.items.map {it.type})
        assertEquals(f.items[0].id,f.items[1].folderId);assertEquals("",f.items[2].folderId)
        assertEquals(WebIdentity.mapped("product","q"),f.items[2].id)
    }
    @Test fun missingAndCollidingCoordinatesUseWebFirstFreeRuleButLargePositionsStayBlocked() {
        val j=source().put("layout",layout().put("tiles",JSONArray().put(JSONObject().put("type","category").put("id","Пустая").put("col",0).put("row",0)).put(JSONObject().put("type","product").put("id","p").put("col",0).put("row",0))))
        val f=mapped(j);assertEquals(listOf(0,1),f.home.map {it.nativeSlot});assertEquals(1,f.normalizedPositions)
        j.getJSONObject("layout").getJSONArray("tiles").getJSONObject(1).put("row",100)
        val far=mapped(j);assertEquals(100,far.home.last().row);assertNull(far.home.last().nativeSlot)
        assertTrue(far.blockers.contains("homePositionBeyondNativeCapacity"))
    }
    @Test fun staleProductFolderAndDuplicateNavigationRefuseInsteadOfDisappearing() {
        for(mode in 0..2) {
            val j=source().put("layout",layout())
            val item=JSONObject().put("type","product").put("id",if(mode==0) "missing" else "p").put("parentId",if(mode==1) "missing" else "")
            val rows=JSONArray().put(item);if(mode==2) rows.put(JSONObject(item.toString()))
            j.put("posNavigation",JSONObject().put("version",1).put("categories",JSONArray().put(JSONObject().put("category","Напитки").put("items",rows))))
            assertThrows(BackupFailure::class.java) {mapped(j)}
        }
    }
    @Test fun uncategorizedProductsUseNativeEmptyCategoryAndOnlineFlagsRemainDeferred() {
        val j=source();j.getJSONArray("products").getJSONObject(0).put("category","")
        j.put("layout",JSONObject().put("categoryOnlineOrder",JSONObject().put("Без категории",false)))
        val f=mapped(j);assertEquals("",f.categories.single().id);assertEquals("",f.items.single().categoryId)
        assertEquals(setOf("categoryOnlineOrder"),f.deferredFields)
    }
}
