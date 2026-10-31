package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.*
import java.util.UUID

class SalesReportRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun clock(day: String)=Clock.fixed(Instant.parse(day+"T12:00:00Z"),ZoneOffset.UTC)
    private fun period(day: String,to: String=day)=ReportPeriod(LocalDate.parse(day),LocalDate.parse(to),ZoneId.of("Europe/Minsk"))
    private suspend fun seed(db: CatalogDatabase): String {
        val catalog=CatalogRepository(db);catalog.initialize()
        catalog.saveProduct(ProductInput("p",null,"Товар",null,"20","1","10",null,true,"product"))
        val work=WorkspaceRepository(db);work.initialize()
        val shifts=ShiftRepository(db,clock("2026-10-30"));shifts.initializeCashierFixture()
        val initial=shifts.snapshot();shifts.open("open",initial.revision,null,initial.ownerId)
        work.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
        val before=work.snapshot();work.cart(CartAction.SetDetails(OrderDetails(type="delivery"),before.cartRevision),"delivery",before.cart.sessionId)
        val w=work.snapshot();val s=shifts.snapshot()
        val draft=CheckoutRepository(db).save(0,CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,s.ownerId,
            delivery=DeliveryCharge("courier","Курьер",500,1),parts=listOf(PaymentInput("cash",1000,1500),PaymentInput("card",1500))))
        return SaleRepository(db,clock("2026-10-30")).commit(draft)
    }
    @Test fun realSaleRefundReopenAndLiveCatalogChangesKeepHistoricalReadOnlyTotals()=runBlocking {
        val name="report-${UUID.randomUUID()}.db";fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val id=seed(db)
            val refunds=RefundRepository(db,clock("2026-10-31"));val actor=db.catalog().config()!!.ownerId
            refunds.commit("refund",refunds.preview(id,actor))
            val product=db.catalog().product("p")!!;db.catalog().updateProduct(product.copy(name="Другое",price="999",cost="999",revision=product.revision+1))
            val before=tables(db);val repo=SalesReportRepository(db,clock("2026-11-01"))
            val yesterday=repo.snapshot(period("2026-10-30"));val today=repo.snapshot(period("2026-10-31"));val both=repo.snapshot(period("2026-10-30","2026-10-31"))
            assertEquals(BigInteger.valueOf(2500),yesterday.sales.total);assertEquals(BigInteger.valueOf(500),yesterday.sales.delivery)
            assertEquals(BigInteger.valueOf(1000),yesterday.sales.cash);assertEquals(BigInteger.valueOf(1500),yesterday.sales.card)
            assertEquals(BigInteger.valueOf(-2500),today.net.total);assertEquals(0L,today.saleCount);assertEquals(1L,today.refundCount)
            assertEquals(BigInteger.ZERO,both.net.total);assertEquals(BigInteger.valueOf(2500),both.averageSale)
            val yd=requireNotNull(yesterday.details);val td=requireNotNull(today.details)
            assertEquals(listOf("Товар"),yd.products.single().names)
            assertEquals("Без категории",yd.categories.single().names.single())
            assertEquals(BigInteger.valueOf(100),yd.saleCost)
            assertEquals(BigInteger.valueOf(1900),yd.profit)
            assertEquals(BigInteger.valueOf(-1900),td.profit)
            assertEquals(yd.employees.single().id,td.employees.single().id)
            assertEquals(BigInteger.valueOf(2500),td.employees.single().refunds)
            assertEquals(BigInteger.ZERO,requireNotNull(both.details).profit)
            assertEquals(before,tables(db));db.close();db=open()
            assertEquals(both,SalesReportRepository(db,clock("2026-11-01")).snapshot(period("2026-10-30","2026-10-31")))
            assertEquals(before,tables(db))
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun emptyReadDoesNotInitializeStorageAndLogoutRevokesCapturedAuthority()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=SalesReportRepository(db,clock("2026-11-01"));val p=period("2026-10-30")
            assertEquals(BigInteger.ZERO,repo.snapshot(p).net.total);assertNull(db.catalog().config())
            val old=AdminForms(this).permit();val before=tables(db)
            Administration.session.logout()
            try {repo.snapshot(p);fail()} catch(e: CatalogFailure) {assertEquals("access",e.kind)}
            assertTrue(Administration.session.login("fixture-admin".toCharArray()))
            try {old.write {repo.snapshot(p)};fail()} catch(e: CatalogFailure) {assertEquals("access",e.kind)}
            assertEquals(BigInteger.ZERO,repo.snapshot(p).net.total);assertEquals(before,tables(db))
        } finally {db.close()}
    }
    @Test fun corruptReceiptTotalsAndRefundHashFailWithoutWritingReplacementData()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val id=seed(db);val repo=SalesReportRepository(db,clock("2026-11-01"));val p=period("2026-10-30","2026-10-31")
            db.openHelper.writableDatabase.execSQL("UPDATE receipts SET total=2499 WHERE id=?",arrayOf(id))
            var before=tables(db)
            try {repo.snapshot(p);fail()} catch(e: CatalogFailure) {assertEquals("report",e.kind)}
            assertEquals(before,tables(db))
            db.openHelper.writableDatabase.execSQL("UPDATE receipts SET total=2500 WHERE id=?",arrayOf(id))
            val refunds=RefundRepository(db,clock("2026-10-31"));refunds.commit("refund",refunds.preview(id,db.catalog().config()!!.ownerId))
            db.openHelper.writableDatabase.execSQL("UPDATE refunds SET receiptHash='broken'")
            before=tables(db)
            try {repo.snapshot(p);fail()} catch(e: CatalogFailure) {assertEquals("report",e.kind)}
            assertEquals(before,tables(db))
        } finally {db.close()}
    }
    private fun tables(db: CatalogDatabase): Map<String,List<List<String?>>> {
        val sql=db.openHelper.readableDatabase
        val names=sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use {c->buildList {while(c.moveToNext()) add(c.getString(0))}}
        return names.associateWith {name->sql.query("SELECT * FROM \"$name\" ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
    }
}
