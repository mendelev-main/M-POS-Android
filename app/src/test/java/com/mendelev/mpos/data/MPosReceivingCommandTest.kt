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
class MPosReceivingCommandTest {
    private fun runCase(kind:String="standalone",block:suspend(MPosDatabase,MPosReceivingCommand,JSONObject)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val c=JSONObject(File("../tests/fixtures/receiving-commands.json").readText()).getJSONObject(kind);val expected=c.getJSONObject("expected");val supply=MPosSupplyStorage(db)
            supply.initialize("suppliers","""[{"id":"s","name":"Supplier"}]""")
            for(key in listOf("purchaseOrders","receivings"))supply.initialize(key,expected.getJSONArray(key).toString())
            supply.initialize("receivingDraft",c.getJSONObject("draft").toString());MPosCatalogStorage(db).initialize(expected.getJSONArray("products").toString());MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            block(db,MPosReceivingCommand(db),c)
        }finally{db.close()}
    }
    @Test fun reviewedArithmeticFixturesPreserveUnitsRepeatedLinesNegativeStockAndShortages(){
        val rows=JSONArray(File("../tests/fixtures/receiving-arithmetic.json").readText())
        for(i in 0 until rows.length()){val row=rows.getJSONObject(i);val expected=row.getJSONObject("expected");val label=row.getString("label")
            try{val items=MPosReceivingEngine.items(row.getJSONObject("draft"),row.getJSONArray("products"));val products=MPosReceivingEngine.products(items,row.getJSONArray("products"));assertTrue(label,expected.getBoolean("allowed"));assertTrue("$label items",MPosSupplyParity.same(expected.getJSONArray("items"),items));assertTrue("$label stock/cost",MPosSupplyParity.same(expected.getJSONArray("products"),products));assertEquals(label,expected.getBoolean("shortage"),MPosReceivingEngine.discrepancy(row.getJSONObject("order"),items))}
            catch(e:IllegalStateException){assertFalse(label,expected.getBoolean("allowed"));assertEquals(label,expected.getString("message"),e.message)}
        }
    }
    @Test fun standaloneCommitAtomicallyClearsDraftAndPreservesExtensions()=runCase{db,engine,c->
        assertTrue(engine.commit(c.toString()).getBoolean("ok"));val supply=MPosSupplyStorage(db)
        assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONArray("products"),JSONArray(MPosCatalogStorage(db).read().getString("payload"))))
        assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONArray("receivings"),JSONArray(supply.read("receivings").getString("payload"))))
        assertEquals("null",supply.read("receivingDraft").getString("payload"));assertEquals(1,db.stockEventProjectionDao().events("receivings").size)
    }
    @Test fun orderClosesWithShortageAndDoesNotCreateReplacementOrder()=runCase("ordered"){db,engine,c->
        engine.commit(c.toString());val orders=JSONArray(MPosSupplyStorage(db).read("purchaseOrders").getString("payload"));assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONArray("purchaseOrders"),orders));assertEquals(1,orders.length());assertTrue(orders.getJSONObject(0).getBoolean("shortage"));assertFalse(orders.getJSONObject(0).has("receivingDraftV2"))
    }
    @Test fun untrackedStockStaysAndCostUsesReceivedPrice()=runCase("untracked"){db,engine,c->
        engine.commit(c.toString());val p=JSONArray(MPosCatalogStorage(db).read().getString("payload")).getJSONObject(0);assertEquals(4.0,p.getDouble("stock"),0.0);assertEquals(40.0,p.getDouble("cost"),0.0)
    }
    @Test fun staleStockOrTamperedCostOrDuplicateReceiptCannotCommit()=runCase{db,engine,c->
        val original=c.toString();c.getJSONObject("writes").getJSONArray("products").getJSONObject(0).put("cost",999)
        try{engine.commit(c.toString());fail("tampered cost accepted")}catch(_:IllegalStateException){}
        val fresh=JSONObject(original);val p=JSONArray(fresh.getJSONObject("expected").getJSONArray("products").toString());p.getJSONObject(0).put("stock",2);MPosCatalogStorage(db).write(p.toString())
        try{engine.commit(fresh.toString());fail("stale stock accepted")}catch(_:IllegalStateException){}
        MPosCatalogStorage(db).write(fresh.getJSONObject("expected").getJSONArray("products").toString());engine.commit(fresh.toString())
        try{engine.commit(fresh.toString());fail("duplicate receipt accepted")}catch(_:IllegalStateException){}
        assertEquals(1,JSONArray(MPosSupplyStorage(db).read("receivings").getString("payload")).length())
    }
    @Test fun projectionFailureRollsBackStockOrderHistoryAndDraft()=runCase("ordered"){db,engine,c->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_receiving BEFORE INSERT ON stock_event_projection BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{engine.commit(c.toString());fail("failed projection accepted")}catch(_:android.database.sqlite.SQLiteException){}
        for(key in listOf("purchaseOrders","receivings"))assertTrue(MPosSupplyParity.same(c.getJSONObject("expected").getJSONArray(key),JSONArray(MPosSupplyStorage(db).read(key).getString("payload"))))
        assertTrue(MPosSupplyParity.same(c.getJSONObject("expected").getJSONArray("products"),JSONArray(MPosCatalogStorage(db).read().getString("payload"))))
        assertNotEquals("null",MPosSupplyStorage(db).read("receivingDraft").getString("payload"))
    }
    @Test fun finalDraftWriteFailureRollsBackPreviouslyWrittenStockAndHistory()=runCase{db,engine,c->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_draft BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'receivingDraft' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{engine.commit(c.toString());fail("failed draft clearing accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertTrue(MPosSupplyParity.same(c.getJSONObject("expected").getJSONArray("products"),JSONArray(MPosCatalogStorage(db).read().getString("payload"))))
        assertEquals("[]",MPosSupplyStorage(db).read("receivings").getString("payload"));assertNotEquals("null",MPosSupplyStorage(db).read("receivingDraft").getString("payload"));assertEquals(0,db.stockEventProjectionDao().events("receivings").size)
    }

}
