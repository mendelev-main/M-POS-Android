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
class MPosCartItemRepositoryTest {
    private val session="""{"items":[{"cartLineId":"line","productId":"p","name":"Латте","price":3.5,"qty":2,"selectedModifiers":[]}],"customer":{"id":"customer"},"source":"web","webOrderId":"w","kitchenPrinted":true,"printedItems":[{"productId":"p","qty":2}],"paymentDraft":{"paidParts":[{"amount":1}]},"extension":{"keep":true},"updatedAt":1}"""
    private suspend fun prepare(db:MPosDatabase) {
        MPosRecoveryStorage(db).initialize("currentOrderSession",session);MPosRecoveryStorage(db).initialize("criticalStorageJournal",null)
        MPosShiftStorage(db).initialize("[]");MPosEmployeeStorage(db).initialize("[]")
        MPosCatalogStorage(db).initialize("""[{"id":"p","name":"Латте","type":"simple","stock":4,"stockUnit":"piece"}]""")
    }
    private fun view(expected:JSONObject)=JSONObject().put("version",1).put("operation","cartItemView").put("expected",expected).put("id","line")
        .put("items",JSONObject(session).getJSONArray("items")).put("discounts",JSONArray()).put("currency","BYN")
    private fun save(expected:JSONObject,revision:String)=JSONObject().put("version",1).put("operation","cartItemCommit").put("expected",expected)
        .put("id","line").put("sessionRevision",revision).put("quantity",3).put("comment"," \uFEFFбез сахара\u00a0 ").put("discountId","d")
    @Test fun savesOnceAndPreservesWebKitchenPaidPartsAndUnknownMetadata()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            prepare(db);val owner=MPosWorkspaceNavigationOwner();val expected=owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos")).getJSONObject("snapshot")
            val repo=MPosCartItemRepository(db,owner);val model=repo.execute(view(expected)).getJSONObject("model")
            assertEquals(session,db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
            val command=save(expected,model.getString("sessionRevision"));val result=repo.execute(command);assertTrue(result.getBoolean("allowed"))
            val saved=JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
            assertEquals(3,saved.getJSONArray("items").getJSONObject(0).getInt("qty"));assertEquals("без сахара",saved.getJSONArray("items").getJSONObject(0).getString("comment"))
            for(key in listOf("customer","source","webOrderId","kitchenPrinted","printedItems","paymentDraft","extension"))assertTrue(key,MPosSupplyParity.same(JSONObject(session).opt(key),saved.opt(key)))
            try{repo.execute(command);fail("duplicate save accepted")}catch(_:IllegalStateException){}
            assertEquals(saved.toString(),db.currentOrderSessionProjectionDao().get()!!.payload)
        }finally{db.close()}
    }
    @Test fun freshStockInsufficiencyAndImportedSessionNeverMutateAnotherOrder()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            prepare(db);val owner=MPosWorkspaceNavigationOwner();val expected=owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos")).getJSONObject("snapshot")
            val repo=MPosCartItemRepository(db,owner);val model=repo.execute(view(expected)).getJSONObject("model")
            val result=repo.execute(save(expected,model.getString("sessionRevision")).put("quantity",5));assertFalse(result.getBoolean("allowed"))
            assertEquals(session,db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
            val imported=JSONObject(session).put("customer",JSONObject().put("id","imported")).toString();MPosRecoveryStorage(db).write("currentOrderSession",imported)
            try{repo.execute(save(expected,model.getString("sessionRevision")));fail("stale imported order accepted")}catch(_:IllegalStateException){}
            assertEquals(imported,db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
            try{repo.execute(view(expected).put("items",JSONArray()));fail("forged runtime accepted")}catch(_:IllegalStateException){}
        }finally{db.close()}
    }
    @Test fun recoveryAndSqliteFailureLeaveSessionAndProjectionUnchanged()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            prepare(db);val owner=MPosWorkspaceNavigationOwner();val expected=owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos")).getJSONObject("snapshot")
            val repo=MPosCartItemRepository(db,owner);val model=repo.execute(view(expected)).getJSONObject("model")
            MPosRecoveryStorage(db).write("criticalStorageJournal","{}")
            try{repo.execute(save(expected,model.getString("sessionRevision")));fail("recovery bypassed")}catch(_:IllegalStateException){}
            MPosRecoveryStorage(db).remove("criticalStorageJournal")
            val projection=db.currentOrderSessionProjectionDao().get()!!.payload
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_cart_session BEFORE INSERT ON current_order_session_projection BEGIN SELECT RAISE(ABORT, 'fixture'); END")
            try{repo.execute(save(expected,model.getString("sessionRevision")));fail("SQLite failure ignored")}catch(_:android.database.sqlite.SQLiteException){}
            assertEquals(session,db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);assertEquals(projection,db.currentOrderSessionProjectionDao().get()!!.payload)
        }finally{db.close()}
    }
}
