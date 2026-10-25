package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WorkspaceRepositoryTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun input(id: String="tea",stock: String="20")=ProductInput(id,null,id,"c","2","1",stock,null,true,"create-$id")
    private suspend fun seed(db: CatalogDatabase) {
        val catalog=CatalogRepository(db);catalog.initialize();catalog.saveCategory("c",null,"Напитки","category")
        catalog.saveProduct(input());WorkspaceRepository(db).initialize()
    }
    private suspend fun WorkspaceRepository.applyCart(action: CartAction,command: String)=cart(action,command,snapshot().cart.sessionId)
    private fun add(id: String="line")=CartAction.Add("tea",1,emptySet(),null,id)
    private suspend fun reject(block: suspend () -> Unit) {try {block();fail("Expected rejection")} catch(_:CatalogFailure) {}}
    @Test fun cartAndLayoutReopenReplayAndDoNotWriteStock() = runBlocking {
        val name="workspace-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            seed(db);var repo=WorkspaceRepository(db)
            val action=add();repo.applyCart(action,"add");repo.applyCart(action,"add")
            repo.layout(LayoutAction.SaveFolder("f","c","Популярное"),0,"folder")
            repo.layout(LayoutAction.MoveProduct("tea","f"),1,"move")
            val before=repo.snapshot()
            db.close();db=open();repo=WorkspaceRepository(db)
            assertEquals(before,repo.snapshot())
            assertEquals("1",before.cart.lines.single().quantity);assertEquals("f",before.layout.first { it.id=="tea" }.folderId)
            assertEquals("20",db.catalog().balance("tea")!!.quantity);assertEquals(1,db.catalog().movementCount("tea"))
            reject {repo.applyCart(action.copy(manualPrice="3"),"add")}
            reject {repo.applyCart(CartAction.SetQuantity("line","4",0),"stale-cart")}
            reject {repo.layout(LayoutAction.SaveFolder("g","c","Другая"),0,"stale-layout")}
            val product=db.catalog().product("tea")!!
            CatalogRepository(db).saveProduct(input().copy(expectedRevision=product.revision,stockEdited=false,name="Переименован",commandId="rename"))
            val changed=repo.snapshot();assertTrue(CartEngine.stale(changed.cart.lines.single(),changed.products))
            assertEquals("tea",changed.cart.lines.single().name)
            reject {repo.applyCart(CartAction.Step("line",1),"stale-increase")}
            repo.applyCart(CartAction.Step("line",-1),"remove")
            assertTrue(repo.snapshot().cart.lines.isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun concurrentDeltaCommandsNeverLoseTapsOrOversell() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val repo=WorkspaceRepository(db)
            coroutineScope {(1..20).map { n -> async(Dispatchers.IO) {repo.applyCart(add("line-$n"),"tap-$n")} }.awaitAll()}
            val snap=repo.snapshot();assertEquals("20",snap.cart.lines.single().quantity);assertEquals(20L,snap.cartRevision)
            reject {repo.applyCart(add("overflow"),"overflow")}
            assertEquals("20",repo.snapshot().cart.lines.single().quantity)
            assertEquals("20",db.catalog().balance("tea")!!.quantity)
        } finally {db.close()}
    }
    @Test fun actualDocumentWriteFailureRollsBackRevisionResultAndAudit() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val repo=WorkspaceRepository(db);val before=repo.snapshot()
            val failing=WorkspaceRepository(db,afterWrite={throw java.io.IOException("injected")})
            try {failing.applyCart(add(),"fault-cart");fail()} catch(_:java.io.IOException) {}
            try {failing.layout(LayoutAction.SaveFolder("f","c","Папка"),0,"fault-layout");fail()} catch(_:java.io.IOException) {}
            assertEquals(before,repo.snapshot());assertNull(db.catalog().result("fault-cart"));assertNull(db.catalog().result("fault-layout"))
            assertEquals(0,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE commandId LIKE 'fault-%'").use {it.count})
            repo.applyCart(add(),"fault-cart");assertEquals(1L,repo.snapshot().cartRevision)
        } finally {db.close()}
    }
    @Test fun activeCartAndExplicitLayoutProtectCatalogDependencies() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize();catalog.saveCategory("c",null,"c","category")
            catalog.saveProduct(input(stock="0").copy(noStockTracking=true))
            val repo=WorkspaceRepository(db);repo.initialize();repo.applyCart(add(),"add")
            reject {catalog.deleteProduct("tea",1,"delete-in-cart")}
            repo.applyCart(CartAction.Clear(1),"clear")
            repo.layout(LayoutAction.SaveFolder("f","c","Папка"),0,"folder")
            reject {catalog.deleteProduct("tea",1,"delete-in-layout")}
            repo.layout(LayoutAction.ResetProduct("tea"),1,"reset")
            catalog.deleteProduct("tea",1,"delete-product")
            reject {catalog.deleteCategory("c",1,"delete-folder-category")}
            repo.layout(LayoutAction.DeleteFolder("f"),2,"delete-folder")
            catalog.deleteCategory("c",1,"delete-category")
            assertTrue(repo.snapshot().layout.isEmpty())
        } finally {db.close()}
    }
    @Test fun migration4To5PreservesCatalogAndStartsAnEmptyCurrent() = runBlocking {
        val name="workspace-migration-${UUID.randomUUID()}.db"
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/4.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val legacy=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try {
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                legacy.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                e.optJSONArray("indices")?.let { indices -> for(j in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table)) }
            }
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
            legacy.execSQL("INSERT INTO app_config VALUES (1,'register45','owner45','BYN')")
            legacy.execSQL("INSERT INTO categories VALUES ('c','Кофе','кофе',7,4,'#DDEBFF','КФ')")
            legacy.execSQL("INSERT INTO products (id,categoryId,name,searchKey,price,cost,revision,currency,unit,type) VALUES ('p','c','Чай','чай','3','1',1,'BYN','piece','simple')")
            legacy.execSQL("INSERT INTO stock_balances VALUES ('p','0.0004',7)")
            legacy.version=4
        } finally {legacy.close()}
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_4_5,CatalogDatabase.MIGRATION_5_6).build()
        try {
            val repo=WorkspaceRepository(db);repo.initialize();val snap=repo.snapshot()
            assertTrue(snap.cart.lines.isEmpty());assertEquals(0L,snap.cartRevision)
            assertEquals("КФ",snap.categories.single().symbol);assertEquals("#DDEBFF",snap.categories.single().color)
            assertEquals("0.0004",db.catalog().balance("p")!!.quantity);assertEquals("owner45",db.catalog().config()!!.ownerId)
            assertEquals("p",snap.layout.single().id)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun unknownPayloadIsNotSilentlyReplacedByAnEmptyOrder() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            seed(db);val payload="{\"version\":99}"
            db.workspace().put(WorkspaceDocument("current",7,payload))
            val repo=WorkspaceRepository(db)
            reject {repo.initialize()}
            reject {repo.snapshot()}
            assertEquals(payload,db.workspace().document("current")!!.payload)
            assertEquals(7L,db.workspace().document("current")!!.revision)
        } finally {db.close()}
    }

}
