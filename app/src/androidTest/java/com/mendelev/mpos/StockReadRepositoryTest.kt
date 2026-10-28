package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StockReadRepositoryTest {
    @Test fun exactBalancesHistoryAndLowThresholdReadWithoutMutations(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            standaloneInvoice(db,"read");val p=db.catalog().product("p-read")!!;val b=db.catalog().balance(p.id)!!
            CatalogRepository(db).saveProduct(ProductInput(p.id,p.revision,p.name,p.categoryId,p.price,p.cost,"0.0004",b.revision,true,"correct",unit=p.unit,minimumStock="0.0004"))
            val docs=db.workspace().documents();val balance=db.catalog().balance(p.id);val count=db.catalog().movementCount(p.id)
            val r=StockReadRepository(db);val s=r.snapshot(p.id);val row=s.rows.single {it.id==p.id}
            assertEquals("0.0004",row.quantity);assertTrue(row.low);assertEquals(2,s.movements.size)
            assertEquals(listOf("-9.9996","10").toSet(),s.movements.map {it.delta}.toSet())
            assertEquals(s.movements,r.snapshot(p.id).movements);assertEquals(docs,db.workspace().documents());assertEquals(balance,db.catalog().balance(p.id));assertEquals(count,db.catalog().movementCount(p.id))
            assertTrue(runCatching {r.snapshot("missing")}.isFailure)
        } finally {db.close()}
    }
    @Test fun noTrackingIsNotZeroOrLowAndMissingBalanceFailsExplicitly(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            standaloneInvoice(db,"untracked");val p=db.catalog().product("p-untracked")!!
            CatalogRepository(db).saveProduct(ProductInput(p.id,p.revision,p.name,p.categoryId,p.price,p.cost,"10",null,false,"no-track",unit=p.unit,noStockTracking=true,minimumStock="20"))
            val r=StockReadRepository(db);val row=r.snapshot(null).rows.single {it.id==p.id};assertNull(row.quantity);assertFalse(row.low)
            db.openHelper.writableDatabase.execSQL("DELETE FROM stock_balances WHERE productId=?",arrayOf(p.id))
            assertTrue(runCatching {r.snapshot(null)}.isFailure)
        } finally {db.close()}
    }
}
