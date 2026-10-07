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
class MPosAnalyticsEngineTest {
    private fun fixtures()=JSONArray(File("../tests/fixtures/analytics-reports.json").readText())
    private fun fixture(label:String):JSONObject {val rows=fixtures();return (0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("label")==label}}
    @Test fun salesPaymentsRefundsCostNamedGroupsAndPeriodsMatchActualSource(){
        val rows=fixtures();for(i in 0 until rows.length()){val f=rows.getJSONObject(i);val state=f.getJSONObject("state");val data=MPosAnalyticsEngine.calculate(f.getJSONObject("request"),state.getJSONArray("orders"),state.getJSONArray("products"),state.getJSONArray("shifts"))
            assertTrue("${f.getString("label")}\nexpected=${f.getJSONObject("expected")}\nactual=$data",MPosSupplyParity.same(f.getJSONObject("expected"),data))
        }
    }
    @Test fun repositoryAggregatesPersistedSourcesWithoutExportingFullReceiptsOrMutatingData()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val f=fixture("payment-arrays");val state=f.getJSONObject("state");MPosOrderStorage(db).initialize(state.getJSONArray("orders").toString());MPosCatalogStorage(db).initialize(state.getJSONArray("products").toString());MPosShiftStorage(db).initialize(state.getJSONArray("shifts").toString())
            val beforeOrders=MPosOrderStorage(db).read().getString("payload");val beforeProducts=MPosCatalogStorage(db).read().getString("payload");val beforeShifts=MPosShiftStorage(db).read().getString("payload")
            val request=JSONObject(f.getJSONObject("request").toString()).put("orders",JSONArray()).put("products",JSONArray()).put("shifts",JSONArray());val result=MPosAnalyticsRepository(db).read(request.toString())
            assertTrue(result.getBoolean("authoritative"));assertTrue(MPosSupplyParity.same(f.getJSONObject("expected"),result.getJSONObject("data")));assertFalse(result.getJSONObject("data").has("orders"))
            assertEquals(beforeOrders,MPosOrderStorage(db).read().getString("payload"));assertEquals(beforeProducts,MPosCatalogStorage(db).read().getString("payload"));assertEquals(beforeShifts,MPosShiftStorage(db).read().getString("payload"))
        }finally{db.close()}
    }
    @Test fun laterReturnRemovesOriginalSaleFromEarlierPeriodAsReviewed()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val f=fixture("bounds");val state=f.getJSONObject("state");MPosOrderStorage(db).initialize(state.getJSONArray("orders").toString());MPosCatalogStorage(db).initialize(state.getJSONArray("products").toString());MPosShiftStorage(db).initialize(state.getJSONArray("shifts").toString());val repository=MPosAnalyticsRepository(db);val before=repository.read(f.getJSONObject("request").toString()).getJSONObject("data")
            val orders=state.getJSONArray("orders");orders.getJSONObject(1).put("returnedAt",f.getJSONObject("request").getLong("now"));MPosOrderStorage(db).write(orders.toString());val after=repository.read(f.getJSONObject("request").toString()).getJSONObject("data")
            assertEquals(before.getInt("orderCount")-1,after.getInt("orderCount"));assertEquals(before.getDouble("revenue")-100,after.getDouble("revenue"),0.0)
        }finally{db.close()}
    }
    @Test fun uninitializedSourcesFailInsteadOfReturningZeroRevenue()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{try{MPosAnalyticsRepository(db).read(fixture("empty").getJSONObject("request").toString());fail("missing authority accepted")}catch(_:IllegalStateException){}}
        finally{db.close()}
    }
    @Test fun legacyAndTypedFieldsRemainExactAfterRoomImportAndProjection()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{MPosOrderStorage(db).initialize("[]");MPosCatalogStorage(db).initialize("[]");MPosShiftStorage(db).initialize("[]");val rows=fixtures()
            for(i in 0 until rows.length()){val f=rows.getJSONObject(i);val state=f.getJSONObject("state");MPosOrderStorage(db).write(state.getJSONArray("orders").toString());MPosCatalogStorage(db).write(state.getJSONArray("products").toString());MPosShiftStorage(db).write(state.getJSONArray("shifts").toString())
                val data=MPosAnalyticsRepository(db).read(f.getJSONObject("request").toString()).getJSONObject("data");assertTrue(f.getString("label"),MPosSupplyParity.same(f.getJSONObject("expected"),data))
            }
        }finally{db.close()}
    }

}
