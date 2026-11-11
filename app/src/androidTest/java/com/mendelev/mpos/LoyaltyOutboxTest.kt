package com.mendelev.mpos

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Isolated Room database and fake HTTP only; never opens the application's dataset. */
class LoyaltyOutboxTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val settings=BackendSettings(url="https://fixture.example.org",deviceKey="fixture-key",datasetId="fixture")
    private val line=CartLine("line","product","Кофе","10",false,"10","1.5","шт",emptyList(),emptyList(),emptyMap())
    private fun cart()=CurrentCart(sessionId="session",lines=listOf(line),loyalty=LoyaltySelection(settings.loyaltyTarget(),LoyaltyCustomer("customer","Тест","375290000000",active=true,consentVersion=4),emptyList(),datasetId="fixture"))
    private val receipt=ReceiptEntity("receipt","command","session","shift",1,1000,1500,1500,0,0,"fixture")
    private suspend fun seed(db: CatalogDatabase,outbox: LoyaltyOutbox) {
        db.shifts().insert(ShiftEntity("shift","register","actor","Кассир",0,"UTC",0,null,null,null,null,null,1))
        db.sales().insert(receipt);outbox.enqueue(receipt,cart(),emptyMap())
    }
    @Test fun receiptAndIntentRollbackTogetherAndReplaySurvivesRepositoryRecreation()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val outbox=LoyaltyOutbox(db)
            try {db.withTransaction {seed(db,outbox);throw IllegalStateException("fixture rollback")}} catch(_:IllegalStateException) {}
            assertNull(db.sales().receipt(receipt.id));assertTrue(outbox.pending(settings.loyaltyTarget(),"fixture").isEmpty())
            db.withTransaction {seed(db,outbox)}
            val restored=LoyaltyOutbox(db).pending(settings.loyaltyTarget(),"fixture").single()
            val body=JSONObject(restored.body)
            assertEquals("receipt",body.getString("orderId"));assertEquals(4L,body.getLong("consentVersion"))
            assertEquals("1.5",body.getJSONArray("items").getJSONObject(0).get("quantity").toString())
            assertTrue(outbox.pending(settings.loyaltyTarget(),"another-dataset").isEmpty())
            try {outbox.reverse(receipt.id);fail("must share refund transaction")} catch(_:IllegalStateException) {}
        } finally {db.close()}
    }
    @Test fun lostAcknowledgementReplaysOriginalSaleThenPendingRefundOnce()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val outbox=LoyaltyOutbox(db);db.withTransaction {seed(db,outbox);outbox.reverse(receipt.id)}
            val calls=mutableListOf<Pair<String,String>>();val applied=mutableSetOf<String>();var lose=true
            val http=BackendHttp(OkHttpClient.Builder().addInterceptor {chain->
                val request=chain.request();val buffer=Buffer();request.body!!.writeTo(buffer)
                val body=buffer.readUtf8();calls+=request.url.encodedPath to body
                val key=request.url.encodedPath+JSONObject(body).getString("orderId");applied+=key
                if(lose) {lose=false;throw IOException("applied; acknowledgement lost")}
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body("{\"ok\":true,\"events\":[],\"programs\":[]}".toResponseBody("application/json".toMediaType())).build()
            }.build())
            try {LoyaltyDelivery.deliver(outbox,settings,http) {};fail()} catch(_:BackendFailure) {}
            assertFalse(outbox.read(receipt.id)!!.saleDone)
            LoyaltyDelivery.deliver(LoyaltyOutbox(db),settings,http) {}
            assertEquals(listOf("/api/loyalty/sales","/api/loyalty/sales","/api/loyalty/reversal"),calls.map {it.first})
            assertEquals(calls[0].second,calls[1].second);assertEquals(2,applied.size)
            assertTrue(outbox.read(receipt.id)!!.reversalDone);assertTrue(outbox.pending(settings.loyaltyTarget(),"fixture").isEmpty())
            LoyaltyDelivery.deliver(outbox,settings,http) {};assertEquals(3,calls.size)
        } finally {db.close()}
    }
    @Test fun serverConflictStopsAutomaticReplayWithoutPretendingSuccess()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val outbox=LoyaltyOutbox(db);db.withTransaction {seed(db,outbox)};var requests=0
            val http=BackendHttp(OkHttpClient.Builder().addInterceptor {chain->requests++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(409).message("fixture")
                    .body("{}".toResponseBody("application/json".toMediaType())).build()
            }.build())
            LoyaltyDelivery.deliver(outbox,settings,http) {};LoyaltyDelivery.deliver(outbox,settings,http) {}
            assertEquals(1,requests);assertFalse(outbox.read(receipt.id)!!.saleDone)
            assertEquals("http_409",outbox.read(receipt.id)!!.blocked)
        } finally {db.close()}
    }
}
