package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosCatalogDeleteCommandTest {
    private val seed="""[{"id":"p","name":"Coffee","type":"simple","category":"Drinks","extension":false}]"""
    private val layout="""{"categoryOrder":["Drinks","Empty"],"categoryColors":{"Empty":"#fff"},"categorySymbols":{"Empty":"E"},"categoryOnline":{"Empty":false},"categoryOnlineOrder":{"Empty":false},"categoryOnlineMenu":{"Empty":false},"tiles":[{"type":"product","id":"p"},{"type":"category","id":"Empty"}],"extension":{"v":7}}"""
    private val navigation="""{"categories":[{"category":"Empty"},{"category":"Drinks"}],"folders":[{"id":"folder"}],"extension":false}"""
    private fun runCase(block:suspend (MPosDatabase,MPosCatalogDeleteCommand)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            MPosCatalogStorage(db).initialize(seed);MPosOrderStorage(db).initialize("[]")
            MPosEmployeeStorage(db).initialize("""[{"id":"a","role":"admin"}]""")
            MPosShiftStorage(db).initialize("""[{"id":"s","status":"open","employeeId":"a"}]""")
            MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            MPosWorkspaceStorage(db).initialize("layout",layout);MPosWorkspaceStorage(db).initialize("posNavigation",navigation)
            block(db,MPosCatalogDeleteCommand(db){it=="synthetic-confirmation"})
        }finally{db.close()}
    }
    private suspend fun command(db:MPosDatabase,type:String="product",id:String="p"):JSONObject {
        val storage=MPosWorkspaceStorage(db)
        return JSONObject().put("version",1).put("operation","delete").put("type",type).put("id",id)
            .put("expected",JSONObject().put("products",JSONArray(MPosCatalogStorage(db).read().getString("payload")))
                .put("layout",JSONObject(storage.read("layout").getString("payload")))
                .put("posNavigation",JSONObject(storage.read("posNavigation").getString("payload"))))
    }
    private suspend fun denied(engine:MPosCatalogDeleteCommand,input:JSONObject,credential:String="") {
        try{engine.commit(input.toString(),credential);fail("unexpected delete")}catch(_:IllegalStateException){}
    }
    @Test fun adminDeletesProductWithoutCredentialAndRetainsMetadataAndOtherTiles()=runCase {db,engine->
        val result=engine.commit(command(db).toString())
        assertEquals(0,result.getJSONArray("products").length());assertEquals(0,db.catalogProjectionDao().allProducts().size)
        val saved=JSONObject(MPosWorkspaceStorage(db).read("layout").getString("payload"))
        assertEquals(7,saved.getJSONObject("extension").getInt("v"));assertEquals("Empty",saved.getJSONArray("tiles").getJSONObject(0).getString("id"))
        assertEquals(navigation,MPosWorkspaceStorage(db).read("posNavigation").getString("payload"))
    }
    @Test fun liveDemotionRequiresCredentialDespiteUiPasswordRequiredFalse()=runCase {db,engine->
        val input=command(db).put("passwordRequired",false)
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"employee"}]""")
        denied(engine,input);assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"))
        engine.commit(input.toString(),"synthetic-confirmation")
        assertEquals("[]",MPosCatalogStorage(db).read().getString("payload"))
    }
    @Test fun noShiftRequiresPasswordButDoesNotForbidExistingPasswordDeletionRule()=runCase {db,engine->
        MPosShiftStorage(db).write("[]");val input=command(db)
        denied(engine,input);engine.commit(input.toString(),"synthetic-confirmation")
        assertEquals("[]",MPosCatalogStorage(db).read().getString("payload"))
    }
    @Test fun categoryAlwaysRequiresPasswordAndDeletesOnlyEmptyCategoryMetadata()=runCase {db,engine->
        val input=command(db,"category","Empty");denied(engine,input)
        val result=engine.commit(input.toString(),"synthetic-confirmation")
        val saved=result.getJSONObject("layout")
        assertEquals(1,saved.getJSONArray("categoryOrder").length());assertFalse(saved.getJSONObject("categoryColors").has("Empty"))
        assertEquals(1,result.getJSONObject("posNavigation").getJSONArray("categories").length())
        assertEquals(1,result.getJSONObject("posNavigation").getJSONArray("folders").length())
        assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"))
    }
    @Test fun categoryWithProductsAndReferencedProductRemainProtected()=runCase {db,engine->
        denied(engine,command(db,"category","Drinks"),"synthetic-confirmation")
        val products=JSONArray(seed).put(JSONObject("""{"id":"recipe","name":"Latte","type":"composite","components":[{"productId":"p","qty":1}]}"""))
        MPosCatalogStorage(db).write(products.toString());denied(engine,command(db))
        assertEquals(2,JSONArray(MPosCatalogStorage(db).read().getString("payload")).length())
    }
    @Test fun liveUnreturnedStockConsumptionBlocksDeleteAndReturnedReceiptAllowsIt()=runCase {db,engine->
        val input=command(db)
        MPosOrderStorage(db).write("""[{"id":"receipt","stockConsumption":{"items":[{"productId":"p","qty":1}]}}]""")
        denied(engine,input)
        MPosOrderStorage(db).write("""[{"id":"receipt","returnedAt":1,"stockConsumption":{"items":[{"productId":"p","qty":1}]}}]""")
        engine.commit(input.toString());assertEquals("[]",MPosCatalogStorage(db).read().getString("payload"))
    }
    @Test fun staleLayoutAndPendingJournalCannotDelete()=runCase {db,engine->
        val input=command(db);MPosWorkspaceStorage(db).write("layout","{}")
        denied(engine,input);assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"))
        MPosRecoveryStorage(db).write("criticalStorageJournal","""{"operation":"pending"}""")
        denied(engine,command(db));assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"))
    }
    @Test fun failedLayoutWriteRollsBackProductAndProjection()=runCase {db,engine->
        val input=command(db)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_layout BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'layout' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try{engine.commit(input.toString());fail("failed transaction accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"));assertEquals(1,db.catalogProjectionDao().allProducts().size)
        assertEquals(layout,MPosWorkspaceStorage(db).read("layout").getString("payload"))
    }
    @Test fun failedNavigationWriteRollsBackCategoryAndLayout()=runCase {db,engine->
        val input=command(db,"category","Empty")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_navigation BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'posNavigation' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try{engine.commit(input.toString(),"synthetic-confirmation");fail("failed transaction accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(layout,MPosWorkspaceStorage(db).read("layout").getString("payload"));assertEquals(navigation,MPosWorkspaceStorage(db).read("posNavigation").getString("payload"))
    }
}
