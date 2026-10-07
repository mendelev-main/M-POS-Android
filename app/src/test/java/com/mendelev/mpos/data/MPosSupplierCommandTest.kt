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
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosSupplierCommandTest {
    private fun before()=JSONArray("""[{"id":"s","name":"Old","productIds":["p"],"extension":{"v":7}}]""")
    private fun command(op:String,next:JSONArray,id:String="s")=JSONObject().put("version",1).put("operation",op).put("id",id).put("expected",before()).put("next",next).put("shiftId","shift")
    private fun runCase(block:suspend(MPosDatabase,MPosSupplyStorage,MPosSupplierCommand)->Unit)=runBlocking{val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build();try{val storage=MPosSupplyStorage(db);storage.initialize("suppliers",before().toString());block(db,storage,MPosSupplierCommand(db))}finally{db.close()}}
    @Test fun saveWithoutAdminPreservesExtensionsAndExactBindings()=runCase{_,storage,engine->
        val next=before();next.getJSONObject(0).put("name","New").put("productIds",JSONArray().put("p2").put("p2"));engine.commit(command("save",next).toString());assertEquals(next.toString(),storage.read("suppliers").getString("payload"))
        val added=JSONArray(next.toString()).put(JSONObject().put("id","new").put("name","New").put("productIds",JSONArray()))
        engine.commit(command("save",added,"").put("expected",next).toString());assertEquals(2,JSONArray(storage.read("suppliers").getString("payload")).length())
    }
    @Test fun staleOrUnrelatedMutationCannotOverwriteNativeList()=runCase{_,storage,engine->
        val next=before();next.getJSONObject(0).put("name","New").put("extension",JSONObject().put("v",999))
        try{engine.commit(command("save",next).toString());fail("extension mutation accepted")}catch(_:IllegalStateException){}
        storage.write("suppliers",before().put(JSONObject().put("id","concurrent")).toString())
        try{engine.commit(command("save",before()).toString());fail("stale whole list accepted")}catch(_:IllegalStateException){}
        assertEquals(2,JSONArray(storage.read("suppliers").getString("payload")).length())
    }
    @Test fun deleteChecksPersistedAdminShiftAndPreservesHistoricalDocuments()=runCase{db,storage,engine->
        MPosEmployeeStorage(db).initialize("""[{"id":"e","role":"employee"}]""");MPosShiftStorage(db).initialize("""[{"id":"shift","employeeId":"e","status":"open"}]""")
        db.legacyStorageShadowDao().upsert(LegacyStorageShadowEntity("purchaseOrders","[{\"supplierId\":\"s\",\"supplierName\":\"Old\"}]",1))
        try{engine.commit(command("delete",JSONArray()).toString());fail("ordinary employee deleted supplier")}catch(_:IllegalStateException){}
        MPosEmployeeStorage(db).write("""[{"id":"e","role":"admin"}]""")
        MPosShiftStorage(db).write("""[{"id":"shift","employeeId":"e","status":"closed"}]""")
        try{engine.commit(command("delete",JSONArray()).toString());fail("closed shift deleted supplier")}catch(_:IllegalStateException){}
        MPosShiftStorage(db).write("""[{"id":"shift","employeeId":"e","status":"open"}]""");engine.commit(command("delete",JSONArray()).toString())
        assertEquals("[]",storage.read("suppliers").getString("payload"));assertTrue(db.legacyStorageShadowDao().get("purchaseOrders")!!.payload.contains("Old"))
    }
}
