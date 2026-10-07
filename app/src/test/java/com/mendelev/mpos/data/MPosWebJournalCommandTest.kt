package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
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
class MPosWebJournalCommandTest {
    private val accept="webOrderAcceptances";private val ready="webOrderReadyJournal"
    private fun input(op:String,key:String=accept)=JSONObject().put("version",1).put("operation",op).put("key",key).put("id","w").put("body",JSONObject().put("readyEstimate","15m"))
    private fun record(stage:String="local")=JSONObject("""{"stage":"$stage","readyEstimate":"15m","parked":{"id":"p","webOrderId":"w","items":[],"extra":7},"extension":8}""")
    private fun session()=JSONObject("""{"source":"web","webOrderId":"w","webOrderStatus":"accepted","items":[{"productId":"x","qty":2}],"customer":{"id":"c"},"paymentDraft":{"parts":[{"amount":5,"paid":true}]}}""")
    private fun runCase(block:suspend(MPosDatabase,MPosWebJournalStorage,MPosWebJournalCommand)->Unit)=runBlocking{
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val storage=MPosWebJournalStorage(db);storage.initialize(accept,"{}");storage.initialize(ready,"{}");MPosParkedOrderStorage(db).initialize("""[{"id":"p","webOrderId":"w","items":[]}]""");MPosRecoveryStorage(db).initialize("currentOrderSession",session().toString());block(db,storage,MPosWebJournalCommand(db))}finally{db.close()}
    }
    private suspend fun reject(engine:MPosWebJournalCommand,c:JSONObject){try{engine.execute(c.toString());fail("invalid command accepted")}catch(_:IllegalStateException){}}
    @Test fun authorityMigrationNullRemoveAndProjectionPreserveFullRecords()=runCase{db,storage,_->
        storage.write(accept,JSONObject().put("w",record()).toString());storage.initialize(accept,"{}");assertEquals(8,JSONObject(storage.read(accept).getString("payload")).getJSONObject("w").getInt("extension"));assertEquals("15m",db.webAcceptanceProjectionDao().all().single().readyEstimate)
        storage.write(ready,"null");assertEquals("null",storage.read(ready).getString("payload"));assertEquals(0,db.webReadyProjectionDao().all().size);storage.remove(ready);assertFalse(storage.read(ready).getBoolean("found"))
    }
    @Test fun acceptanceRequiresLocalParkedValidEstimateAndPersistedAckProof()=runCase{db,storage,engine->
        storage.write(accept,JSONObject().put("w",record("prepared")).toString());reject(engine,input("gate"))
        storage.write(accept,JSONObject().put("w",record()).toString());reject(engine,input("gate").put("body",JSONObject().put("readyEstimate","wrong")))
        val expected=JSONObject(storage.read(accept).getString("payload"));val next=JSONObject(expected.toString());next.getJSONObject("w").put("stage","confirmed")
        reject(engine,input("patch").put("expected",expected).put("next",next));val gate=engine.execute(input("gate").toString());engine.execute(input("ack").put("token",gate.getString("token")).toString())
        engine.execute(input("patch").put("expected",expected).put("next",next).toString());assertEquals("confirmed",db.webAcceptanceProjectionDao().all().single().stage)
        assertTrue(engine.execute(input("gate").toString()).getBoolean("confirmed"))
    }
    @Test fun conflictSafePatchesPreserveOtherRecordsAndRejectSameRecordRace()=runCase{_,storage,engine->
        val expected=JSONObject().put("w",record());storage.write(accept,expected.toString());val concurrent=JSONObject(expected.toString()).put("other",record("prepared"));storage.write(accept,concurrent.toString())
        val next=JSONObject(expected.toString());next.getJSONObject("w").put("readyEstimate","30m");engine.execute(input("patch").put("expected",expected).put("next",next).toString())
        assertTrue(JSONObject(storage.read(accept).getString("payload")).has("other"));reject(engine,input("patch").put("expected",expected).put("next",JSONObject()))
    }
    @Test fun readyAtomicCommitProvidesPendingProofEvenAfterSessionMovesOn()=runCase{db,storage,engine->
        val before=session();val next=JSONObject(before.toString()).put("webOrderStatus","ready")
        engine.execute(input("markReady",ready).put("at",10).put("expectedSession",before).put("session",next).toString())
        assertEquals("ready",JSONObject(MPosRecoveryStorage(db).read("currentOrderSession").getString("payload")).getString("webOrderStatus"));assertEquals("pending",db.webReadyProjectionDao().all().single().stage)
        MPosRecoveryStorage(db).write("currentOrderSession","{\"items\":[]}")
        val gate=engine.execute(input("gate",ready).toString());engine.execute(input("ack",ready).put("token",gate.getString("token")).toString());val expected=JSONObject(storage.read(ready).getString("payload"));val confirmed=JSONObject(expected.toString());confirmed.getJSONObject("w").put("stage","confirmed")
        engine.execute(input("patch",ready).put("expected",expected).put("next",confirmed).toString());engine.execute(input("patch",ready).put("expected",confirmed).put("next",JSONObject()).toString());assertEquals(0,db.webReadyProjectionDao().all().size)
    }
    @Test fun preparedReadyNeverAcknowledgesAndPendingRequiresLocalStatus()=runCase{_,storage,engine->
        val before=JSONObject().put("w",JSONObject().put("stage","prepared"));storage.write(ready,before.toString());reject(engine,input("gate",ready));reject(engine,input("patch",ready).put("expected",before).put("next",JSONObject()));val next=JSONObject(before.toString());next.getJSONObject("w").put("stage","pending");reject(engine,input("patch",ready).put("expected",before).put("next",next))
    }
    @Test fun staleAckCannotConfirmChangedRecord()=runCase{_,storage,engine->
        storage.write(accept,JSONObject().put("w",record()).toString());val gate=engine.execute(input("gate").toString());storage.write(accept,JSONObject().put("w",record().put("readyEstimate","30m")).toString());reject(engine,input("ack").put("token",gate.getString("token")))
    }
    @Test fun failedJournalIndexRollsBackReadySessionAndIntent()=runCase{db,storage,engine->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_ready BEFORE INSERT ON web_ready_projection BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{engine.execute(input("markReady",ready).put("at",10).put("expectedSession",session()).put("session",session().put("webOrderStatus","ready")).toString());fail("write failure accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals("accepted",JSONObject(MPosRecoveryStorage(db).read("currentOrderSession").getString("payload")).getString("webOrderStatus"));assertEquals("{}",storage.read(ready).getString("payload"))
    }
}
