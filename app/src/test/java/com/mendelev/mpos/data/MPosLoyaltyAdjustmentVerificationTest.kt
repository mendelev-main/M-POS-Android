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
class MPosLoyaltyAdjustmentVerificationTest {
    @Test fun pendingGateSurvivesRepositoryRestartAndCannotReplayUntilVerified()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val config=JSONObject().put("backendUrl","https://example.invalid").put("deviceKey","synthetic-key")
            val journal=MPosLoyaltyAdjustmentVerification(db);val token=journal.begin(config,"customer","program")
            val restarted=MPosLoyaltyAdjustmentVerification(db)
            assertEquals(token,restarted.ticket(config,"customer","program"))
            try{restarted.begin(config,"customer","program");fail("unchecked replay accepted")}catch(_:IllegalStateException){}
            assertNull(restarted.ticket(config,"other","program"))
            assertNull(restarted.ticket(JSONObject(config.toString()).put("backendUrl","https://other.invalid"),"customer","program"))
            restarted.finish(config,"customer","program","stale-token");assertEquals(token,restarted.ticket(config,"customer","program"))
            restarted.finish(config,"customer","program",token);assertNull(restarted.ticket(config,"customer","program"))
            val next=restarted.begin(config,"customer","program");assertNotEquals(token,next)
            restarted.finish(config,"customer","program",token);assertEquals(next,restarted.ticket(config,"customer","program"))
            val doc=db.legacyStorageShadowDao().get("mpos_loyalty_adjustment_verification_v1")!!.payload
            assertFalse(doc.contains("synthetic-key"));assertFalse(doc.contains("adminPassword"))
        }finally{db.close()}
    }

    @Test fun reopeningDatabaseRetainsGateAndOnlyMatchingNativeProfileCanReleaseIt()=runBlocking {
        val app:android.app.Application=RuntimeEnvironment.getApplication()
        val name="loyalty-verification-${java.util.UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(app,MPosDatabase::class.java,name).build()
        try {
            val config=JSONObject().put("backendUrl","https://example.invalid").put("deviceKey","synthetic-key")
            val token=MPosLoyaltyAdjustmentVerification(db).begin(config,"c","program")
            db.close();db=Room.databaseBuilder(app,MPosDatabase::class.java,name).build()
            val journal=MPosLoyaltyAdjustmentVerification(db);assertEquals(token,journal.ticket(config,"c","program"))
            val balance=MPosLoyaltyBalanceVerification(db)
            try{balance.complete(config,"c","program",token,JSONObject().put("programs",org.json.JSONArray().put(JSONObject().put("id","other"))));fail("wrong balance accepted")}catch(_:IllegalStateException){}
            assertEquals(token,journal.ticket(config,"c","program"))
            val data=JSONObject().put("programs",org.json.JSONArray().put(JSONObject().put("id","program").put("progress",3).put("rewards",1)))
            assertEquals(1,balance.complete(config,"c","program",token,data).getInt("rewards"));assertNull(journal.ticket(config,"c","program"))
            val next=journal.begin(config,"c","program")
            balance.complete(config,"c","program",token,data);assertEquals(next,journal.ticket(config,"c","program"))
        }finally{db.close();app.deleteDatabase(name)}
    }
}
