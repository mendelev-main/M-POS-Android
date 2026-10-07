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
class MPosInventoryCommandTest {
    private fun runCase(kind:String,block:suspend(MPosDatabase,MPosInventoryCommand,JSONObject)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val c=JSONObject(File("../tests/fixtures/inventory-commands.json").readText()).getJSONObject(kind);val expected=c.getJSONObject("expected");val storage=MPosInventoryStorage(db)
            for(key in MPosInventoryStorage.KEYS)storage.initialize(key,expected.get(key).toString())
            MPosCatalogStorage(db).initialize(expected.getJSONArray("products").toString());MPosRecoveryStorage(db).initialize("criticalStorageJournal","null");block(db,MPosInventoryCommand(db),c)
        }finally{db.close()}
    }
    @Test fun fixRecalculatesDifferenceAgainstCurrentStockAndPersistsBothDocuments()=runCase("fix"){db,engine,c->
        engine.commit(c.toString());assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONArray("products"),JSONArray(MPosCatalogStorage(db).read().getString("payload"))));assertTrue(MPosSupplyParity.same(c.getJSONObject("writes").getJSONObject("inventoryDraft"),JSONObject(MPosInventoryStorage(db).read("inventoryDraft").getString("payload"))))
    }
    @Test fun scheduledAndAdhocCompletionPreserveLaterSalesAndRawAuditRows(){for(kind in listOf("complete","adhoc"))runCase(kind){db,engine,c->
        engine.commit(c.toString());val storage=MPosInventoryStorage(db);for(key in listOf("inventoryHistory","inventoryConfig")){val raw=storage.read(key).getString("payload");val result=if(key=="inventoryHistory")JSONArray(raw) else JSONObject(raw);assertTrue(kind,MPosSupplyParity.same(c.getJSONObject("writes").get(key),result))}
        assertEquals("null",storage.read("inventoryDraft").getString("payload"));assertEquals(7.0,JSONArray(MPosCatalogStorage(db).read().getString("payload")).getJSONObject(0).getDouble("stock"),0.0);assertEquals(8.0,db.stockEventProjectionDao().events("inventoryHistory")[0].totalCost,0.0)
    }}
    @Test fun loaderDefaultsAndUnsavedFrequencyChoiceRemainCompatible(){
        runCase("normalized-config"){db,engine,c->val config=JSONObject(c.getJSONObject("expected").getJSONObject("inventoryConfig").toString());config.remove("customDates");config.remove("lastCompletedAt");MPosInventoryStorage(db).write("inventoryConfig",config.toString());assertTrue(engine.commit(c.toString()).getBoolean("ok"))}
        runCase("unsaved-frequency"){db,engine,c->val config=JSONObject(c.getJSONObject("expected").getJSONObject("inventoryConfig").toString()).put("frequency","monthly");MPosInventoryStorage(db).write("inventoryConfig",config.toString());engine.commit(c.toString());assertEquals("weekly",JSONObject(MPosInventoryStorage(db).read("inventoryConfig").getString("payload")).getString("frequency"))}
    }
    @Test fun staleStockOrDraftAndTamperedDifferenceCannotCommit()=runCase("fix"){db,engine,c->
        val before=c.toString();c.getJSONObject("writes").getJSONObject("inventoryDraft").getJSONArray("items").getJSONObject(0).put("difference",999)
        try{engine.commit(c.toString());fail("tampered difference accepted")}catch(_:IllegalStateException){}
        val fresh=JSONObject(before);val products=JSONArray(fresh.getJSONObject("expected").getJSONArray("products").toString());products.getJSONObject(0).put("stock",9);MPosCatalogStorage(db).write(products.toString());try{engine.commit(fresh.toString());fail("stale stock overwritten")}catch(_:IllegalStateException){}
        MPosCatalogStorage(db).write(fresh.getJSONObject("expected").getJSONArray("products").toString());val draft=JSONObject(fresh.getJSONObject("expected").getJSONObject("inventoryDraft").toString()).put("extension",99);MPosInventoryStorage(db).write("inventoryDraft",draft.toString());try{engine.commit(fresh.toString());fail("stale draft overwritten")}catch(_:IllegalStateException){}
    }
    @Test fun nullUntrackedAndAlreadyFixedRowsCannotBeFixed()=runCase("fix"){db,engine,c->
        val draft=c.getJSONObject("expected").getJSONObject("inventoryDraft");draft.getJSONArray("items").getJSONObject(0).put("actual",JSONObject.NULL);MPosInventoryStorage(db).write("inventoryDraft",draft.toString());try{engine.commit(c.toString());fail("null count accepted")}catch(_:IllegalStateException){}
        draft.getJSONArray("items").getJSONObject(0).put("actual",8);MPosInventoryStorage(db).write("inventoryDraft",draft.toString());val products=c.getJSONObject("expected").getJSONArray("products");products.getJSONObject(0).put("noStockTracking",true);MPosCatalogStorage(db).write(products.toString());try{engine.commit(c.toString());fail("untracked count accepted")}catch(_:IllegalStateException){}
        products.getJSONObject(0).remove("noStockTracking");MPosCatalogStorage(db).write(products.toString());draft.getJSONArray("items").getJSONObject(0).put("fixedAt",100);MPosInventoryStorage(db).write("inventoryDraft",draft.toString());try{engine.commit(c.toString());fail("fixed row accepted")}catch(_:IllegalStateException){}
    }
    @Test fun lastWriteFailureRollsBackProductFixation()=runCase("fix"){db,engine,c->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_inventory_draft BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'inventoryDraft' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{engine.commit(c.toString());fail("failed draft accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertTrue(MPosSupplyParity.same(c.getJSONObject("expected").getJSONArray("products"),JSONArray(MPosCatalogStorage(db).read().getString("payload"))))
    }
    @Test fun lastWriteFailureRollsBackCompletionHistoryProjectionConfigAndDraft()=runCase("complete"){db,engine,c->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_inventory_clear BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'inventoryDraft' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{engine.commit(c.toString());fail("failed clearing accepted")}catch(_:android.database.sqlite.SQLiteException){}
        val storage=MPosInventoryStorage(db);assertEquals("[]",storage.read("inventoryHistory").getString("payload"));assertEquals(0,db.stockEventProjectionDao().events("inventoryHistory").size);assertTrue(MPosSupplyParity.same(c.getJSONObject("expected").getJSONObject("inventoryConfig"),JSONObject(storage.read("inventoryConfig").getString("payload"))));assertNotEquals("null",storage.read("inventoryDraft").getString("payload"))
    }
    @Test fun missingFixationOrDuplicateCompletionDoesNotAppendHistory()=runCase("complete"){db,engine,c->
        val draft=c.getJSONObject("expected").getJSONObject("inventoryDraft");draft.getJSONArray("items").getJSONObject(0).put("fixedAt",0);MPosInventoryStorage(db).write("inventoryDraft",draft.toString());try{engine.commit(c.toString());fail("unfinished inventory completed")}catch(_:IllegalStateException){}
        draft.getJSONArray("items").getJSONObject(0).put("fixedAt",120);MPosInventoryStorage(db).write("inventoryDraft",draft.toString());engine.commit(c.toString());try{engine.commit(c.toString());fail("duplicate completion accepted")}catch(_:IllegalStateException){}
        assertEquals(1,JSONArray(MPosInventoryStorage(db).read("inventoryHistory").getString("payload")).length())
    }
}
