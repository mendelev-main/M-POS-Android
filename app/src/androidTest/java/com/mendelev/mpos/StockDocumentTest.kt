package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StockDocumentTest {
    @Test fun realSaleRefundReceivingInventoryDocumentsUseHistoricalSnapshotsWithoutWrites(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            stockDocumentFixture(db);val p=db.catalog().product("p-stock-doc")!!
            CatalogRepository(db).saveProduct(ProductInput(p.id,p.revision,"Новое имя",p.categoryId,"99","9","0.0004",null,false,"rename",unit=p.unit))
            val before=db.workspace().documents();val balance=db.catalog().balance(p.id);val movements=db.catalog().stockHistory(p.id)
            val reader=StockDocumentReader(db)
            val docs=movements.filter {it.reason!=null}.associate {it.source to reader.read(it.id)}
            assertEquals(setOf("sale","refund","receiving","inventory"),docs.keys)
            assertTrue(docs.getValue("sale").title.contains("Чек"));assertTrue(docs.getValue("refund").title.contains("Возврат"))
            assertTrue(docs.getValue("receiving").title.contains("TTN-stock-doc"));assertTrue(docs.getValue("inventory").title.contains("Завершена"))
            docs.values.forEach {assertTrue(it.lines.any {line->line.contains("Молоко stock-doc")});assertFalse(it.lines.any {line->line.contains("Новое имя")})}
            assertTrue(docs.getValue("inventory").lines.any {it.contains("0.0004")})
            assertEquals(before,db.workspace().documents());assertEquals(balance,db.catalog().balance(p.id));assertEquals(movements,db.catalog().stockHistory(p.id))
            assertTrue(runCatching {reader.read(movements.single {it.source=="opening"}.id)}.isFailure)
        } finally {db.close()}
    }
    @Test fun forgedLinkAndCorruptDocumentRefuseRatherThanOpeningAnotherDocument(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            stockDocumentFixture(db);val reader=StockDocumentReader(db);val sale=db.catalog().stockHistory("p-stock-doc").single {it.source=="sale"}
            db.openHelper.writableDatabase.execSQL("UPDATE stock_movements SET delta='-2' WHERE id=?",arrayOf(sale.id))
            assertTrue(runCatching {reader.read(sale.id)}.isFailure)
            val receiving=db.catalog().stockHistory("p-stock-doc").single {it.source=="receiving"}
            val doc=db.workspace().document(PostedReceivingCodec.KEY)!!;db.workspace().put(doc.copy(payload="broken"))
            assertTrue(runCatching {reader.read(receiving.id)}.isFailure);assertEquals("broken",db.workspace().document(PostedReceivingCodec.KEY)!!.payload)
        } finally {db.close()}
    }
    @Test fun missingDocumentRefusesExplicitly(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {stockDocumentFixture(db);val m=db.catalog().stockHistory("p-stock-doc").single {it.source=="receiving"};db.workspace().delete(PostedReceivingCodec.KEY);assertTrue(runCatching {StockDocumentReader(db).read(m.id)}.isFailure)} finally {db.close()}
    }
}
