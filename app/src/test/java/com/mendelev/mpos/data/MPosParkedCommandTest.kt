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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosParkedCommandTest {
    private fun record() = JSONObject("""{"id":"p","items":[{"productId":"x","qty":2,"price":3,"comment":"no sugar"}],"customer":{"id":"c","extra":7},"orderLabel":"Table 4","comment":"WEB","source":"web","webOrderId":"w","webOrderStatus":"accepted","orderType":"Доставка","deliveryFee":0,"deliveryTariffSelected":true,"kitchenPrinted":true,"printedItems":[]} """)
    private fun command(op: String, before: JSONArray, after: JSONArray, session: JSONObject? = null): JSONObject = JSONObject().put("version",1).put("operation",op).put("expected",before).put("id","p").put("cartEmpty",true).put("writes",JSONObject().put("parked",after).apply { if(session!=null)put("currentOrderSession",session) })
    private suspend fun init(db: MPosDatabase) {
        MPosParkedOrderStorage(db).initialize("[]")
        MPosRecoveryStorage(db).initialize("currentOrderSession","{\"items\":[]}")
        MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
    }
    @Test fun holdResumeAndDeletePreserveFullDocumentsAndRequireMatchingList() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            init(db);val engine=MPosParkedCommand(db);val added=JSONArray().put(record())
            assertTrue(engine.commit(command("park-order",JSONArray(),added,JSONObject("{\"items\":[]}")).toString()).getBoolean("ok"))
            try { engine.commit(command("delete-parked",JSONArray(),JSONArray()).toString());fail("stale accepted") } catch (_: IllegalStateException) { }
            val session=JSONObject("""{"items":[],"customer":{"name":"","phone":"","address":"","id":"c","extra":7},"orderLabel":"Table 4","orderComment":"WEB","source":"web","webOrderId":"w","webOrderStatus":"accepted","orderType":"Доставка","deliveryFee":0,"deliveryTariffSelected":true,"kitchenPrinted":true,"printedItems":[],"updatedAt":10}""").put("items",record().getJSONArray("items"))
            engine.commit(command("resume-parked",added,JSONArray(),session).toString())
            assertEquals("w",JSONObject(MPosRecoveryStorage(db).read("currentOrderSession").getString("payload")).getString("webOrderId"))
            assertEquals(0,JSONArray(MPosParkedOrderStorage(db).read().getString("payload")).length())
            engine.commit(command("park-order",JSONArray(),added,JSONObject("{\"items\":[]}")).toString())
            engine.commit(command("delete-parked",added,JSONArray()).toString())
            assertEquals(0,JSONArray(MPosParkedOrderStorage(db).read().getString("payload")).length())
        } finally { db.close() }
    }
    @Test fun invalidTransitionAndPendingJournalNeverModifyStoredList() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            init(db);val engine=MPosParkedCommand(db);val added=JSONArray().put(record());engine.commit(command("park-order",JSONArray(),added,JSONObject("{\"items\":[]}")).toString())
            val invalid=JSONArray().put(JSONObject(record().toString()).put("orderLabel","changed").put("kitchenPrinted",true))
            try {engine.commit(command("park-order-print-state",added,invalid).toString());fail("changed metadata accepted")}catch(_:IllegalStateException){}
            MPosRecoveryStorage(db).write("criticalStorageJournal","{\"type\":\"pending\"}")
            try {engine.commit(command("delete-parked",added,JSONArray()).toString());fail("journal ignored")}catch(_:IllegalStateException){}
            assertEquals("Table 4",JSONArray(MPosParkedOrderStorage(db).read().getString("payload")).getJSONObject(0).getString("orderLabel"))
        }finally{db.close()}
    }
    @Test fun secondDocumentWriteFailureRollsBackParkedRawAndProjection() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            init(db)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_session BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'currentOrderSession' BEGIN SELECT RAISE(ABORT, 'test session failure'); END")
            try {
                MPosParkedCommand(db).commit(command("park-order",JSONArray(),JSONArray().put(record()),JSONObject("{\"items\":[]}")).toString())
                fail("write failure ignored")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals("[]",MPosParkedOrderStorage(db).read().getString("payload"))
            assertEquals(0,db.parkedOrderProjectionDao().orderCount())
            assertEquals(0,JSONObject(MPosRecoveryStorage(db).read("currentOrderSession").getString("payload")).getJSONArray("items").length())
        }finally{db.close()}
    }

}
