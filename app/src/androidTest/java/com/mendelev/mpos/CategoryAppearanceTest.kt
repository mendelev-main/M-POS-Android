package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CategoryAppearanceTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private suspend fun reject(action: suspend () -> Unit) {
        try { action();fail("Expected rejection") } catch(_:CatalogFailure) {}
    }
    @Test fun appearanceIsDurableReplayableAndUnaffectedByReorder() = runBlocking {
        val name="appearance-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            var repo=CatalogRepository(db);repo.initialize()
            repo.saveCategory("c",null,"Кофе","create","#ddeBff","👨‍🍳❤️🇧🇾")
            repo.saveCategory("c",null,"Кофе","create","#DDEBFF","👨‍🍳❤️🇧🇾")
            assertEquals(1L,repo.dao.category("c")!!.revision)
            repo.saveProduct(ProductInput("p",null,"Чай","c","2","1","8",null,true,"product"))
            repo.saveCategory("other",null,"Другая","other")
            repo.reorderCategories(repo.dao.categorySnapshot().reversed().map { CategoryOrderEntry(it.id,it.revision) },"move")
            val current=repo.dao.category("c")!!
            repo.saveCategory("c",current.revision,"Напитки","rename",current.color,current.symbol)
            db.close();db=open();repo=CatalogRepository(db)
            val saved=repo.dao.category("c")!!
            assertEquals("Напитки",saved.name);assertEquals("#DDEBFF",saved.color);assertEquals("👨‍🍳❤️🇧🇾",saved.symbol)
            assertEquals("c",repo.dao.product("p")!!.categoryId);assertEquals("8",repo.dao.balance("p")!!.quantity)
            repo.saveCategory("c",saved.revision,saved.name,"clear",CategoryAppearance.DEFAULT_COLOR,"")
            assertEquals("",repo.dao.category("c")!!.symbol)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun invalidStaleReusedAndFailedCommandsLeaveAppearanceUntouched() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize();repo.saveCategory("c",null,"Кофе","create")
            val before=repo.dao.category("c")!!
            reject { repo.saveCategory("c",1,"Кофе","long","#DDEBFF","АБВГ") }
            reject { repo.saveCategory("c",1,"Кофе","color","red","☕") }
            reject { repo.saveCategory("c",0,"Кофе","stale","#DDEBFF","☕") }
            reject { repo.saveCategory("c",null,"Кофе","create","#DDEBFF","☕") }
            val failing=CatalogRepository(db,afterCategoryWrite={throw java.io.IOException("injected")})
            try { failing.saveCategory("c",1,"Кофе","fault","#DDEBFF","☕");fail() } catch(_:java.io.IOException) {}
            assertEquals(before,repo.dao.category("c"));assertNull(repo.dao.result("fault"))
            assertEquals(0,db.openHelper.readableDatabase.query("SELECT * FROM audit_events WHERE commandId='fault'").use { it.count })
            repo.saveCategory("c",1,"Кофе","fault","#DDEBFF","☕")
            assertEquals(2L,repo.dao.category("c")!!.revision)
        } finally { db.close() }
    }
    @Test fun migration3To4PreservesCategoryReferencesAndLegacyCommandReplay() = runBlocking {
        val name="migration34-${UUID.randomUUID()}.db"
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/3.json")
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
            legacy.execSQL("INSERT INTO app_config VALUES (1,'register34','owner34','BYN')")
            legacy.execSQL("INSERT INTO categories VALUES ('c','Кофе','кофе',7,4)")
            legacy.execSQL("INSERT INTO products (id,categoryId,name,searchKey,price,cost,revision,currency,unit,type) VALUES ('p','c','Чай','чай','3','1',1,'BYN','piece','simple')")
            legacy.execSQL("INSERT INTO stock_balances VALUES ('p','0.0004',7)")
            legacy.execSQL("INSERT INTO operation_results VALUES (?,?,?)",arrayOf("old-command",CatalogRules.fingerprint("category","c",null,"Кофе"),"c"))
            legacy.version=3
        } finally { legacy.close() }
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_3_4,CatalogDatabase.MIGRATION_4_5,CatalogDatabase.MIGRATION_5_6,CatalogDatabase.MIGRATION_6_7,CatalogDatabase.MIGRATION_7_8).build()
        try {
            val repo=CatalogRepository(db)
            assertEquals("c",repo.saveCategory("c",null,"Кофе","old-command"))
            val category=repo.dao.category("c")!!
            assertEquals(CategoryAppearance.DEFAULT_COLOR,category.color);assertEquals("",category.symbol)
            assertEquals(7,category.sortOrder);assertEquals(4L,category.revision)
            assertEquals("c",repo.dao.product("p")!!.categoryId);assertEquals("0.0004",repo.dao.balance("p")!!.quantity)
            assertEquals("owner34",repo.dao.config()!!.ownerId)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
