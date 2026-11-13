package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.domain.sale.PaymentInput
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.CartAction
import com.mendelev.mpos.telegram.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.math.BigInteger
import java.io.File
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

class OwnerPanoramaHolidayTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun clock(value: String)=Clock.fixed(Instant.parse(value),ZoneOffset.UTC)
    @Test fun decemberHolidayReportsOnJanuaryOpeningWithNoAdminAndCorrectCalendarBounds(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveProduct(ProductInput("p",null,"Кофе",null,"2","1","10",null,true,"p"))
            val work=WorkspaceRepository(db);work.initialize()
            val shifts=ShiftRepository(db,clock("2026-12-30T08:00:00Z"));shifts.initializeCashierFixture()
            val initial=shifts.snapshot();val id=shifts.open("open",initial.revision,null,initial.ownerId)
            work.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
            val w=work.snapshot();val active=shifts.snapshot()
            val draft=CheckoutRepository(db).save(0,CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,id,active.revision,active.ownerId,parts=listOf(PaymentInput("card",200))))
            val receipt=SaleRepository(db,clock("2026-12-30T09:00:00Z")).commit(draft);SaleRepository(db).acknowledge(receipt)
            val closing=shifts.snapshot()
            ShiftRepository(db,clock("2026-12-30T18:00:00Z")).close("close",id,closing.revision,ShiftRules.token(closing),0,closing.ownerId)
            try {db.ownerPanorama(id,OwnerStatsKind.MONTH);fail("Not last day") } catch(_:IllegalArgumentException) {}
            val closed=shifts.snapshot()
            val january=ShiftRepository(db,clock("2027-01-01T09:00:00Z")).open("open-jan",closed.revision,id,closed.ownerId)
            val month=OwnerReportSchedule.period(OwnerStatsKind.MONTH,LocalDate.parse("2026-12-01"),ZoneOffset.UTC)
            val quarter=OwnerReportSchedule.period(OwnerStatsKind.QUARTER,LocalDate.parse("2026-12-01"),ZoneOffset.UTC)
            Administration.session.logout()
            val before=tables(db)
            val m=db.ownerPanorama(january,OwnerStatsKind.MONTH,month)
            val q=db.ownerPanorama(january,OwnerStatsKind.QUARTER,quarter)
            assertFalse(Administration.session.state.value.active)
            assertEquals(LocalDate.parse("2026-12-01"),m.report.period.from);assertEquals(LocalDate.parse("2026-12-31"),m.report.period.through)
            assertEquals(LocalDate.parse("2026-10-01"),q.report.period.from);assertEquals(LocalDate.parse("2026-12-31"),q.report.period.through)
            assertEquals(200.toBigInteger(),m.report.net.total);assertEquals(100.toBigInteger(),ReportAnalytics.grossProfit(m.report))
            assertEquals(m.report.net.total,q.report.net.total);assertEquals(1L,q.report.saleCount)
            assertEquals(before,tables(db))
            try {db.ownerPanorama(january,OwnerStatsKind.SHIFT);fail("Open shift")} catch(_:IllegalStateException) {}
            try {db.ownerPanorama(id,OwnerStatsKind.MONTH,month);fail("Not completed yet at that trigger")} catch(_:IllegalArgumentException) {}
            val message=OwnerPanoramaDelivery.message(context,q)
            assertTrue(message.album.isEmpty());assertEquals("application/pdf",message.attachment!!.mediaType)
            assertTrue(message.attachment!!.name.contains("2026-Q4"));assertTrue(message.text.contains("IV квартал 2026"))
            assertTrue(message.text.contains("01.10.2026 — 31.12.2026"))
            val pdf=File(context.cacheDir,"owner-quarter-fixture.pdf")
            try {
                pdf.writeBytes(message.attachment!!.bytes)
                val reader=PdfRenderer(ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY))
                try {assertEquals(1,reader.pageCount);reader.openPage(0).use {p->
                    val text=p.textContents.joinToString("\n") {it.text}
                    assertTrue(text.contains("IV квартал 2026"));assertTrue(text.contains("Движение товаров"));assertTrue(text.contains("Кофе"))
                }} finally {reader.close()}
            } finally {pdf.delete()}
            assertEquals(before,tables(db))
        } finally {Administration.session.login("fixture-admin".toCharArray());db.close()}
    }
    private fun tables(db: CatalogDatabase): Map<String,List<List<String?>>> {
        val sql=db.openHelper.readableDatabase
        val names=sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use {c->buildList {while(c.moveToNext()) add(c.getString(0))}}
        return names.associateWith {name->sql.query("SELECT * FROM \"$name\" ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
    }
}
