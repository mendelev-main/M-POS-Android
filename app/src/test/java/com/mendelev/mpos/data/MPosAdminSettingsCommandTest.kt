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
class MPosAdminSettingsCommandTest {
    private fun runCase(block:suspend (MPosDatabase,MPosWorkspaceStorage,MPosAdminSettingsCommand)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            MPosEmployeeStorage(db).initialize("""[{"id":"a","role":"admin"}]""")
            MPosShiftStorage(db).initialize("""[{"id":"s","status":"open","employeeId":"a"}]""")
            MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            val storage=MPosWorkspaceStorage(db);storage.initialize("network",null);storage.initialize("telegram",null)
            block(db,storage,MPosAdminSettingsCommand(db))
        }finally{db.close()}
    }
    private fun network(expected:JSONObject=MPosAdminSettingsCommand.defaults("network"))=JSONObject().put("version",1).put("key","network").put("expected",expected)
        .put("fields",JSONObject().put("backendUrl"," HTTPS://example.invalid/// ").put("deviceName","  Tablet  "))
    private fun telegram()=JSONObject().put("version",1).put("key","telegram").put("expected",MPosAdminSettingsCommand.defaults("telegram"))
        .put("fields",JSONObject().put("botToken","synthetic-token").put("chatId","-123").put("threadId","").put("deviceChatId","123").put("ownerChatId","")
            .put("enabled",true).put("notifyOnlineOrders",true).put("notifyShiftOpened",true).put("notifyShiftClosed",true).put("notifyMonthlyWarehouse",false))
    private suspend fun denied(engine:MPosAdminSettingsCommand,c:JSONObject){try{engine.commit(c.toString());fail("unexpected save")}catch(_:IllegalStateException){}}
    @Test fun firstNetworkSaveAdoptsReviewedStartupKeyAndTrimsUrlWithoutChangingMetadata()=runCase {_,storage,engine->
        val expected=MPosAdminSettingsCommand.defaults("network").put("deviceKey","synthetic-device-key")
        val result=engine.commit(network(expected).toString())
        val next=result.getJSONObject("settings");assertEquals("synthetic-device-key",next.getString("deviceKey"))
        assertEquals("HTTPS://example.invalid",next.getString("backendUrl"));assertEquals("Tablet",next.getString("deviceName"))
        assertEquals(next.toString(),storage.read("network").getString("payload"))
    }
    @Test fun liveDemotionOrClosedShiftRejectsSettingsWithNoDurableChange()=runCase {db,storage,engine->
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"employee"}]""");denied(engine,network());denied(engine,telegram())
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"admin"}]""");MPosShiftStorage(db).write("[]");denied(engine,network())
        assertFalse(storage.read("network").getBoolean("found"));assertFalse(storage.read("telegram").getBoolean("found"))
    }
    @Test fun nativeTelegramValidationRejectsInvalidIdsAndPersistsOnlyAfterValidation()=runCase {_,storage,engine->
        val c=telegram();c.getJSONObject("fields").put("deviceChatId","invalid");denied(engine,c)
        assertFalse(storage.read("telegram").getBoolean("found"))
        c.getJSONObject("fields").put("deviceChatId","123");engine.commit(c.toString())
        assertEquals("synthetic-token",JSONObject(storage.read("telegram").getString("payload")).getString("botToken"))
    }
    @Test fun staleSettingsAndWriteFailureCannotOverwriteExistingDocument()=runCase {db,storage,engine->
        val expected=MPosAdminSettingsCommand.defaults("network").put("deviceKey","stable").put("extension",false)
        storage.write("network",expected.toString());denied(engine,network())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_network BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'network' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try{engine.commit(network(expected).toString());fail("failed write accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(expected.toString(),storage.read("network").getString("payload"))
    }
}
