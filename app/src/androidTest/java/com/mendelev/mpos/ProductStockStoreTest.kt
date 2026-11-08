package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.backup.NativeSnapshotDatabase
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.NativeRestoreValidator
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.products.ProductDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.util.UUID

class ProductStockStoreTest {
    @Test fun wholeRecipeBasisPackagingAndReadOnlyCostAreDurableAndRestoreValidatesThem(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        val root=File(context.cacheDir,"stock-config-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val repo=CatalogRepository(db);repo.initialize()
            val raw=ProductDraft(id="flour",name="Мука",unit="kg",displayUnit="kg",cost="2",stock="10",stockEdited=true,purchaseLabel="pack",packSize="500",details=ProductDetails(purchaseContentUnit="g"))
            repo.saveProduct(raw.input())
            assertEquals("0.5",db.catalog().product("flour")!!.packSize)
            assertEquals("g",repo.editorSnapshot("flour").details.purchaseContentUnit)
            try {repo.saveProduct(raw.input().copy(expectedRevision=1,stockEdited=false,cost="3",commandId="cost-edit"));fail()} catch(e:CatalogFailure) {assertEquals("Себестоимость",e.kind)}
            assertEquals("2",db.catalog().product("flour")!!.cost);assertNull(db.catalog().result("cost-edit"))
            val recipe=ProductDraft(id="recipe",name="Состав",type="composite",unit="g",displayUnit="g",recipeYield="300",price="5",
                components=listOf(IngredientInput("flour","0.3","kg")),details=ProductDetails(sellingBasis="recipe-batch",recipeYieldManual=true))
            repo.saveProduct(recipe.input());repo.saveProduct(recipe.input())
            val work=WorkspaceRepository(db);work.initialize();var s=work.snapshot()
            assertEquals("recipe-batch",s.products.single {it.id=="recipe"}.sellingBasis)
            work.cart(CartAction.Add("recipe",1,emptySet(),null,"line"),"add",s.cart.sessionId)
            s=work.snapshot()
            assertEquals(0,BigDecimal("0.3").compareTo(CartEngine.reserved(s.cart).getValue("flour")))
            assertEquals(0,BigDecimal("0.6").compareTo(RecipeCalculator(s.cart.graph).cost(CartEngine.requests(s.cart))))
            try {repo.saveProduct(recipe.input().copy(expectedRevision=1,unit="kg",displayUnit="kg",recipeYield="0.3",commandId="unit-while-open"));fail()} catch(e:CatalogFailure) {assertEquals("unit",e.kind)}
            assertEquals("g",db.catalog().product("recipe")!!.unit);assertNull(db.catalog().result("unit-while-open"))
            work.cart(CartAction.Clear(s.cartRevision),"clear",s.cart.sessionId)
            repo.saveProduct(recipe.input().copy(expectedRevision=1,unit="kg",displayUnit="kg",recipeYield="0.3",commandId="unit-after-clear"))
            assertEquals("kg",db.catalog().product("recipe")!!.unit)
            assertEquals("recipe-batch",repo.editorSnapshot("recipe").details.sellingBasis)
            val stage=File(root,"snapshot").apply {mkdir()}
            val snapshot=NativeSnapshotDatabase.capture(db,ProductPhotoStore(File(root,"media")),stage)
            NativeRestoreValidator.validate(context,snapshot,File(root,"validation").apply {mkdir()})
            val invalid=ProductDetailsCodec.fields(ProductDetails()).put("recipeYieldManual","false").put("version",1).put("productId","p").toString()
            assertThrows(IllegalArgumentException::class.java) {ProductDetailsCodec.decode(invalid,"p")}
        } finally {db.close();root.deleteRecursively()}
    }
}
