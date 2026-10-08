package com.mendelev.mpos.workspace

import androidx.room.Room
import com.mendelev.mpos.data.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosCartEditRepositoryTest {
    private fun session()=JSONObject("""{"items":[{"cartLineId":"line","productId":"p","qty":1,"price":5}],"customer":{"id":"c"},"source":"web","webOrderId":"w","kitchenPrinted":true,"printedItems":[{"productId":"p","qty":1}],"paymentDraft":{"paidParts":[{"amount":1}]},"extension":{"keep":true}}""")
    private suspend fun prepare(db:MPosDatabase,s:JSONObject){
        MPosCatalogStorage(db).initialize("""[{"id":"p","type":"simple","stock":10,"stockUnit":"piece"}]""")
        MPosRecoveryStorage(db).initialize("currentOrderSession",s.toString());MPosRecoveryStorage(db).initialize("criticalStorageJournal",null)
        MPosShiftStorage(db).initialize("[]");MPosEmployeeStorage(db).initialize("[]")
    }
    private fun owner()=MPosWorkspaceNavigationOwner().also{it.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos"))}
    private fun command(o:MPosWorkspaceNavigationOwner,s:JSONObject,operation:String)=JSONObject().put("version",1).put("operation",operation).put("id","line").put("session",s)
        .put("expected",o.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot"))
    @Test fun lastRemovalResetsButDecrementToZeroRetainsOrderContext()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val s=session();prepare(db,s);val o=owner();val repo=MPosWorkspaceNavigationRepository(db,o)
            val decrement=repo.execute(command(o,s,"cartQuantityCommit").put("delta",-1));assertTrue(decrement.getBoolean("allowed"));assertFalse(decrement.has("resetState"))
            var saved=JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);assertEquals(0,saved.getJSONArray("items").length())
            for(key in listOf("customer","source","webOrderId","printedItems","paymentDraft","extension"))assertTrue(key,MPosSupplyParity.same(s.get(key),saved.get(key)))
            MPosRecoveryStorage(db).write("currentOrderSession",s.toString())
            val removed=repo.execute(command(o,s,"cartRemoveCommit"));assertTrue(removed.getBoolean("allowed"));assertEquals("",removed.getJSONObject("resetState").getJSONObject("customer").getString("id"))
            assertEquals(0,removed.getJSONObject("resetState").getJSONArray("_splitPayments").length())
            saved=JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);assertFalse(saved.has("paymentDraft"));assertFalse(saved.has("extension"));assertFalse(saved.getBoolean("kitchenPrinted"));assertEquals("",saved.getString("webOrderId"))
            assertEquals(saved.toString(),db.currentOrderSessionProjectionDao().get()!!.payload)
        }finally{db.close()}
    }
    @Test fun duplicateKeysPreflightAllRowsButQuantityMutatesOnlyFirstAndRemovalDeletesAll()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val s=session();s.getJSONArray("items").put(JSONObject(s.getJSONArray("items").getJSONObject(0).toString()));prepare(db,s);val o=owner();val repo=MPosCartEditRepository(db,o)
            assertFalse(repo.execute(command(o,s,"cartQuantityCommit").put("delta",5)).getBoolean("allowed"));assertEquals(s.toString(),db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
            val changed=repo.execute(command(o,s,"cartQuantityCommit").put("delta",1));assertTrue(changed.getBoolean("allowed"));assertEquals(2,changed.getJSONArray("items").getJSONObject(0).getInt("qty"));assertEquals(1,changed.getJSONArray("items").getJSONObject(1).getInt("qty"))
            val fresh=JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);val removed=repo.execute(command(o,fresh,"cartRemoveCommit"));assertEquals(0,removed.getJSONArray("items").length())
        }finally{db.close()}
    }
    @Test fun partialRemoveKeepsExtensionsAndNumericStringQuantityKeepsSourceAddition()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val s=session();s.getJSONArray("items").getJSONObject(0).put("qty","2");s.getJSONArray("items").put(JSONObject("""{"cartLineId":"other","productId":"p","qty":1,"price":5}"""));prepare(db,s);val o=owner();val repo=MPosCartEditRepository(db,o)
            assertFalse(repo.execute(command(o,s,"cartQuantityCommit").put("delta",1)).getBoolean("allowed")) // legacy "2" + 1 is 21: stock failure.
            assertFalse(repo.execute(command(o,s,"cartQuantityCommit").put("delta",-1)).getBoolean("allowed")) // "2-1" cannot be persisted as a quantity.
            val malformed=JSONObject(s.toString());malformed.getJSONArray("items").getJSONObject(0).remove("qty")
            MPosRecoveryStorage(db).write("currentOrderSession",malformed.toString())
            assertFalse(repo.execute(command(o,malformed,"cartQuantityCommit").put("delta",1)).getBoolean("allowed"))
            assertEquals(malformed.toString(),db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
            MPosRecoveryStorage(db).write("currentOrderSession",s.toString())
            val supplied=JSONObject(s.toString()).put("customer",JSONObject().put("id","new"));val removed=repo.execute(command(o,supplied,"cartRemoveCommit"));assertEquals(1,removed.getJSONArray("items").length());assertFalse(removed.has("resetState"))
            val saved=JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);assertTrue(saved.getJSONObject("extension").getBoolean("keep"));assertEquals("new",saved.getJSONObject("customer").getString("id"));assertTrue(saved.has("paymentDraft"))
        }finally{db.close()}
    }
    @Test fun staleProjectionRecoveryAndSqliteFailureDoNotPersistPartialMutation()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val s=session();prepare(db,s);val o=owner();val repo=MPosCartEditRepository(db,o);val cmd=command(o,s,"cartRemoveCommit")
            try{repo.execute(command(o,JSONObject(s.toString()).put("items",JSONArray()),"cartRemoveCommit"));fail("stale accepted")}catch(_:IllegalStateException){}
            MPosRecoveryStorage(db).write("criticalStorageJournal","{}")
            try{repo.execute(cmd);fail("recovery bypassed")}catch(_:IllegalStateException){}
            MPosRecoveryStorage(db).remove("criticalStorageJournal")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_edit_projection BEFORE INSERT ON current_order_session_projection BEGIN SELECT RAISE(ABORT, 'fixture'); END")
            try{repo.execute(cmd);fail("partial mutation persisted")}catch(_:android.database.sqlite.SQLiteException){}
            assertEquals(s.toString(),db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);assertEquals(s.toString(),db.currentOrderSessionProjectionDao().get()!!.payload)
        }finally{db.close()}
    }
}
