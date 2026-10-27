package com.mendelev.mpos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.feature.products.ProductDraft
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

class ProductExtrasTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun root()=File(context.cacheDir,"photo-test-${UUID.randomUUID()}").apply { mkdirs() }
    private fun image(): ByteArray {
        val bitmap=Bitmap.createBitmap(1300,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle() }.toByteArray()
    }
    private fun input(id: String) = ProductInput(id,null,id,null,"3","1","0",null,false,id)
    private val group get()=ModifierGroupInput("group","Молоко","1","1",listOf(ModifierOptionInput("option","milk",quantity="0.05",priceDelta="1.5")))
    private suspend fun reject(action: suspend () -> Unit) { try { action();fail("Expected validation failure") } catch(_: CatalogFailure) {} }

    @Test fun modifierPersistenceReplayGuardsDraftAndRollbackAreAtomic() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize();repo.saveProduct(input("milk"))
            val coffee=input("coffee").copy(modifierGroups=listOf(group))
            repo.saveProduct(coffee);repo.saveProduct(coffee)
            assertEquals(listOf(group),repo.editorSnapshot("coffee").modifierGroups)
            reject { repo.deleteProduct("milk",1,"delete-used") }
            reject { repo.saveProduct(coffee.copy(commandId="bad",expectedRevision=1,modifierGroups=listOf(group.copy(minimum="2")))) }
            reject { repo.saveProduct(coffee.copy(commandId="missing",expectedRevision=1,modifierGroups=listOf(group.copy(options=listOf(group.options.single().copy(productId="gone")))))) }
            assertEquals(1,repo.dao.modifierOptions("coffee").size);assertEquals(1L,repo.dao.product("coffee")!!.revision)
            repo.dao.draft(DraftEntity(payload=ProductDraft(id="another",modifierGroups=listOf(group)).json()))
            repo.saveProduct(coffee.copy(commandId="clear-groups",expectedRevision=1,modifierGroups=emptyList()))
            // A separate, unsaved editor also protects referenced products and is not erased by unrelated deletion.
            repo.dao.draft(DraftEntity(payload=ProductDraft(id="another",modifierGroups=listOf(group)).json()))
            reject { repo.deleteProduct("milk",1,"draft-used") }
            repo.saveProduct(input("unrelated"))
            repo.dao.draft(DraftEntity(payload=ProductDraft(id="another",modifierGroups=listOf(group)).json()))
            repo.deleteProduct("unrelated",1,"delete-unrelated")
            assertEquals("another",JSONObject(repo.dao.draft()!!.payload).getString("id"))
            repo.dao.clearDraft();repo.deleteProduct("milk",1,"delete-free")
            assertNull(repo.dao.product("milk"))
        } finally { db.close() }
    }

    @Test fun photoPreparationRejectsBadStreamsAndFaultsKeepPreviousImmutablePhoto() {
        val root=root()
        try {
            val bytes=image();val store=ProductPhotoStore(root)
            val original=store.prepare { ByteArrayInputStream(bytes) }
            assertTrue(store.file(original).isFile)
            store.read(original,1200)!!.also { assertTrue(it.width<=1200);it.recycle() }
            assertThrows(Exception::class.java) { store.prepare { throw SecurityException("permission lost") } }
            assertThrows(Exception::class.java) { store.prepare { ByteArrayInputStream("not an image".toByteArray()) } }
            assertThrows(CatalogFailure::class.java) { store.prepare { ByteArrayInputStream(ByteArray(16*1024*1024+1)) } }
            for(point in listOf("copied","decoded","beforePublish","published")) {
                assertThrows(java.io.IOException::class.java) { ProductPhotoStore(root) { if(it==point) throw java.io.IOException("injected $point") }.prepare { ByteArrayInputStream(bytes) } }
                assertTrue(store.file(original).isFile);assertNotNull(store.read(original))
            }
            assertTrue(File(root,"product-photo-staging").listFiles()!!.isEmpty())
            assertThrows(CatalogFailure::class.java) { store.file("../../outside.jpg") }
        } finally { root.deleteRecursively() }
    }

    @Test fun failedDatabaseCommitPreservesOldPhotoAndDraftAcrossReopenThenCleanupRespectsReferences() = runBlocking {
        val root=root();val name="photo-${UUID.randomUUID()}.db"
        val store=ProductPhotoStore(root)
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val a=store.prepare { ByteArrayInputStream(image()) };val b=store.prepare { ByteArrayInputStream(image()) }
            var repo=CatalogRepository(db,photos=store);repo.initialize()
            repo.saveProduct(input("p").copy(photoId=a))
            val draft=ProductDraft(id="p",expectedRevision=1,photoId=b,dirty=true,modifierGroups=emptyList())
            repo.saveDraft(DraftEntity(payload=draft.json()))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo_commit BEFORE INSERT ON audit_events BEGIN SELECT RAISE(ABORT,'injected storage failure'); END")
            try { repo.saveProduct(input("p").copy(commandId="replace",expectedRevision=1,photoId=b));fail() } catch(_: android.database.sqlite.SQLiteException) {}
            db.close();db=open();repo=CatalogRepository(db,photos=store)
            assertEquals(a,repo.dao.product("p")!!.photoId);assertEquals(b,ProductDraft.parse(repo.dao.draft()!!.payload).photoId)
            assertNull(repo.dao.result("replace"));assertTrue(store.file(a).isFile)
            val orphan=store.prepare { ByteArrayInputStream(image()) }
            for(id in listOf(a,b,orphan)) store.file(id).setLastModified(System.currentTimeMillis()-48*60*60*1000L)
            repo.cleanupPhotos()
            assertTrue(store.file(a).isFile);assertTrue(store.file(b).isFile);assertFalse(store.file(orphan).exists())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_photo_commit")
            val replacement=input("p").copy(commandId="replace",expectedRevision=1,photoId=b)
            repo.saveProduct(replacement);repo.saveProduct(replacement)
            assertEquals(b,repo.dao.product("p")!!.photoId)
            repo.saveProduct(replacement.copy(commandId="remove-photo",expectedRevision=2,photoId=null))
            assertNull(repo.dao.product("p")!!.photoId)
            assertTrue(store.file(b).isFile) // no file removal precedes a successful DB commit.
        } finally { db.close();context.deleteDatabase(name);root.deleteRecursively() }
    }

    @Test fun migration2To3PreservesRecipeFlagsIdentityAndPriorCommand() = runBlocking {
        val name="migration23-${UUID.randomUUID()}.db"
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/2.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val legacy=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        val old=input("milk").copy(unit="kg",displayUnit="g",noStockTracking=true).validated()
        try {
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                legacy.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                e.optJSONArray("indices")?.let { indices -> for(j in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table)) }
            }
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
            legacy.execSQL("INSERT INTO app_config VALUES (1,'register23','owner23','BYN')")
            legacy.execSQL("INSERT INTO products (id,categoryId,name,searchKey,price,cost,revision,currency,unit,type,displayUnit,noStockTracking,allowNegativeStock,recipeYield) VALUES ('milk',NULL,'milk','milk','3','1',1,'BYN','kg','simple','g',1,0,'1')")
            legacy.execSQL("INSERT INTO products (id,categoryId,name,searchKey,price,cost,revision,currency,unit,type,displayUnit,recipeYield) VALUES ('recipe',NULL,'recipe','recipe','3','0',1,'BYN','piece','composite','piece','3')")
            legacy.execSQL("INSERT INTO stock_balances VALUES ('milk','0.0004',7)")
            legacy.execSQL("INSERT INTO recipe_components VALUES ('recipe',0,'milk','0.4','g')")
            legacy.execSQL("INSERT INTO operation_results VALUES (?,?,?)",arrayOf("milk","product:"+old.fingerprint(),"milk"))
            legacy.version=2
        } finally { legacy.close() }
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_2_3,CatalogDatabase.MIGRATION_3_4,CatalogDatabase.MIGRATION_4_5,CatalogDatabase.MIGRATION_5_6,CatalogDatabase.MIGRATION_6_7,CatalogDatabase.MIGRATION_7_8).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            assertEquals("owner23",repo.dao.config()!!.ownerId)
            val p=repo.dao.product("milk")!!
            assertEquals("g",p.displayUnit);assertTrue(p.noStockTracking);assertNull(p.photoId)
            assertEquals("0.0004",repo.dao.balance("milk")!!.quantity)
            assertEquals("milk",repo.dao.recipe("recipe").single().productId)
            assertTrue(repo.dao.modifierGroups("milk").isEmpty());assertTrue(repo.dao.modifierOptions("milk").isEmpty())
            repo.saveProduct(old);assertEquals(7L,repo.dao.balance("milk")!!.revision)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
