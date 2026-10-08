package com.mendelev.mpos.workspace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosWorkspaceReadModelTest {
    private val products=JSONArray("""[{"id":"p","name":"Латте","type":"simple","category":"Кофе","price":3.5,"stock":2.125,"stockUnit":"l","tileSymbol":"☕"},{"id":"u","name":"Без учёта","category":"Кофе","type":"simple","price":0,"noStockTracking":true},{"id":"r","name":"Рецепт","category":"Кофе","type":"composite","price":2,"components":[{"productId":"p","qty":0.5}]}]""")
    private val layout=JSONObject("""{"categoryOrder":["Кофе"],"categoryColors":{"Кофе":"#123456"},"categorySymbols":{"Кофе":"☕"},"tiles":[{"type":"category","id":"Кофе","col":3,"row":0},{"type":"product","id":"p","col":0,"row":2},{"type":"product","id":"u","col":1,"row":2},{"type":"product","id":"r","col":2,"row":2}]}""")
    private fun snapshot(path:String?=null,search:String="")=JSONObject().put("tab","pos").put("posPath",path?:JSONObject.NULL).put("posFolder","").put("editMode",false).put("search",search).put("revision",1)
    private fun order()=JSONObject("""{"currency":"BYN","items":[],"discounts":[],"loyaltyPrograms":[],"loyaltyRedemptions":{},"orderType":"На месте","deliveryFee":0,"deliveryTariffSelected":false,"deliveryRates":[],"customer":{"id":"","name":"","phone":""},"orderLabel":"","orderComment":"","source":"","webOrderId":"","webOrderStatus":""}""")
    @Test fun rootModelPreservesCoordinatesSymbolsUnitsRecipesAndManualPriceTile() {
        val pBefore=products.toString();val lBefore=layout.toString()
        val model=MPosWorkspaceReadModel.calculate(products,layout,null,snapshot(),null,order(),false,2,5)
        val tiles=model.getJSONArray("tiles");assertEquals(4,tiles.length())
        assertEquals("#123456",tiles.getJSONObject(0).getString("color"));assertEquals(3,tiles.getJSONObject(0).getInt("column"))
        assertEquals("3,50 BYN",tiles.getJSONObject(1).getString("price"));assertEquals("Остаток: 2.125 л",tiles.getJSONObject(1).getString("stock"));assertEquals(2,tiles.getJSONObject(1).getInt("row"))
        assertEquals("0,00 BYN",tiles.getJSONObject(2).getString("price"));assertEquals("Остаток: ∞",tiles.getJSONObject(2).getString("stock"));assertFalse(tiles.getJSONObject(2).getBoolean("disabled"))
        assertEquals("Доступно: 4",tiles.getJSONObject(3).getString("stock"));assertTrue(model.getString("notice").contains("откройте"))
        assertEquals(pBefore,products.toString());assertEquals(lBefore,layout.toString())
    }
    @Test fun folderScopeSearchAndStrictDatasetIdsPreserveReviewedBehavior() {
        val nav=JSONObject("""{"version":1,"categories":[{"category":"Кофе","items":[{"type":"folder","id":"f","name":"Папка","parentId":""},{"type":"product","id":"p","parentId":"f"}]}]}""")
        val all=MPosWorkspaceReadModel.calculate(products,layout,nav,snapshot("Кофе","ЛАТТЕ"),null,order(),true,0,5)
        assertEquals(1,all.getJSONArray("tiles").length())
        val live=MPosWorkspaceReadModel.calculate(products,layout,nav,snapshot("Кофе","ЛАТТЕ"),null,order(),true,0,5,JSONArray("""[{"type":"product","id":"u"},{"type":"folder","id":"f"}]"""))
        assertEquals(0,live.getJSONArray("tiles").length())
        val folder=MPosWorkspaceReadModel.calculate(products,layout,nav,snapshot("Кофе","not found"),JSONObject().put("category","Кофе").put("id","f"),order(),true,0,1)
        assertEquals(1,folder.getJSONArray("tiles").length());assertEquals("Папка",folder.getString("title"))
        val numeric=JSONArray("""[{"id":0,"name":"Numeric","type":"simple","price":1,"noStockTracking":true}]""")
        val numericLayout=JSONObject("""{"tiles":[{"type":"product","id":0,"col":0,"row":0}]}""")
        val model=MPosWorkspaceReadModel.calculate(numeric,numericLayout,null,snapshot(),null,order(),true,0,5)
        val key=model.getJSONArray("tiles").getJSONObject(0).getString("key");assertEquals("0",model.getJSONObject("actions").getJSONObject(key).getString("value"))
    }
    @Test fun cartQuoteUsesNativeDiscountDeliveryAndLoyaltyRatherThanHtmlAmounts() {
        val order=order().put("items",JSONArray("""[{"cartLineId":"line","productId":"p","name":"Латте","qty":2,"price":3.5,"discountId":"d","comment":"без сахара","selectedModifiers":[{"name":"Сироп"}]}]"""))
            .put("discounts",JSONArray("""[{"id":"d","name":"Сотрудник","type":"percent","value":10}]""")).put("orderType","Доставка").put("deliveryFee",2)
            .put("source","web").put("webOrderId","w").put("webOrderStatus","accepted")
        val model=MPosWorkspaceReadModel.calculate(products,layout,null,snapshot(),null,order,true,0,5)
        assertEquals("6,30 BYN",model.getJSONArray("lines").getJSONObject(0).getString("amount"))
        assertTrue(model.getJSONArray("lines").getJSONObject(0).getString("details").contains("без сахара"))
        assertEquals("Выберите тариф",model.getJSONArray("totals").getJSONObject(0).getString("value"))
        assertEquals("8,30 BYN",model.getJSONArray("totals").getJSONObject(1).getString("value"))
        assertEquals(3,model.getJSONArray("cartButtons").length());assertEquals("Текущий заказ — 2 поз.",model.getString("cartTitle"))
        assertEquals("payment",model.getJSONObject("actions").getJSONObject(model.getJSONArray("cartButtons").getJSONObject(2).getString("key")).getString("operation"))
    }
    @Test fun visibleRecipeReadsIngredientsOutsideWorkspaceWithoutRenderingTheirTiles() {
        val records=JSONArray("""[{"id":"ingredient","name":"Молоко","type":"simple","price":1,"stock":2.125,"stockUnit":"l"},{"id":"drink","name":"Напиток","type":"composite","price":2,"components":[{"productId":"ingredient","qty":0.25}]}]""")
        val layout=JSONObject("""{"tiles":[{"type":"product","id":"drink"}]}""")
        val tiles=MPosWorkspaceReadModel.calculate(records,layout,null,snapshot(),null,order(),true,0,5).getJSONArray("tiles")
        assertEquals(1,tiles.length());assertEquals("Доступно: 8",tiles.getJSONObject(0).getString("stock"));assertFalse(tiles.getJSONObject(0).getBoolean("disabled"))
    }
    @Test fun simpleNegativeStockRemainsVisibleAndInfiniteLegacyStockRetainsTileAvailability() {
        val records=JSONArray("""[{"id":"negative","name":"Корректировка","type":"simple","price":1,"stock":-0.125,"stockUnit":"kg"},{"id":"infinite","name":"Legacy","type":"simple","price":1,"stock":"Infinity"}]""")
        val layout=JSONObject("""{"tiles":[{"type":"product","id":"negative"},{"type":"product","id":"infinite"}]}""")
        val tiles=MPosWorkspaceReadModel.calculate(records,layout,null,snapshot(),null,order(),true,0,5).getJSONArray("tiles")
        assertEquals("Остаток: -0.125 кг",tiles.getJSONObject(0).getString("stock"));assertTrue(tiles.getJSONObject(0).getBoolean("disabled"))
        assertEquals("Остаток: 0 ед. (не задана)",tiles.getJSONObject(1).getString("stock"));assertFalse(tiles.getJSONObject(1).getBoolean("disabled"))
    }
}
