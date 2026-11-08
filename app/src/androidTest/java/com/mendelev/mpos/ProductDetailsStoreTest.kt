package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.feature.products.ProductDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProductDetailsStoreTest {
    @Test fun detailsAreAtomicRetainedAcrossOldCallersAndRestoredIntoDraft(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            val details=ProductDetails("TEA-1","Для сотрудников","☕")
            val input=ProductInput("p",null,"Чай",null,"2","1","0",null,true,"create",details=details)
            repo.saveProduct(input);repo.saveProduct(input)
            assertEquals(1L,db.catalog().product("p")!!.revision)
            assertEquals(details,repo.editorSnapshot("p").details)
            val update=input.copy(expectedRevision=1,stockEdited=false,commandId="update",details=null)
            repo.saveProduct(update)
            assertEquals(details,CatalogRepository(db).editorSnapshot("p").details)
            try {CatalogRepository(db,afterProductWrite={error("disk fault")}).saveProduct(update.copy(expectedRevision=2,commandId="fault",details=details.copy(symbol="К")));fail()} catch(_: IllegalStateException) {}
            assertEquals(2L,db.catalog().product("p")!!.revision);assertEquals(details,repo.editorSnapshot("p").details)
            assertNull(db.catalog().result("fault"))
            WorkspaceRepository(db).initialize()
            assertEquals("☕",WorkspaceRepository(db).snapshot().products.single().symbol)
            val draft=ProductDraft(id="p",details=details)
            assertEquals(details,ProductDraft.parse(draft.json()).details)
            val legacy=org.json.JSONObject(draft.json()).apply {remove("details")}.toString()
            assertNull(ProductDraft.parse(legacy).details)
            val doc=db.workspace().document(ProductDetailsCodec.PREFIX+"p")!!
            assertEquals(details,ProductDetailsCodec.decode(doc.payload,"p"))
            repo.deleteProduct("p",2,"delete")
            assertNull(db.workspace().document(ProductDetailsCodec.PREFIX+"p"))
        } finally {db.close()}
    }
    @Test fun immutableWebSidecarCanBeReadAndLocallyOverridden(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            repo.saveProduct(ProductInput("p",null,"Чай",null,"2","1","0",null,true,"seed"))
            val payload=org.json.JSONObject().put("version",1).put("values",org.json.JSONObject().put("sku","WEB-1").put("internalNote","Из Web").put("tileSymbol","Ч")).toString()
            val raw=org.json.JSONObject().put("version",1).put("sourceSha256","0".repeat(64)).put("productId","p").put("payload",payload).toString()
            val key=com.mendelev.mpos.domain.backup.WebProductMetadataDocuments.PREFIX+"p"
            db.workspace().put(WorkspaceDocument(key,1,raw))
            assertEquals(ProductDetails("WEB-1","Из Web","Ч"),repo.editorSnapshot("p").details)
            repo.saveProduct(ProductInput("p",1,"Чай",null,"2","1","0",null,false,"edit",details=ProductDetails("NATIVE-1","Новая заметка","К")))
            assertEquals("NATIVE-1",repo.editorSnapshot("p").details.sku)
            assertEquals(raw,db.workspace().document(key)!!.payload)
        } finally {db.close()}
    }
    @Test fun usageIncludesIndirectRecipesAndOnlyDirectParkedProducts(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            repo.saveProduct(ProductInput("p",null,"Основа",null,"2","1","5",null,true,"p"))
            repo.saveProduct(ProductInput("a",null,"Состав А",null,"3","0","0",null,true,"a",type="composite",components=listOf(IngredientInput("p","1","piece"))))
            repo.saveProduct(ProductInput("b",null,"Состав Б",null,"5","0","0",null,true,"b",type="composite",components=listOf(IngredientInput("a","1","piece"))))
            val usage=repo.usage("p")
            assertEquals(setOf("a","b"),usage.parents.map {it.id}.toSet());assertTrue(usage.parents.single {it.id=="a"}.direct);assertFalse(usage.parents.single {it.id=="b"}.direct)
            val work=WorkspaceRepository(db);work.initialize();var w=work.snapshot()
            work.cart(com.mendelev.mpos.domain.workspace.CartAction.Add("b",1,emptySet(),null,"line"),"add",w.cart.sessionId)
            w=work.snapshot();work.park(w.cartRevision,w.cart.sessionId,"Стол 1","park")
            assertEquals(0,repo.usage("p").parked);assertEquals(1,repo.usage("b").parked)
        } finally {db.close()}
    }

}
