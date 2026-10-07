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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosHallCommandTest {
    private fun fixtures()=JSONArray(File("../tests/fixtures/hall-commands.json").readText())
    private fun fixture(name:String):JSONObject {val all=fixtures();return (0 until all.length()).map{all.getJSONObject(it)}.first{it.getString("name")==name}}
    private fun runCase(name:String,block:suspend(MPosDatabase,MPosHallStorage,JSONObject)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val f=fixture(name);val storage=MPosHallStorage(db);for(key in MPosHallStorage.KEYS)storage.initialize(key,f.getJSONObject("expected").getJSONArray(key).toString());MPosRecoveryStorage(db).initialize("criticalStorageJournal","null");block(db,storage,f)}finally{db.close()}
    }
    private fun command(f:JSONObject)=JSONObject(f.getJSONObject("command").toString()).put("expected",f.getJSONObject("expected"))
    @Test fun everyReviewedFixtureMatchesNativeRulesAndRoomPersistence()=runBlocking {
        val fixtures=fixtures()
        for(i in 0 until fixtures.length()){val f=fixtures.getJSONObject(i);runCase(f.getString("name")){db,storage,case->
            if(case.has("refused")){try{MPosHallCommand(db).commit(command(case).toString());fail(case.getString("name"))}catch(e:IllegalArgumentException){assertEquals(case.getString("refused"),e.message)}catch(e:IllegalStateException){assertEquals(case.getString("refused"),e.message)}}
            else{val result=MPosHallCommand(db).commit(command(case).toString());for(key in MPosHallStorage.KEYS){assertTrue(case.getString("name"),MPosSupplyParity.same(case.getJSONObject("result").getJSONArray(key),result.getJSONArray(key)));assertTrue(MPosSupplyParity.same(result.getJSONArray(key),JSONArray(storage.read(key).getString("payload"))))}}
        }}
    }
    @Test fun tableDeletionAndBookingsRollbackTogetherOnFinalWriteFailure()=runCase("delete"){db,storage,f->
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_booking_delete BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'bookings' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try{MPosHallCommand(db).commit(command(f).toString());fail("partial delete")}catch(_:android.database.sqlite.SQLiteException){}
        for(key in MPosHallStorage.KEYS)assertTrue(MPosSupplyParity.same(f.getJSONObject("expected").getJSONArray(key),JSONArray(storage.read(key).getString("payload"))))
    }
    @Test fun staleSnapshotsAndRecoveryJournalCannotOverwriteHall()=runCase("create"){db,storage,f->
        val changed=JSONArray(f.getJSONObject("expected").getJSONArray("bookings").toString()).put(JSONObject().put("id","later"));storage.write("bookings",changed.toString())
        try{MPosHallCommand(db).commit(command(f).toString());fail("stale command")}catch(_:IllegalStateException){}
        storage.write("bookings",f.getJSONObject("expected").getJSONArray("bookings").toString());MPosRecoveryStorage(db).write("criticalStorageJournal","{}")
        try{MPosHallCommand(db).commit(command(f).toString());fail("pending recovery")}catch(_:IllegalStateException){}
        assertTrue(MPosSupplyParity.same(f.getJSONObject("expected").getJSONArray("hallTables"),JSONArray(storage.read("hallTables").getString("payload"))))
    }
    @Test fun nativeConflictUsesCurrentRoomBookingsAndDuplicateCreateIsRefused()=runCase("booking"){db,storage,f->
        val cmd=command(f);MPosHallCommand(db).commit(cmd.toString());cmd.put("expected",JSONObject().put("hallTables",JSONArray(storage.read("hallTables").getString("payload"))).put("bookings",JSONArray(storage.read("bookings").getString("payload"))))
        try{MPosHallCommand(db).commit(cmd.toString());fail("duplicate create")}catch(_:IllegalStateException){}
        cmd.put("id","another");try{MPosHallCommand(db).commit(cmd.toString());fail("overlap")}catch(e:IllegalStateException){assertEquals("На выбранное время стол уже забронирован",e.message)}
    }
    @Test fun moveClampsCoordinatesAndLeavesPaidDocumentsUntouched()=runCase("edit"){db,storage,f->
        val paid="[{\"id\":\"receipt\",\"tableId\":\"t\",\"tableName\":\"Original\",\"total\":100}]";db.legacyStorageShadowDao().upsert(LegacyStorageShadowEntity("orders",paid,0))
        val cmd=command(f).put("operation","table-move").put("x",150).put("y",-10);MPosHallCommand(db).commit(cmd.toString());val t=JSONArray(storage.read("hallTables").getString("payload")).getJSONObject(0);assertEquals(94.0,t.getDouble("x"),0.0);assertEquals(0.0,t.getDouble("y"),0.0);assertTrue(t.getJSONObject("extension").getBoolean("keep"))
        cmd.put("operation","table-delete").put("expected",JSONObject().put("hallTables",JSONArray(storage.read("hallTables").getString("payload"))).put("bookings",JSONArray(storage.read("bookings").getString("payload"))));MPosHallCommand(db).commit(cmd.toString());assertEquals(paid,db.legacyStorageShadowDao().get("orders")!!.payload)
    }
    @Test fun nullAbsentImportAndUnknownFieldsRemainCompatibleAcrossReopen()=runBlocking {
        val context=RuntimeEnvironment.getApplication();val name="hall-reopen-test";context.deleteDatabase(name)
        var db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build()
        try{var storage=MPosHallStorage(db);storage.initialize("hallTables",null);storage.initialize("bookings","null");assertFalse(storage.read("hallTables").getBoolean("found"));assertEquals("null",storage.read("bookings").getString("payload"));val raw="[{\"id\":7,\"extension\":{\"nested\":[null,false,3]}}]";storage.write("hallTables",raw);db.close();db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build();storage=MPosHallStorage(db);storage.initialize("hallTables","[]");assertEquals(raw,storage.read("hallTables").getString("payload"));storage.remove("hallTables");assertFalse(storage.read("hallTables").getBoolean("found"));assertTrue(storage.isAuthoritative("hallTables"))}finally{db.close();context.deleteDatabase(name)}
    }
}
