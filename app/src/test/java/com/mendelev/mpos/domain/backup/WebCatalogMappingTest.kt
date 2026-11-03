package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class WebCatalogMappingTest {
    private fun product(id: Any="p")=JSONObject().put("id",id).put("type","simple").put("name","Товар $id").put("price","2.35").put("cost","2.35").put("stock","9.0004").put("stockUnit","kg").put("stockDisplayUnit","g")
    private fun doc(vararg products: JSONObject): WebBackupDocument {
        val j=JSONObject().put("version",1).put("products",JSONArray(products.toList())).put("employees",JSONArray()).put("shifts",JSONArray()).put("orders",JSONArray())
        return WebBackupReader.read(j.toString().byteInputStream())
    }
    private fun mapped(vararg products: JSONObject)=WebCatalogMapping.map(doc(*products))
    private fun composite(id: String,child: String,qty: String,yield: String)=JSONObject().put("id",id).put("type","composite").put("name",id).put("price",10).put("recipeUnit","kg").put("recipeYield",yield).put("components",JSONArray().put(JSONObject().put("productId",child).put("qty",qty)))
    @Test fun stockBaseDisplayCostAndPackAreConvertedWithoutRoundingOrPostingStock() {
        val p=product().put("purchaseUnit","box").put("purchasePackSize",500).put("purchaseContentUnit","g").put("minStock","0.0004")
        val before=p.toString();val f=mapped(p).products.single()
        assertEquals("9.0004",f.stock);assertEquals("2.35",f.cost);assertEquals("kg",f.unit);assertEquals("g",f.displayUnit)
        assertEquals("0.5",f.packSize);assertEquals("box",f.purchaseUnit);assertEquals("0.0004",f.minimumStock)
        assertTrue(f.blockers.isEmpty());assertEquals(before,p.toString());assertEquals(f,mapped(p).products.single())
        val emptyDisplay=product().put("stockDisplayUnit","")
        assertEquals("kg",mapped(emptyDisplay).products.single().displayUnit)
    }
    @Test fun nestedSourceBatchQuantityBecomesPhysicalChildYieldAndKeepsExactConsumption() {
        val f=mapped(product("raw"),composite("mix","raw","0.0004","0.5"),composite("meal","mix","2","1"))
        val meal=f.products.last();assertEquals("1",meal.components.single().quantity)
        val graph=f.products.map {p->if(p.type=="simple") RecipeProduct.Simple(p.id,quantityUnit(p.unit!!),p.cost!!.toBigDecimal()) else
            RecipeProduct.Composite(p.id,quantityUnit(p.unit!!),p.recipeYield.toBigDecimal(),p.components.map {RecipeComponent(it.productId,it.quantity.toBigDecimal(),quantityUnit(it.unit!!))})}
        val used=RecipeCalculator(graph).consumption(listOf(RecipeRequest(meal.id,BigDecimal.ONE,quantityUnit(meal.unit!!))))
        assertEquals(0,BigDecimal("0.0008").compareTo(used.getValue(f.products.first().id)))
    }
    @Test fun scopedIdentitiesAreStableAndDoNotAliasNativeOwnerOrDifferentKinds() {
        assertEquals(WebIdentity.mapped("product","1"),mapped(product(1)).products.single().id)
        assertEquals(mapped(product(1)).products.single().id,mapped(product("1")).products.single().id)
        assertNotEquals(WebIdentity.mapped("employee","1"),WebIdentity.mapped("product","1"))
        assertNotEquals("local-owner",mapped(product("local-owner")).products.single().id)
        assertThrows(BackupFailure::class.java) {mapped(product(1),product("1"))}
    }
    @Test fun unknownLegacyUnitAndNegativePermissionAreExplicitNotInvented() {
        val p=product().apply {remove("stockUnit");remove("stockDisplayUnit")}.put("stock","-0.0004")
        val f=mapped(p).products.single();assertNull(f.unit);assertNull(f.displayUnit)
        assertEquals("-0.0004",f.stock);assertFalse(f.allowNegativeStock)
        assertTrue(f.blockers.containsAll(setOf("legacyUnitUnknown","negativeStockWithoutPermission")))
    }
    @Test fun missingReferencesCyclesWrongDimensionsAndExcessPrecisionRefuse() {
        for(rows in listOf(arrayOf(composite("a","missing","1","1")),arrayOf(composite("a","b","1","1"),composite("b","a","1","1")),
            arrayOf(product().put("stockDisplayUnit","ml")),arrayOf(product().put("stock","0.0000000000001")),arrayOf(product().put("price","2.351")))) {
            assertThrows(BackupFailure::class.java) {mapped(*rows)}
        }
    }
    @Test fun categoriesKeepDistinctNamesAndExternalPhotosRequireExplicitHandling() {
        val f=mapped(product("a").put("category","Кофе").put("imageUrl","https://example.invalid/photo.jpg").put("sku","private-sku"),product("b").put("category","кофе"))
        assertEquals(2,f.categories.size);assertNotEquals(f.products[0].categoryId,f.products[1].categoryId)
        assertNull(f.products[0].photoKey);assertTrue(f.products[0].externalPhoto)
        assertTrue(f.products[0].blockers.contains("externalPhotoNeedsExplicitSkip"));assertTrue(f.products[0].unmappedFields.isEmpty());assertTrue(f.products[0].metadata.retainedOnlyFields.contains("sku"));assertTrue(f.products[0].blockers.contains("metadataRetentionRequired"))
    }
    @Test fun metadataIsAttachedWithoutLosingUnknownFieldsOrInventingLegacyUnits() {
        val p=product().apply {remove("stockUnit");remove("stockDisplayUnit")}.put("unit","piece").put("sku","A1").put("custom",true)
        val f=mapped(p).products.single();assertNull(f.unit)
        assertTrue(f.blockers.contains("legacyUnitUnknown"));assertTrue(f.blockers.contains("metadataRetentionRequired"))
        assertEquals(setOf("custom"),f.unmappedFields);assertTrue(f.blockers.contains("productMetadataNotMapped"))
        assertEquals(setOf("unit","sku"),f.metadata.presentFields)
        assertEquals(f.metadata,WebProductMetadataCodec.decode(f.metadata.payload))
    }
    @Test fun missingCostAndStockStayUnknownWhileMissingPriceOrTypeRefuses() {
        val p=product().apply {remove("cost");remove("stock")}
        val f=mapped(p).products.single();assertNull(f.cost);assertNull(f.stock)
        assertTrue(f.blockers.containsAll(setOf("costUnknown","stockUnknown")))
        for(field in listOf("price","type")) {
            val invalid=product().apply {remove(field)}
            assertThrows(BackupFailure::class.java) {mapped(invalid)}
        }
    }
    @Test fun modifiersKeepSignedPriceAndPhysicalQuantityWithoutGeneratingRandomIds() {
        val modifier=JSONObject().put("id","g").put("name","Добавка").put("min",0).put("max",1).put("options",JSONArray().put(JSONObject().put("id","o").put("productId","mix").put("qty",2).put("priceDelta","-0.25")))
        val owner=product("main").put("modifierGroups",JSONArray().put(modifier))
        val f=mapped(product("raw"),composite("mix","raw","1","0.5"),owner).products.last()
        val option=f.modifierGroups.single().options.single();assertEquals("1",option.quantity);assertEquals("-0.25",option.priceDelta)
        assertEquals(f,mapped(product("raw"),composite("mix","raw","1","0.5"),owner).products.last())
    }
}
