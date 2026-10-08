package com.mendelev.mpos.workspace

import androidx.room.Room
import com.mendelev.mpos.data.*
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
class MPosWorkspaceReadRepositoryTest {
    private fun order()=JSONObject("""{"currency":"BYN","items":[],"discounts":[],"loyaltyPrograms":[],"loyaltyRedemptions":{},"orderType":"На месте","deliveryFee":0,"deliveryTariffSelected":false,"deliveryRates":[],"customer":{},"orderLabel":"","orderComment":""}""")
    @Test fun freshOwnedRoomCatalogueOverridesCallerDataAndReadDoesNotWrite()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val owner=MPosWorkspaceNavigationOwner();val expected=owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos")).getJSONObject("snapshot")
            val input=JSONObject().put("version",1).put("expected",expected).put("order",order()).put("columns",5)
            val repo=MPosWorkspaceReadRepository(db,owner)
            try{repo.read(input);fail("unowned documents accepted")}catch(_:IllegalStateException){}
            assertEquals(0,db.legacyStorageShadowDao().count())
            MPosCatalogStorage(db).initialize("""[{"id":"p","name":"Room product","type":"simple","price":1,"stock":2,"stockUnit":"piece"}]""")
            MPosWorkspaceStorage(db).initialize("layout","""{"tiles":[{"type":"product","id":"p","col":0,"row":0}]}""")
            MPosWorkspaceStorage(db).initialize("posNavigation","""{"version":1,"categories":[]}""")
            MPosParkedOrderStorage(db).initialize("[]");MPosShiftStorage(db).initialize("[]");MPosEmployeeStorage(db).initialize("[]")
            MPosRecoveryStorage(db).initialize("criticalStorageJournal",null)
            val keys=listOf("products","layout","posNavigation","parked","shifts","employees","criticalStorageJournal")
            val before=db.legacyStorageShadowDao().getAll(keys)
            val model=repo.read(input.put("products","caller forged")).getJSONObject("model")
            assertEquals("Room product",model.getJSONArray("tiles").getJSONObject(0).getString("name"));assertFalse(model.getBoolean("blocked"))
            assertEquals(before,db.legacyStorageShadowDao().getAll(keys))
            MPosRecoveryStorage(db).write("criticalStorageJournal","{}")
            assertTrue(repo.read(input).getJSONObject("model").getBoolean("blocked"))
            MPosCatalogStorage(db).write("""[{"id":"p","name":"Imported product","type":"simple","price":2,"noStockTracking":true}]""")
            assertEquals("Imported product",repo.read(input).getJSONObject("model").getJSONArray("tiles").getJSONObject(0).getString("name"))
            owner.handle(JSONObject().put("version",1).put("operation","selectTab").put("tab","receipts"))
            try{repo.read(input);fail("old view accepted")}catch(_:IllegalStateException){}
        }finally{db.close()}
    }
}
