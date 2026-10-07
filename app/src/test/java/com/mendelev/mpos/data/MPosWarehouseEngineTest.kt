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
class MPosWarehouseEngineTest {
    private fun fixtures()=JSONArray(File("../tests/fixtures/warehouse-reports.json").readText())
    private fun fixture(label:String):JSONObject {val rows=fixtures();return (0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("label")==label}}
    @Test fun reportPeriodsMovementsValuationsWarningsAndDocumentsMatchActualSource(){
        val fixtures=fixtures();for(i in 0 until fixtures.length()){val f=fixtures.getJSONObject(i);val state=f.getJSONObject("state");val expected=f.getJSONObject("expected");val label=f.getString("label")
            try{val model=MPosWarehouseEngine.calculate(f.getJSONObject("request"),state.getJSONArray("products"),state.getJSONArray("receivings"),state.getJSONArray("orders"));assertTrue(label,expected.getBoolean("allowed"));assertTrue("$label\nexpected=${expected.getJSONObject("report")}\nactual=$model",MPosSupplyParity.same(expected.getJSONObject("report"),model))}
            catch(e:IllegalStateException){assertFalse(label,expected.getBoolean("allowed"));assertEquals(label,expected.getString("message"),e.message)}
        }
    }
    @Test fun repositoryUsesAuthoritativeDocumentsNotClientListsAndDoesNotChangeStock()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val f=fixture("period-boundaries");val state=f.getJSONObject("state");MPosCatalogStorage(db).initialize(state.getJSONArray("products").toString());MPosSupplyStorage(db).initialize("receivings",state.getJSONArray("receivings").toString());MPosOrderStorage(db).initialize(state.getJSONArray("orders").toString())
            val beforeProducts=MPosCatalogStorage(db).read().getString("payload");val beforeOrders=MPosOrderStorage(db).read().getString("payload");val beforeReceivings=MPosSupplyStorage(db).read("receivings").getString("payload")
            val request=JSONObject(f.getJSONObject("request").toString()).put("products",JSONArray()).put("orders",JSONArray()).put("receivings",JSONArray())
            val result=MPosWarehouseRepository(db).read(request.toString());assertTrue(result.getBoolean("authoritative"));assertTrue(MPosSupplyParity.same(f.getJSONObject("expected").getJSONObject("report"),result.getJSONObject("report")))
            assertEquals(beforeProducts,MPosCatalogStorage(db).read().getString("payload"));assertEquals(beforeOrders,MPosOrderStorage(db).read().getString("payload"));assertEquals(beforeReceivings,MPosSupplyStorage(db).read("receivings").getString("payload"))
        }finally{db.close()}
    }
    @Test fun changedRoomStockAffectsEstimateWithoutPersistingHistoricalBalances()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{val f=fixture("empty");val state=f.getJSONObject("state");val products=state.getJSONArray("products");MPosCatalogStorage(db).initialize(products.toString());MPosSupplyStorage(db).initialize("receivings","[]");MPosOrderStorage(db).initialize("[]")
            products.getJSONObject(0).put("stock",7);MPosCatalogStorage(db).write(products.toString());val report=MPosWarehouseRepository(db).read(f.getJSONObject("request").toString()).getJSONObject("report");assertEquals(7.0,report.getJSONArray("rows").getJSONObject(0).getDouble("current"),0.0);assertEquals(7.0,report.getJSONArray("rows").getJSONObject(0).getDouble("start"),0.0)
            assertEquals(7.0,JSONArray(MPosCatalogStorage(db).read().getString("payload")).getJSONObject(0).getDouble("stock"),0.0)
        }finally{db.close()}
    }
    @Test fun invalidDatesOrUninitializedDocumentsCannotYieldEmptyFallbackReport()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{try{MPosWarehouseRepository(db).read(fixture("empty").getJSONObject("request").toString());fail("uninitialized report returned")}catch(_:IllegalStateException){}
            MPosCatalogStorage(db).initialize("[]");MPosSupplyStorage(db).initialize("receivings","[]");MPosOrderStorage(db).initialize("[]")
            try{MPosWarehouseRepository(db).read(fixture("bad-date").getJSONObject("request").toString());fail("invalid date accepted")}catch(e:IllegalStateException){assertEquals("Некорректная дата",e.message)}
        }finally{db.close()}
    }
}
