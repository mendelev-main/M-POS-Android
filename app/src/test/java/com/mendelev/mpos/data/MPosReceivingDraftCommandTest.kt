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
class MPosReceivingDraftCommandTest {
    private fun fixtures()=JSONArray(File("../tests/fixtures/receiving-drafts.json").readText())
    private fun fixture(name:String):JSONObject {val rows=fixtures();return (0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("name")==name}}
    private fun runCase(name:String,block:suspend(MPosDatabase,MPosReceivingDraftCommand,JSONObject)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val f=fixture(name);val supply=MPosSupplyStorage(db)
            supply.initialize("purchaseOrders",f.getJSONObject("command").getJSONArray("expectedOrders").toString());supply.initialize("receivingDraft",f.get("saved").toString())
            MPosCatalogStorage(db).initialize(f.getJSONArray("products").toString());MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            block(db,MPosReceivingDraftCommand(db),f)
        }finally{db.close()}
    }
    @Test fun allOpenAndSaveResultsMatchIndependentlyReviewedSource() {
        val rows=fixtures();for(i in 0 until rows.length())runCase(rows.getJSONObject(i).getString("name")){db,engine,f->
            val result=engine.execute(f.getJSONObject("command").toString());val expected=f.getJSONObject("result");val label=f.getString("name")
            for(key in listOf("draft","orders","changed"))assertTrue("$label $key",MPosSupplyParity.same(expected.get(key),result.get(key)))
            assertTrue(label,MPosSupplyParity.same(f.getJSONArray("products"),JSONArray(MPosCatalogStorage(db).read().getString("payload"))))
        }
    }
    @Test fun reopeningRestoresPersistedOrderDraftWithoutWritingItAgain()=runCase("new-order"){db,engine,f->
        val c=f.getJSONObject("command");val first=engine.execute(c.toString());assertTrue(first.getBoolean("changed"));c.put("expectedOrders",first.getJSONArray("orders"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_repeat_draft BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'purchaseOrders' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val reopened=engine.execute(c.toString());assertFalse(reopened.getBoolean("changed"));assertTrue(MPosSupplyParity.same(first.getJSONObject("draft"),reopened.getJSONObject("draft")))
    }
    @Test fun receivedDeletedAndStaleOrdersCannotBeOpenedOrOverwritten()=runCase("save-ordered-partial"){db,engine,f->
        val c=f.getJSONObject("command");val supply=MPosSupplyStorage(db);val before=c.getJSONArray("expectedOrders");val changed=JSONArray(before.toString());changed.getJSONObject(0).put("extension",10);supply.write("purchaseOrders",changed.toString())
        try{engine.execute(c.toString());fail("stale order overwritten")}catch(_:IllegalStateException){}
        for(status in listOf("received","deleted")){changed.getJSONObject(0).put("status",status);supply.write("purchaseOrders",changed.toString());c.put("expectedOrders",changed)
            try{engine.execute(c.toString());fail("closed order accepted")}catch(_:IllegalStateException){}
            val open=JSONObject().put("version",1).put("operation","open").put("orderId","o").put("expectedOrders",changed).put("cart",JSONArray())
            try{engine.execute(open.toString());fail("closed order opened")}catch(_:IllegalStateException){}
        }
    }
    @Test fun partialStandaloneDraftSurvivesSaveAndRestoreIncludingExtensions()=runCase("save-standalone-partial"){db,engine,f->
        val saved=engine.execute(f.getJSONObject("command").toString()).getJSONObject("draft");val c=JSONObject().put("version",1).put("operation","open").put("orderId",JSONObject.NULL).put("cart",JSONArray()).put("expectedOrders",JSONArray())
        val restored=engine.execute(c.toString()).getJSONObject("draft");assertTrue(MPosSupplyParity.same(saved,restored));assertEquals("",restored.getJSONArray("lines").getJSONObject(0).getString("qtyInput"));assertTrue(restored.has("extension"))
        MPosSupplyStorage(db).remove("receivingDraft");assertEquals(0,engine.execute(c.toString()).getJSONObject("draft").getJSONArray("lines").length())
    }
    @Test fun sqlFailurePreservesOrderAndPreviousDraft()=runCase("save-ordered-partial"){db,engine,f->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_order_draft BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'purchaseOrders' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{engine.execute(f.getJSONObject("command").toString());fail("failed write accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertTrue(MPosSupplyParity.same(f.getJSONObject("command").getJSONArray("expectedOrders"),JSONArray(MPosSupplyStorage(db).read("purchaseOrders").getString("payload"))))
    }
    @Test fun pendingRecoveryCannotOpenOrSaveDraft()=runCase("save-standalone-partial"){db,engine,f->
        MPosRecoveryStorage(db).write("criticalStorageJournal","""{"version":1,"operation":"test","writes":{}}""")
        try{engine.execute(f.getJSONObject("command").toString());fail("draft changed during recovery")}catch(_:IllegalStateException){}
        assertEquals("null",MPosSupplyStorage(db).read("receivingDraft").getString("payload"))
    }
    @Test fun savedStandaloneDraftRestoresAfterFileBackedDatabaseReopen()=runBlocking {
        val context=RuntimeEnvironment.getApplication();val name="draft-reopen-${java.util.UUID.randomUUID()}.db";var db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build()
        try{val f=fixture("save-standalone-partial");val supply=MPosSupplyStorage(db);supply.initialize("purchaseOrders","[]");supply.initialize("receivingDraft",null);MPosCatalogStorage(db).initialize(f.getJSONArray("products").toString());MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            MPosReceivingDraftCommand(db).execute(f.getJSONObject("command").toString());db.close();db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build()
            val c=JSONObject().put("version",1).put("operation","open").put("orderId",JSONObject.NULL).put("cart",JSONArray()).put("expectedOrders",JSONArray())
            assertTrue(MPosSupplyParity.same(f.getJSONObject("command").getJSONObject("draft"),MPosReceivingDraftCommand(db).execute(c.toString()).getJSONObject("draft")))
        }finally{db.close();context.deleteDatabase(name)}
    }

}
