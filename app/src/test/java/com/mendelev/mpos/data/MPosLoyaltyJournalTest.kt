package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import java.io.File
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
class MPosLoyaltyJournalTest {
    private fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject&&b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet()&&a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray&&b is JSONArray -> a.length()==b.length()&&(0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number&&b is Number -> a.toDouble()==b.toDouble()
        else -> a==b
    }
    private fun row()=JSONObject("""{"id":"o","customer":{"id":"c"},"items":[{"productId":"p","qty":2}],"total":12,"loyaltySync":{"status":"pending"},"extension":7}""")
    private fun command(op:String)=JSONObject().put("version",1).put("operation",op).put("id","o").put("kind","sale").put("at",10)
    private fun runCase(block:suspend(MPosDatabase,MPosOrderStorage,MPosLoyaltyJournal)->Unit)=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val archive=MPosOrderStorage(db);archive.initialize(JSONArray().put(row()).toString());block(db,archive,MPosLoyaltyJournal(db))}finally{db.close()}
    }
    private fun finish(claim:JSONObject,success:Boolean=true)=command("finish").put("token",claim.getString("token")).put("success",success).put("data",JSONObject().put("events",JSONArray().put("event"))).put("error","offline")
    @Test fun realRoomTransitionsAndPayloadMatchReviewedSourceFixtures()=runCase{db,archive,journal->
        val f=listOf(File("../tests/fixtures/loyalty-journal.json"),File("tests/fixtures/loyalty-journal.json")).first{it.exists()};val cases=JSONArray(f.readText())
        for(i in 0 until cases.length()){
            val c=cases.getJSONObject(i);archive.write(JSONArray().put(c.getJSONObject("before")).toString());val claim=journal.execute(command("claim").put("kind",c.getString("kind")).toString());val requests=c.getJSONArray("requests")
            assertEquals("send $i",requests.length()>0,claim.getBoolean("send"))
            if(claim.getBoolean("send")){
                assertTrue("body $i",same(requests.getJSONObject(0).getJSONObject("body"),claim.getJSONObject("payload")))
                assertTrue("sending $i",same(c.getJSONArray("writes").getJSONObject(0),JSONObject(db.orderProjectionDao().get("o")!!.payload)))
                journal.execute(finish(claim,c.getBoolean("success")).put("kind",c.getString("kind")).toString())
            }
            assertTrue("finished $i",same(c.getJSONObject("after"),JSONObject(db.orderProjectionDao().get("o")!!.payload)))
        }
    }
    @Test fun recoveryKeepsActiveAttemptAndPersistsInterruptedPending()=runCase{db,_,journal->
        journal.execute(command("claim").toString())
        val protected=journal.execute(command("recover").put("active",JSONArray().put("sale:o")).toString());assertEquals(0,protected.getJSONArray("actions").length());assertEquals("sending",db.orderProjectionDao().get("o")!!.loyaltySyncStatus)
        val recovered=journal.execute(command("recover").put("active",JSONArray()).toString());assertEquals(1,recovered.getJSONArray("actions").length());assertEquals("pending",db.orderProjectionDao().get("o")!!.loyaltySyncStatus)
        assertEquals(10,JSONObject(db.orderProjectionDao().get("o")!!.payload).getJSONObject("loyaltySync").getInt("recoveredAt"))
    }
    @Test fun staleTokenPayloadReplacementAndDuplicateClaimCannotFinishAnotherAttempt()=runCase{db,archive,journal->
        val first=journal.execute(command("claim").toString());assertFalse(journal.execute(command("claim").toString()).getBoolean("send"))
        assertFalse(journal.execute(finish(first).put("token","wrong").toString()).getBoolean("applied"))
        val replacement=row().put("customer",JSONObject().put("id","new")).put("loyaltySync",JSONObject().put("status","sending"));archive.write(JSONArray().put(replacement).toString())
        assertFalse(journal.execute(finish(first).toString()).getBoolean("applied"));assertEquals("new",JSONObject(db.orderProjectionDao().get("o")!!.payload).getJSONObject("customer").getString("id"))
    }
    @Test fun finishPreservesConcurrentReturnAndOtherReceiptsThenRequestsReversal()=runCase{db,archive,journal->
        val claim=journal.execute(command("claim").toString());val returned=JSONObject(db.orderProjectionDao().get("o")!!.payload).put("returnedAt",11).put("returnAmount",12).put("loyaltyReversal",JSONObject().put("status","pending"));val other=row().put("id","other").put("extension",99)
        archive.write(JSONArray().put(returned).put(other).toString());val result=journal.execute(finish(claim).toString());assertTrue(result.getBoolean("reverseNext"));assertEquals(11,result.getJSONObject("order").getInt("returnedAt"));assertEquals(other.toString(),db.orderProjectionDao().get("other")!!.payload)
        val reversal=journal.execute(command("claim").put("kind","settle").toString());assertEquals("reversal",reversal.getString("kind"));assertEquals("c",reversal.getJSONObject("payload").getString("customerId"))
    }
    @Test fun writeFailureRollsBackClaimAndFailedFinishRemainsRecoverable()=runCase{db,_,journal->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_loyalty BEFORE INSERT ON order_projection BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{journal.execute(command("claim").toString());fail("claim failure ignored")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals("pending",db.orderProjectionDao().get("o")!!.loyaltySyncStatus);assertNull(db.legacyStorageShadowDao().get("mpos_loyalty_attempt_v1:sale:o"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_loyalty");val claim=journal.execute(command("claim").toString())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_loyalty BEFORE INSERT ON order_projection BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{journal.execute(finish(claim).toString());fail("finish failure ignored")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals("sending",db.orderProjectionDao().get("o")!!.loyaltySyncStatus);assertNotNull(db.legacyStorageShadowDao().get("mpos_loyalty_attempt_v1:sale:o"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_loyalty");journal.execute(command("recover").put("active",JSONArray()).toString());assertEquals("pending",db.orderProjectionDao().get("o")!!.loyaltySyncStatus)
    }
    @Test fun settlementRequiresReturnedReceiptAndPendingSalePrecedesReversal()=runCase{_,archive,journal->
        assertFalse(journal.execute(command("claim").put("kind","settle").toString()).getBoolean("send"))
        val returned=row().put("returnedAt",1).put("loyaltyReversal",JSONObject().put("status","pending"))
        archive.write(JSONArray().put(returned).toString());val sale=journal.execute(command("claim").put("kind","settle").toString());assertEquals("sale",sale.getString("kind"))
        journal.execute(finish(sale,false).toString());val retry=journal.execute(command("recover").put("active",JSONArray()).toString());assertEquals("sale",retry.getJSONArray("actions").getJSONObject(0).getString("kind"))
    }

}
