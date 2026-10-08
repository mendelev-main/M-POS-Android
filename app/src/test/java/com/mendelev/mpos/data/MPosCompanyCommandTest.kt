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
class MPosCompanyCommandTest {
    private fun fields()=JSONObject().put("establishmentName", "").put("legalName", "").put("address", "").put("deliveryAddress", "")
    private fun command(expected:JSONObject=fields())=JSONObject().put("version",1).put("shiftId","s").put("expected",expected).put("fields",fields().put("legalName","  Cafe  "))
    private fun runCase(block:suspend (MPosDatabase,MPosWorkspaceStorage,MPosCompanyCommand)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            MPosEmployeeStorage(db).initialize("""[{"id":"a","role":"admin"}]""")
            MPosShiftStorage(db).initialize("""[{"id":"s","status":"open","employeeId":"a"}]""")
            MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            val storage=MPosWorkspaceStorage(db);storage.initialize("company",null)
            block(db,storage,MPosCompanyCommand(db))
        }finally{db.close()}
    }
    private suspend fun denied(engine:MPosCompanyCommand,input:JSONObject){try{engine.commit(input.toString());fail("unexpected save")}catch(_:IllegalStateException){}}
    @Test fun trimAndAcknowledgeDurableCompanyWithReviewedFourFieldReplacement()=runCase {_,storage,engine->
        val expected=fields().put("extension",JSONObject().put("v",1));storage.write("company",expected.toString())
        val result=engine.commit(command(expected).toString())
        assertTrue(result.getBoolean("authoritative"))
        val saved=JSONObject(storage.read("company").getString("payload"))
        assertEquals("Cafe",saved.getString("legalName"));assertEquals(4,saved.length())
        assertEquals(saved.toString(),result.getJSONObject("company").toString())
    }
    @Test fun liveDemotionAndClosedOrReplacedShiftRejectWithoutChangingDocument()=runCase {db,storage,engine->
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"employee"}]""");denied(engine,command())
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"admin"}]""")
        MPosShiftStorage(db).write("[]");denied(engine,command())
        MPosShiftStorage(db).write("""[{"id":"different","status":"open","employeeId":"a"}]""");denied(engine,command())
        assertFalse(storage.read("company").getBoolean("found"))
    }
    @Test fun staleFormCannotOverwriteNewerDocument()=runCase {_,storage,engine->
        val next=fields().put("legalName","Newer");storage.write("company",next.toString())
        denied(engine,command());assertEquals(next.toString(),storage.read("company").getString("payload"))
    }
    @Test fun absentAndWhitespaceNullUseSameDefaultsAndImportRetainsRawExtensions()=runCase {_,storage,engine->
        engine.commit(command().toString());storage.write("company"," null ")
        assertEquals(" null ",storage.read("company").getString("payload"));engine.commit(command().toString())
        val raw=" {\"address\":\"Imported\",\"extension\":false} "
        storage.write("company",raw);assertEquals(raw,storage.read("company").getString("payload"))
        engine.commit(command(fields().put("address","Imported").put("extension",false)).toString())
    }
    @Test fun failedRoomWriteRollsBackExistingCompany()=runCase {db,storage,engine->
        val original=fields().toString();storage.write("company",original)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_company BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'company' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try{engine.commit(command().toString());fail("failed write accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(original,storage.read("company").getString("payload"))
    }
    @Test fun unexpectedFieldDoesNotPersist()=runCase {_,storage,engine->
        val input=command();input.getJSONObject("fields").put("extra",true)
        try{engine.commit(input.toString());fail("extra field accepted")}catch(_:IllegalArgumentException){}
        assertFalse(storage.read("company").getBoolean("found"))
    }
}
