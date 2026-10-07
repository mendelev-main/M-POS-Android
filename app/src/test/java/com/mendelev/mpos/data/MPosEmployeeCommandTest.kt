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
class MPosEmployeeCommandTest {
    private fun row(id:String,role:String="employee")=JSONObject().put("id",id).put("name","Name").put("phone","").put("role",role)
    private fun command(op:String,id:String,before:JSONArray,next:JSONArray)=JSONObject().put("version",1).put("operation",op).put("id",id).put("expected",before).put("next",next).put("shiftId","s").apply { if(op=="delete")put("authorization","reviewed-handler") }
    private fun runCase(block:suspend (MPosDatabase,MPosEmployeeStorage,MPosEmployeeCommand)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try { val storage=MPosEmployeeStorage(db);storage.initialize("[]");MPosShiftStorage(db).initialize("[]");block(db,storage,MPosEmployeeCommand(db)) }finally{db.close()}
    }
    private suspend fun rejected(engine:MPosEmployeeCommand,c:JSONObject){try{engine.commit(c.toString());fail("invalid command accepted")}catch(_:IllegalStateException){}}
    @Test fun createEditPreserveExtensionsAndProjection()=runCase {db,storage,engine->
        val created=JSONArray().put(row("a"));engine.commit(command("save","",JSONArray(),created).toString())
        created.getJSONObject(0).put("extension",JSONObject().put("v",7));storage.write(created.toString())
        val edited=JSONArray(created.toString());edited.getJSONObject(0).put("name","New name").put("phone","+123")
        engine.commit(command("save","a",created,edited).toString())
        assertEquals(7,JSONArray(storage.read().getString("payload")).getJSONObject(0).getJSONObject("extension").getInt("v"))
        assertEquals("New name",db.employeeProjectionDao().all().single().name)
    }
    @Test fun staleAndInjectedFieldsDoNotWrite()=runCase {_,storage,engine->
        val before=JSONArray().put(row("a"));storage.write(before.toString())
        rejected(engine,command("save","",JSONArray(),JSONArray().put(row("b"))))
        val next=JSONArray(before.toString());next.getJSONObject(0).put("extra",true)
        rejected(engine,command("save","a",before,next));assertEquals(before.toString(),storage.read().getString("payload"))
    }
    @Test fun roleChangeRequiresReviewedGateButLastAdminDemotionIsAllowed()=runCase {_,storage,engine->
        val before=JSONArray().put(row("a","admin"));storage.write(before.toString());val next=JSONArray().put(row("a"))
        rejected(engine,command("save","a",before,next))
        engine.commit(command("save","a",before,next).put("authorization","reviewed-handler").toString())
        assertEquals("employee",JSONArray(storage.read().getString("payload")).getJSONObject(0).getString("role"))
    }
    @Test fun deleteUsesLiveShiftAndProtectsSelfAndAdmins()=runCase {db,storage,engine->
        val before=JSONArray().put(row("self")).put(row("other")).put(row("admin","admin"));storage.write(before.toString())
        rejected(engine,command("delete","other",before,JSONArray().put(row("self")).put(row("admin","admin"))))
        MPosShiftStorage(db).write("""[{"id":"s","status":"open","employeeId":"self"}]""")
        rejected(engine,command("delete","self",before,JSONArray().put(row("other")).put(row("admin","admin"))))
        rejected(engine,command("delete","admin",before,JSONArray().put(row("self")).put(row("other"))))
        val next=JSONArray().put(row("self")).put(row("admin","admin"));engine.commit(command("delete","other",before,next).toString())
        assertEquals(2,db.employeeProjectionDao().count())
    }
    @Test fun projectionFailureRollsBackDocument()=runCase {db,storage,engine->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_employee BEFORE INSERT ON employee_projection BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try {engine.commit(command("save","",JSONArray(),JSONArray().put(row("a"))).toString());fail("failure ignored")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals("[]",storage.read().getString("payload"));assertEquals(0,db.employeeProjectionDao().count())
    }
}
