package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.feature.products.ProductDraft
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CatalogConfigurationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun input(id: String, command: String = id) = ProductInput(id,null,id,null,"5","2","0",null,false,command)
    private suspend fun failure(kind: String, action: suspend () -> Unit) {
        try { action(); fail("Expected $kind") } catch(e: CatalogFailure) { assertEquals(kind,e.kind) }
    }
    @Test fun displayConversionFlagsAndMetadataPreservePhysicalStockAndLedger() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            repo.saveProduct(input("flour").copy(unit="kg",displayUnit="g",stock="1000",cost="0.002",minimumStock="200",packSize="500",purchaseLabel="pack"))
            assertEquals("1",repo.dao.balance("flour")!!.quantity)
            val p=repo.dao.product("flour")!!
            assertEquals("2",p.cost);assertEquals("0.2",p.minimumStock);assertEquals("0.5",p.packSize)
            val metadata=input("flour","flags").copy(expectedRevision=1,expectedStockRevision=1,unit="kg",displayUnit="kg",stock="1",cost="2",
                noStockTracking=true,allowNegativeStock=true)
            repo.saveProduct(metadata);repo.saveProduct(metadata)
            assertEquals(1,repo.dao.movementCount("flour"));assertEquals("1",repo.dao.balance("flour")!!.quantity)
            val negative=metadata.copy(commandId="negative",expectedRevision=2,stock="-1",stockEdited=true)
            repo.saveProduct(negative)
            assertEquals("-1",repo.dao.balance("flour")!!.quantity)
            repo.saveProduct(negative.copy(commandId="disable",expectedRevision=3,expectedStockRevision=2,stockEdited=false,allowNegativeStock=false))
            assertEquals("-1",repo.dao.balance("flour")!!.quantity)
            failure("conflict") { repo.saveProduct(negative.copy(commandId="stale",expectedRevision=4,expectedStockRevision=1)) }
            failure("type") { repo.saveProduct(metadata.copy(commandId="unit-change",expectedRevision=4,unit="g",displayUnit="g")) }
            assertEquals(4L,repo.dao.product("flour")!!.revision)
        } finally { db.close() }
    }

    @Test fun recipeCommandsValidateFullGraphAndRollbackFaultsAndProtectReferences() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize();repo.saveProduct(input("flour").copy(unit="kg",displayUnit="kg",stock="1"))
            val dough=input("dough").copy(type="composite",recipeYield="3",components=listOf(IngredientInput("flour","0.4","g")))
            repo.saveProduct(dough);repo.saveProduct(dough)
            assertNull(repo.dao.balance("dough"));assertEquals(2,repo.dao.products().first().size)
            repo.saveProduct(input("meal").copy(type="composite",components=listOf(IngredientInput("dough","3","piece"))))
            val calculator=RecipeCalculator(recipeSnapshot(repo.dao.allProducts(),repo.dao.allComponents()))
            assertEquals(0,"0.0004".toBigDecimal().compareTo(calculator.consumption(listOf(RecipeRequest("meal",java.math.BigDecimal.ONE,QuantityUnit.PIECE)))["flour"]))
            failure("used") { repo.deleteProduct("dough",1,"delete-used") }
            failure("cycle") { repo.saveProduct(dough.copy(commandId="cycle",expectedRevision=1,components=listOf(IngredientInput("meal","1","piece")))) }
            assertEquals("flour",repo.dao.recipe("dough").single().productId)
            assertNull(repo.dao.result("cycle"));assertEquals(1L,repo.dao.product("dough")!!.revision)
            failure("reference") { repo.saveProduct(dough.copy(commandId="missing",expectedRevision=1,components=listOf(IngredientInput("missing","1","piece")))) }
            val failing=CatalogRepository(db) { throw java.io.IOException("injected") }
            try { failing.saveProduct(dough.copy(commandId="disk-failure",expectedRevision=1,recipeYield="6"));fail() } catch(_: java.io.IOException) { }
            assertEquals("3",repo.dao.product("dough")!!.recipeYield);assertEquals(1,repo.dao.recipe("dough").size)
            assertNull(repo.dao.result("disk-failure"))
            repo.deleteProduct("meal",1,"delete-meal");repo.deleteProduct("dough",1,"delete-dough")
            assertTrue(repo.dao.allComponents().isEmpty())
        } finally { db.close() }
    }

    @Test fun recipeAndExpandedDraftSurviveDiskReopen() = runBlocking {
        val name="recipe-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_1_2,CatalogDatabase.MIGRATION_2_3,CatalogDatabase.MIGRATION_3_4,CatalogDatabase.MIGRATION_4_5,CatalogDatabase.MIGRATION_5_6,CatalogDatabase.MIGRATION_6_7).build()
        var db=open()
        try {
            var repo=CatalogRepository(db);repo.initialize();repo.saveProduct(input("a"))
            val recipe=input("recipe").copy(type="composite",recipeYield="3",components=listOf(IngredientInput("a","1","piece")))
            repo.saveProduct(recipe)
            val draft=ProductDraft(name="Новый состав",type="composite",recipeYield="7",components=recipe.components,dirty=true)
            repo.dao.draft(DraftEntity(payload=draft.json()))
            db.close();db=open();repo=CatalogRepository(db)
            assertEquals("3",repo.dao.product("recipe")!!.recipeYield)
            assertEquals("a",repo.dao.recipe("recipe").single().productId)
            assertEquals(draft,ProductDraft.parse(repo.dao.draft()!!.payload))
        } finally { db.close();context.deleteDatabase(name) }
    }

    @Test fun migrationFromActualSchema1PreservesDataDraftIdentityAndCompletedCommand() = runBlocking {
        val name="migration-${UUID.randomUUID()}.db"
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val original=input("legacy").validated()
        val oldDraft=JSONObject().put("id","draft").put("name","Старый черновик").put("price","5").put("cost","2")
            .put("stock","0").put("stockEdited",false).put("dirty",true).put("commandId","draft-command").toString()
        val legacy=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        try {
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                legacy.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=e.optJSONArray("indices")
                for(j in 0 until (indices?.length() ?: 0)) legacy.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
            legacy.execSQL("INSERT INTO app_config VALUES (1,'register-original','owner-original','BYN')")
            legacy.execSQL("INSERT INTO products VALUES ('legacy',NULL,'legacy','legacy','5','2',1,'BYN','piece','simple')")
            legacy.execSQL("INSERT INTO stock_balances VALUES ('legacy','0.0004',7)")
            legacy.execSQL("INSERT INTO stock_movements VALUES ('movement','legacy','0.0004','opening','opening-command',123,NULL)")
            legacy.execSQL("INSERT INTO operation_results VALUES (?,?,?)",arrayOf("legacy","product:"+original.fingerprint(),"legacy"))
            legacy.execSQL("INSERT INTO editor_drafts VALUES ('product',?)",arrayOf(oldDraft))
            legacy.version=1
        } finally { legacy.close() }
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_1_2,CatalogDatabase.MIGRATION_2_3,CatalogDatabase.MIGRATION_3_4,CatalogDatabase.MIGRATION_4_5,CatalogDatabase.MIGRATION_5_6,CatalogDatabase.MIGRATION_6_7).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            assertEquals("owner-original",repo.dao.config()!!.ownerId)
            val p=repo.dao.product("legacy")!!
            assertEquals("piece",p.displayUnit);assertFalse(p.noStockTracking);assertFalse(p.allowNegativeStock)
            assertEquals("0.0004",repo.dao.balance("legacy")!!.quantity);assertEquals(7L,repo.dao.balance("legacy")!!.revision)
            assertEquals(1,repo.dao.movementCount("legacy"));assertTrue(repo.dao.allComponents().isEmpty())
            val restored=ProductDraft.parse(repo.dao.draft()!!.payload)
            assertEquals("Старый черновик",restored.name);assertEquals("simple",restored.type)
            repo.saveProduct(original) // replay must not rewrite the migrated balance or create another movement.
            assertEquals("0.0004",repo.dao.balance("legacy")!!.quantity)
            assertEquals(1L,repo.dao.product("legacy")!!.revision)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
