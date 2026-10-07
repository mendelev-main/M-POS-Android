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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosPurchaseCommandTest {
    private fun fixture(kind:String)=JSONObject(File("../tests/fixtures/purchase-commands.json").readText()).getJSONObject(kind)
    private fun runCase(kind:String,block:suspend(MPosDatabase,MPosPurchaseCommand,JSONObject)->Unit)=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val c=fixture(kind);val expected=c.getJSONObject("expected");val supply=MPosSupplyStorage(db)
            supply.initialize("suppliers","""[{"id":"s","name":"Supplier","productIds":["p"]}]""")
            for(key in listOf("purchaseOrders","receivings"))supply.initialize(key,expected.getJSONArray(key).toString())
            MPosCatalogStorage(db).initialize(expected.getJSONArray("products").toString());MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            MPosEmployeeStorage(db).initialize("""[{"id":"e","name":"Admin","role":"admin"}]""");MPosShiftStorage(db).initialize("""[{"id":"shift","employeeId":"e","status":"open"}]""")
            block(db,MPosPurchaseCommand(db),c)
        }finally{db.close()}
    }
    @Test fun quantitiesConversionsAndErrorsMatchActualSourceFixtures(){
        val rows=JSONArray(File("../tests/fixtures/purchase-lines.json").readText())
        for(i in 0 until rows.length()){val c=rows.getJSONObject(i);val expected=c.getJSONObject("expected")
            try{val line=MPosPurchaseEngine.line(c.getJSONObject("product"),c.opt("qty"),c.get("unit"),c.opt("size"),c.opt("content"),123);assertTrue(c.getString("label"),expected.getBoolean("allowed"));assertTrue(c.getString("label"),MPosSupplyParity.same(expected.getJSONObject("line"),line))}
            catch(error:IllegalStateException){assertFalse(c.getString("label"),expected.getBoolean("allowed"));assertEquals(expected.getString("message"),error.message)}
        }
    }
    @Test fun reviewedCreateIsAtomicAndOnlyUpdatesPurchaseDefaultsNotStock()=runCase("create"){db,engine,c->
        engine.commit(c.toString());val supply=MPosSupplyStorage(db)
        assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONArray("purchaseOrders"),JSONArray(supply.read("purchaseOrders").getString("payload"))))
        val products=JSONArray(MPosCatalogStorage(db).read().getString("payload"));assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONArray("products"),products));assertEquals(9,products.getJSONObject(0).getInt("stock"));assertEquals("null",MPosRecoveryStorage(db).read("criticalStorageJournal").getString("payload"))
    }
    @Test fun staleCatalogOrSupplierBindingsCannotCommitPreparedOrder()=runCase("create"){db,engine,c->
        val products=JSONArray(c.getJSONObject("expected").getJSONArray("products").toString());products.getJSONObject(0).put("stock",5);MPosCatalogStorage(db).write(products.toString())
        try{engine.commit(c.toString());fail("stale catalogue accepted")}catch(_:IllegalStateException){}
        MPosCatalogStorage(db).write(c.getJSONObject("expected").getJSONArray("products").toString());MPosSupplyStorage(db).write("suppliers","""[{"id":"s","name":"Supplier","productIds":["other"]}]""")
        try{engine.commit(c.toString());fail("unbound product accepted")}catch(_:IllegalStateException){}
        assertEquals("[]",MPosSupplyStorage(db).read("purchaseOrders").getString("payload"))
    }
    @Test fun emptySupplierBindingsRetainUserApprovedExistingCartBehavior()=runCase("create"){db,engine,c->
        MPosSupplyStorage(db).write("suppliers","[{\"id\":\"s\",\"name\":\"Supplier\",\"productIds\":[]}]")
        assertTrue(engine.commit(c.toString()).getBoolean("ok"));assertEquals(1,JSONArray(MPosSupplyStorage(db).read("purchaseOrders").getString("payload")).length())
    }
    @Test fun deletionKeepsTombstoneAndProjectsZeroCostAuditHistory()=runCase("delete"){db,engine,c->
        engine.commit(c.toString());val supply=MPosSupplyStorage(db)
        for(key in listOf("purchaseOrders","receivings"))assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONArray(key),JSONArray(supply.read(key).getString("payload"))))
        assertEquals(1,db.stockEventProjectionDao().events("receivings").size);assertEquals(0.0,db.stockEventProjectionDao().events("receivings")[0].totalCost,0.0)
    }
    @Test fun receivedAndNonAdminDeletionAreRejected()=runCase("delete"){db,engine,c->
        val before=c.getJSONObject("expected").getJSONArray("purchaseOrders");before.getJSONObject(0).put("status","received");MPosSupplyStorage(db).write("purchaseOrders",before.toString())
        try{engine.commit(c.toString());fail("received deleted")}catch(_:IllegalStateException){}
        before.getJSONObject(0).put("status","pending");MPosSupplyStorage(db).write("purchaseOrders",before.toString());MPosEmployeeStorage(db).write("""[{"id":"e","name":"Admin","role":"employee"}]""")
        try{engine.commit(c.toString());fail("ordinary employee deleted")}catch(_:IllegalStateException){}
    }
    @Test fun historyProjectionFailureRollsBackBothDocuments()=runCase("delete"){db,engine,c->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_supply_history BEFORE INSERT ON stock_event_projection BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{engine.commit(c.toString());fail("failed projection accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals("pending",JSONArray(MPosSupplyStorage(db).read("purchaseOrders").getString("payload")).getJSONObject(0).getString("status"));assertEquals("[]",MPosSupplyStorage(db).read("receivings").getString("payload"))
    }
}
