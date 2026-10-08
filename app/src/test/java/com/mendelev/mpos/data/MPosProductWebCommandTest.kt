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
class MPosProductWebCommandTest {
    @Test fun liveRoleAndExpectedCatalogueProtectToggleWithoutValidatingUnrelatedRecipe()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val products=JSONArray("""[{"id":"p","type":"composite","components":[],"extension":false}]""")
            MPosCatalogStorage(db).initialize(products.toString());MPosEmployeeStorage(db).initialize("""[{"id":"a","role":"employee"}]""")
            MPosShiftStorage(db).initialize("""[{"id":"s","status":"open","employeeId":"a"}]""");MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            val engine=MPosProductWebCommand(db);val c=JSONObject().put("version",1).put("id","p").put("expected",products)
            try{engine.commit(c.toString());fail("employee accepted")}catch(_:IllegalStateException){}
            MPosEmployeeStorage(db).write("""[{"id":"a","role":"admin"}]""")
            val result=engine.commit(c.toString());assertFalse(result.getBoolean("enabled"));assertFalse(result.getJSONArray("products").getJSONObject(0).getBoolean("extension"))
            try{engine.commit(c.toString());fail("stale toggle accepted")}catch(_:IllegalStateException){}
            c.put("expected",result.getJSONArray("products"));assertTrue(engine.commit(c.toString()).getBoolean("enabled"))
        }finally{db.close()}
    }
}
