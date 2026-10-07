package com.mendelev.mpos.print

import androidx.room.Room
import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.MPosOrderStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosPrintServiceTest {
    private fun request(trigger:String="manual-receipt")=JSONObject("""{"action":"routePrint","version":1,"trigger":"$trigger","requestId":"test","now":123,"order":{"id":"paid","total":25,"items":[]},"printers":[{"ip":"192.168.1.10","printReceipts":true,"copies":1}]}""")
    private fun runCase(block:suspend(MPosDatabase,CoroutineScope)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try{block(db,scope)}finally{scope.cancel();db.close()}
    }
    private suspend fun rows(db:MPosDatabase)=JSONArray(db.legacyStorageShadowDao().get(MPosPrintJobRepository.KEY)!!.payload)
    private fun awaitEvent(events:LinkedBlockingQueue<JSONObject>,type:String):JSONObject {repeat(10){val e=events.poll(5,TimeUnit.SECONDS)?:error("no print event");if(e.optString("type")==type)return e};error("event missing")}
    @Test fun durableAdmissionAndSendingPrecedeTransportThenSentWithoutCopyingCustomerPayload()=runCase{db,scope->
        val events=LinkedBlockingQueue<JSONObject>();val sent=LinkedBlockingQueue<JSONObject>()
        val service=MPosPrintService(db,scope,{events.add(it)},{order->runBlocking{assertEquals("sending",rows(db).getJSONObject(0).getString("status"));assertFalse(rows(db).getJSONObject(0).has("order"))};sent.add(order)})
        try{service.handle(request());awaitEvent(events,"printed");assertEquals(25,sent.poll(5,TimeUnit.SECONDS)!!.getInt("total"));assertEquals("sent",rows(db).getJSONObject(0).getString("status"))}finally{service.close()}
    }
    @Test fun connectionFailureIsUncertainAndNeverRetransmittedByRestart()=runCase{db,scope->
        val events=LinkedBlockingQueue<JSONObject>();var attempts=0;val service=MPosPrintService(db,scope,{events.add(it)},{attempts++;throw java.io.IOException("test failure")})
        try{service.handle(request());awaitEvent(events,"printError");assertEquals(1,attempts);assertEquals("uncertain",rows(db).getJSONObject(0).getString("status"));MPosPrintJobRepository(db).recover();assertEquals(1,attempts);assertEquals("uncertain",rows(db).getJSONObject(0).getString("status"))}finally{service.close()}
    }
    @Test fun automaticPaymentPrintUsesPersistedReceiptNotClientTotalsAndMissingReceiptCannotPrint()=runCase{db,scope->
        MPosOrderStorage(db).initialize("[{\"id\":\"paid\",\"total\":17,\"items\":[],\"kitchenPrinted\":true},{\"id\":\"unrelated\",\"total\":1}]")
        // An unrelated row must not be parsed for printing a single persisted receipt.
        db.orderProjectionDao().insertOrders(listOf(db.orderProjectionDao().get("unrelated")!!.copy(payload="invalid-unrelated-json")))
        val events=LinkedBlockingQueue<JSONObject>();val sent=LinkedBlockingQueue<JSONObject>();val service=MPosPrintService(db,scope,{events.add(it)},{sent.add(it)})
        try{service.handle(request("completed"));awaitEvent(events,"printed");assertEquals(17,sent.poll(5,TimeUnit.SECONDS)!!.getInt("total"));assertEquals(17,MPosOrderStorage(db).receipt("paid")!!.getInt("total"));assertEquals("invalid-unrelated-json",db.orderProjectionDao().get("unrelated")!!.payload);val missing=request("completed");missing.getJSONObject("order").put("id","missing");service.handle(missing);awaitEvent(events,"printError");assertTrue(sent.isEmpty())}finally{service.close()}
    }
    @Test fun failedAdmissionOrSendingStatusWriteCannotTransmit()=runCase{db,scope->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_print_job BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'mpos_print_jobs_v1' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val events=LinkedBlockingQueue<JSONObject>();val sent=LinkedBlockingQueue<JSONObject>();val service=MPosPrintService(db,scope,{events.add(it)},{sent.add(it)})
        try{service.handle(request());awaitEvent(events,"printError");assertTrue(sent.isEmpty());assertNull(db.legacyStorageShadowDao().get(MPosPrintJobRepository.KEY))}finally{service.close()}
    }
    @Test fun restartCancelsQueuedAndMarksSendingUncertainWithoutReplay()=runCase{db,_->
        val repository=MPosPrintJobRepository(db);val request=request();val jobs=repository.append(request,JSONArray().put(JSONObject()).put(JSONObject()));repository.transition(jobs[0].getString("id"),"sending");repository.recover();val statuses=(0 until rows(db).length()).map{rows(db).getJSONObject(it).getString("status")};assertEquals(listOf("uncertain","cancelled"),statuses)
        try{repository.transition(jobs[0].getString("id"),"sending");fail("uncertain replay")}catch(_:IllegalStateException){}
    }
    @Test fun failedSendingWriteStopsTransportAndFinalWriteFailureStaysUncertain()=runCase{db,scope->
        val events=LinkedBlockingQueue<JSONObject>();val sent=LinkedBlockingQueue<JSONObject>();val service=MPosPrintService(db,scope,{events.add(it)},{sent.add(it)})
        try{
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_print_sending BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'mpos_print_jobs_v1' AND instr(NEW.payload, '\"sending\"') > 0 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            service.handle(request());awaitEvent(events,"printError");assertTrue(sent.isEmpty());assertEquals("cancelled",rows(db).getJSONObject(0).getString("status"));db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_print_sending")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_print_sent BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'mpos_print_jobs_v1' AND instr(NEW.payload, '\"sent\"') > 0 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            service.handle(request());val error=awaitEvent(events,"printError");assertTrue(error.getString("message").contains("статус печати не сохранён"));assertNotNull(sent.poll(5,TimeUnit.SECONDS));assertEquals("sending",rows(db).getJSONObject(1).getString("status"));db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_print_sent");MPosPrintJobRepository(db).recover();assertEquals("uncertain",rows(db).getJSONObject(1).getString("status"))
        }finally{service.close()}
    }
    @Test fun zeroPrintersAdmitsZeroAndInvalidEndpointFailsWithoutNetwork()=runCase{db,scope->
        val events=LinkedBlockingQueue<JSONObject>();val sent=LinkedBlockingQueue<JSONObject>();val service=MPosPrintService(db,scope,{events.add(it)},{sent.add(it)})
        try{val empty=request("shift-close").put("printers",JSONArray());service.handle(empty);val result=awaitEvent(events,"printAdmission");assertTrue(result.getBoolean("ok"));assertEquals(0,result.getInt("count"));val invalid=request();invalid.getJSONArray("printers").getJSONObject(0).put("ip","300.1.1.1");service.handle(invalid);awaitEvent(events,"printError");assertTrue(sent.isEmpty());assertEquals("failed",rows(db).getJSONObject(0).getString("status"))}finally{service.close()}
    }
    @Test fun journalRetentionIsBoundedAndPreservesActiveJobs()=runCase{db,_->
        val repository=MPosPrintJobRepository(db);val input=request();val active=repository.append(input,JSONArray().put(JSONObject()))[0]
        repeat(205){val job=repository.append(input,JSONArray().put(JSONObject()))[0];repository.transition(job.getString("id"),"sending");repository.transition(job.getString("id"),"sent")}
        assertEquals(200,rows(db).length());assertTrue((0 until rows(db).length()).any{rows(db).getJSONObject(it).getString("id")==active.getString("id")})
    }
    @Test fun legacyNoncanonicalReceiptLookupKeepsFirstDuplicateAndNumericIds()=runCase{db,_->
        val storage=MPosOrderStorage(db);storage.initialize("[{\"id\":7,\"total\":10,\"extension\":{\"x\":1}},{\"id\":7,\"total\":20}]")
        assertEquals(10,storage.receipt(7)!!.getInt("total"));assertEquals(1,storage.receipt(7)!!.getJSONObject("extension").getInt("x"));assertNull(storage.receipt("7"));assertNull(storage.receipt("missing"))
    }
}
