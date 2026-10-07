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
class MPosAvailabilityJournalTest {
    private fun input(id:String="r1")=JSONObject().put("version",1).put("id",id).put("at",1000).put("previous",7)
        .put("network",JSONObject().put("backendUrl","https://example.invalid/prefix").put("deviceKey","synthetic-test-key"))
        .put("ids",JSONArray().put(" caller "))
    private fun runCase(block:suspend(MPosDatabase,MPosAvailabilityJournal)->Unit)=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{MPosCatalogStorage(db).initialize("""[{"id":"p","type":"simple","stock":9}]""");MPosOrderStorage(db).initialize("""[{"id":"r1","webOrderId":"w1","timestamp":2},{"id":"r2","webOrderId":"w2","timestamp":3}]""");block(db,MPosAvailabilityJournal(db))}finally{db.close()}
    }
    @Test fun quantitiesMatchActualReviewedFixturesIncludingMalformedRecipes(){
        val cases=JSONArray(File("../tests/fixtures/availability-native.json").readText())
        for(i in 0 until cases.length()){
            val c=cases.getJSONObject(i);val actual=MPosAvailabilityEngine.items(c.getJSONArray("products"));val expected=c.getJSONArray("expected")
            val values=(0 until actual.length()).map{actual.getJSONObject(it).getString("externalId")}.toSet()
            val target=(0 until expected.length()).map{expected.getJSONObject(it).getString("externalId")}.toSet()
            assertEquals(c.getString("label"),target,values)
            for(id in target){val a=(0 until actual.length()).map{actual.getJSONObject(it)}.filter{it.getString("externalId")==id}.map{if(it.isNull("quantity"))null else it.getDouble("quantity")};val e=(0 until expected.length()).map{expected.getJSONObject(it)}.filter{it.getString("externalId")==id}.map{if(it.isNull("quantity"))null else it.getDouble("quantity")};assertEquals(c.getString("label")+id,e,a)}
        }
    }
    @Test fun onlyDurableReceiptGrantsOnceAndNextPaymentUsesFreshProducts()=runCase{db,journal->
        try{journal.prepare(input("missing").toString());fail("unsaved payment accepted")}catch(_:IllegalStateException){}
        val first=journal.prepare(input().toString());assertTrue(first.getBoolean("send"));assertEquals(1000,first.getJSONObject("body").getInt("revision"));assertEquals("1970-01-01T00:00:01.000Z",first.getJSONObject("body").getString("sampledAt"))
        assertEquals("caller",first.getJSONObject("body").getJSONArray("settledWebOrderIds").getString(0));assertEquals("w2",first.getJSONObject("body").getJSONArray("settledWebOrderIds").getString(1))
        assertFalse(journal.prepare(input().toString()).getBoolean("send"));val ticket=journal.consume(first.getString("token"),first.getJSONObject("body"));assertEquals(9,ticket.getJSONObject("body").getJSONArray("items").getJSONObject(0).getInt("quantity"))
        try{journal.consume(first.getString("token"),first.getJSONObject("body"));fail("consumed permit reused")}catch(_:IllegalStateException){}
        MPosCatalogStorage(db).write("""[{"id":"p","type":"simple","stock":8}]""")
        val next=journal.prepare(input("r2").toString());assertEquals(1001,next.getJSONObject("body").getInt("revision"));assertEquals(8,next.getJSONObject("body").getJSONArray("items").getJSONObject(0).getInt("quantity"))
        assertFalse(MPosAvailabilityJournal(db).prepare(input().toString()).getBoolean("send"))
    }
    @Test fun wrongTokenOrModifiedBodyCannotConsumePublicationPermit()=runCase{_,journal->
        val result=journal.prepare(input().toString());val body=result.getJSONObject("body")
        try{journal.consume("old",body);fail("stale token accepted")}catch(_:IllegalStateException){}
        val changed=JSONObject(body.toString());changed.getJSONArray("items").getJSONObject(0).put("quantity",999)
        try{journal.consume(result.getString("token"),changed);fail("changed quantities accepted")}catch(_:IllegalStateException){}
        assertNotNull(journal.consume(result.getString("token"),body))
    }
    @Test fun fileBackedRestartCannotGrantFailedOrInterruptedReceiptAgain()=runBlocking{
        val context=RuntimeEnvironment.getApplication();val name="mpos-availability-test-${java.util.UUID.randomUUID()}"
        var db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build()
        try{
            MPosCatalogStorage(db).initialize("[{\"id\":\"p\",\"type\":\"simple\",\"stock\":9}]")
            MPosOrderStorage(db).initialize("[{\"id\":\"r1\"},{\"id\":\"r2\"}]")
            val first=MPosAvailabilityJournal(db).prepare(input().toString());db.close()
            db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build()
            val journal=MPosAvailabilityJournal(db);assertFalse(journal.prepare(input().toString()).getBoolean("send"))
            // Interrupted issued ticket is never discovered/retried by lifecycle; only a fresh payment claims again.
            assertTrue(journal.prepare(input("r2").toString()).getBoolean("send"))
            try{journal.consume(first.getString("token"),first.getJSONObject("body"));fail("old interrupted ticket accepted")}catch(_:IllegalStateException){}
        }finally{db.close();context.deleteDatabase(name)}
    }
    @Test fun failedTransactionDoesNotConsumeReceiptOrAdvanceRevision()=runCase{db,journal->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_availability BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'mpos_availability_ticket_v1' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{journal.prepare(input().toString());fail("failed write accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertNull(db.legacyStorageShadowDao().get(MPosAvailabilityJournal.REVISION));assertNull(db.legacyStorageShadowDao().get("mpos_availability_payment_v1:r1"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_availability");assertTrue(journal.prepare(input().toString()).getBoolean("send"))
    }
}
