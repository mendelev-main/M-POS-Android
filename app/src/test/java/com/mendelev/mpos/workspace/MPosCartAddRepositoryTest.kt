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
class MPosCartAddRepositoryTest {
    private val catalogue="""[{"id":"p","name":"Кофе","type":"simple","price":5,"stock":30,"stockUnit":"piece","modifierGroups":[{"id":"g","name":"Молоко","min":1,"max":2,"options":[{"id":"o","productId":"milk","qty":0.5,"priceDelta":1,"posName":"Добавить молоко"}]}]},{"id":"milk","name":"Молоко","type":"simple","stock":20,"stockUnit":"l"}]"""
    private fun seed()=JSONObject("""{"items":[],"orderType":"На месте","customer":{"id":"c"},"source":"web","webOrderId":"w","kitchenPrinted":true,"printedItems":[{"productId":"p","qty":1}],"paymentDraft":{"paidParts":[{"amount":1}]},"loyaltyPrograms":[],"loyaltyRedemptions":{}}""")
    private suspend fun prepare(db:MPosDatabase,session:String?=null,products:String=catalogue) {
        MPosCatalogStorage(db).initialize(products);MPosRecoveryStorage(db).initialize("currentOrderSession",session)
        MPosRecoveryStorage(db).initialize("criticalStorageJournal",null);MPosShiftStorage(db).initialize("[]");MPosEmployeeStorage(db).initialize("[]")
    }
    private fun owner()=MPosWorkspaceNavigationOwner().also{it.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos"))}
    private fun view(owner:MPosWorkspaceNavigationOwner,session:JSONObject)=JSONObject().put("version",1).put("operation","cartAddView").put("expected",owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot"))
        .put("id","p").put("session",session).put("currency","BYN")
    private fun commit(view:JSONObject,model:JSONObject)=JSONObject(view.toString()).put("operation","cartAddCommit").put("sessionRevision",model.getString("sessionRevision"))
        .put("catalogRevision",model.getString("catalogRevision")).put("selections",JSONArray("[[0]]"))
    @Test fun firstAddPersistsConfiguredLineAndContextAndRejectsDuplicateCommit()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            prepare(db);val owner=owner();val repo=MPosWorkspaceNavigationRepository(db,owner);val view=view(owner,seed())
            val model=repo.execute(view).getJSONObject("model");assertNull(db.legacyStorageShadowDao().get("currentOrderSession"))
            val command=commit(view,model);val result=repo.execute(command);assertTrue(result.getBoolean("allowed"))
            val saved=JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);val item=saved.getJSONArray("items").getJSONObject(0)
            assertEquals(6.0,item.getDouble("price"),0.0);assertEquals(5.0,item.getDouble("basePrice"),0.0);assertFalse(item.getBoolean("manualPrice"));assertEquals(1,item.getInt("qty"));assertTrue(item.getString("cartLineId").isNotEmpty())
            assertEquals("Добавить молоко",item.getJSONArray("selectedModifiers").getJSONObject(0).getString("name"));assertEquals(0.5,item.getJSONArray("selectedModifiers").getJSONObject(0).getDouble("qty"),0.0)
            for(key in listOf("customer","source","webOrderId","kitchenPrinted","printedItems","paymentDraft"))assertTrue(key,MPosSupplyParity.same(seed().get(key),saved.get(key)))
            assertEquals(saved.toString(),db.currentOrderSessionProjectionDao().get()!!.payload)
            try{repo.execute(command);fail("duplicate add accepted")}catch(_:IllegalStateException){}
        }finally{db.close()}
    }
    @Test fun mergesExistingHistoricalPriceAndRetainsLegacyStringStockPreflight()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val session=seed().put("extension",JSONObject().put("keep",true)).put("items",JSONArray("""[{"cartLineId":"old","productId":"p","name":"Историческое название","price":7,"basePrice":6,"manualPrice":true,"qty":"2","selectedModifiers":[{"groupId":"g","productId":"milk","qty":0.5,"priceDelta":1}]}]"""))
            prepare(db,session.toString());val owner=owner();val repo=MPosCartAddRepository(db,owner);val v=view(owner,session);val command=commit(v,repo.execute(v).getJSONObject("model"))
            val result=repo.execute(command);val item=result.getJSONArray("items").getJSONObject(0)
            assertEquals(1,result.getJSONArray("items").length());assertEquals(3,item.getInt("qty"));assertEquals(7,item.getInt("price"));assertEquals("old",item.getString("cartLineId"))
            assertTrue(JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload).getJSONObject("extension").getBoolean("keep"))
            MPosRecoveryStorage(db).write("currentOrderSession",session.toString());MPosCatalogStorage(db).write(JSONArray(catalogue).also{it.getJSONObject(0).put("stock",20)}.toString())
            val fresh=repo.execute(v).getJSONObject("model");assertFalse(repo.execute(commit(v,fresh)).getBoolean("allowed")) // "2" + 1 preflights 21, then ++ would have saved 3.
            assertEquals(session.toString(),db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
        }finally{db.close()}
    }
    @Test fun manualRowsRemainSeparateAndPositiveGuardPrecedesRounding()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            prepare(db,products=JSONArray(catalogue).also{it.getJSONObject(0).put("price",0)}.toString());val owner=owner();val repo=MPosCartAddRepository(db,owner)
            var session=seed();repeat(2){val v=view(owner,session);val model=repo.execute(v).getJSONObject("model");assertTrue(model.getBoolean("manual"))
                val result=repo.execute(commit(v,model).put("manualInput","0,004"));assertTrue(result.getBoolean("allowed"));session=JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)}
            val items=session.getJSONArray("items");assertEquals(2,items.length());assertNotEquals(items.getJSONObject(0).getString("cartLineId"),items.getJSONObject(1).getString("cartLineId"))
            assertEquals(0.0,items.getJSONObject(0).getDouble("basePrice"),0.0);assertEquals(1.0,items.getJSONObject(0).getDouble("price"),0.0)
            val v=view(owner,session);val bad=repo.execute(commit(v,repo.execute(v).getJSONObject("model")).put("manualInput","0"));assertFalse(bad.getBoolean("allowed"));assertEquals(2,JSONObject(db.legacyStorageShadowDao().get("currentOrderSession")!!.payload).getJSONArray("items").length())
        }finally{db.close()}
    }
    @Test fun validationStaleCatalogueRecoveryAndSqliteFailureNeverPersistPartialAdd()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val session=seed();prepare(db,session.toString());val owner=owner();val repo=MPosCartAddRepository(db,owner);val v=view(owner,session);val command=commit(v,repo.execute(v).getJSONObject("model"))
            assertFalse(repo.execute(JSONObject(command.toString()).put("selections",JSONArray("[[]]"))).getBoolean("allowed"));assertEquals(session.toString(),db.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
            MPosCatalogStorage(db).write(JSONArray(catalogue).also{it.getJSONObject(0).put("price",8)}.toString())
            try{repo.execute(command);fail("stale catalogue accepted")}catch(_:IllegalStateException){}
            MPosCatalogStorage(db).write(catalogue);MPosRecoveryStorage(db).write("criticalStorageJournal","{}")
            try{repo.execute(command);fail("recovery bypassed")}catch(_:IllegalStateException){}
            MPosRecoveryStorage(db).remove("criticalStorageJournal")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_add_projection BEFORE INSERT ON current_order_session_projection BEGIN SELECT RAISE(ABORT, 'fixture'); END")
            try{repo.execute(command);fail("SQLite failure ignored")}catch(_:android.database.sqlite.SQLiteException){}
            assertEquals(session.toString(),db.legacyStorageShadowDao().get("currentOrderSession")!!.payload);assertEquals(session.toString(),db.currentOrderSessionProjectionDao().get()!!.payload)
        }finally{db.close()}
    }
}
