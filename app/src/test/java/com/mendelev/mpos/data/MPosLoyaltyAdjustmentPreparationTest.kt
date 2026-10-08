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
class MPosLoyaltyAdjustmentPreparationTest {
    @Test fun preparationUsesFreshIdentityAndNativeConfigurationWithoutCredentialOrPersistentEffects()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            MPosEmployeeStorage(db).initialize("""[{"id":"a","name":"Current admin","role":"admin"}]""")
            MPosShiftStorage(db).initialize("""[{"id":"s","status":"open","employeeId":"a"}]""");MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            val network="""{"backendUrl":"https://example.invalid","deviceKey":"synthetic-key"}"""
            MPosWorkspaceStorage(db).initialize("network",network)
            val input=JSONObject().put("version",1).put("operation","adjust").put("customerId","c").put("programId","program").put("progressDelta",1).put("rewardDelta",0).put("reason","Correction")
                .put("adminEmployeeId","forged")
            val prepared=MPosLoyaltyAdjustmentPreparation(db).prepare(input.toString())
            assertEquals("a",prepared.getJSONObject("body").getString("adminEmployeeId"));assertEquals("Current admin",prepared.getJSONObject("body").getString("adminEmployeeName"))
            assertFalse(prepared.getJSONObject("body").has("adminPassword"));assertEquals(network,MPosWorkspaceStorage(db).read("network").getString("payload"))
            MPosEmployeeStorage(db).write("""[{"id":"a","role":"employee"}]""")
            try{MPosLoyaltyAdjustmentPreparation(db).prepare(input.toString());fail("demotion ignored")}catch(_:IllegalStateException){}
        }finally{db.close()}
    }
}
