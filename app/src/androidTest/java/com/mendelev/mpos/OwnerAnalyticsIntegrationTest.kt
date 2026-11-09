package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.report.ReportDetailLine
import com.mendelev.mpos.domain.sale.PaymentInput
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.CartAction
import com.mendelev.mpos.telegram.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.io.File

class OwnerAnalyticsIntegrationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun clock(hour: String)=Clock.fixed(Instant.parse("2026-11-30T$hour:00:00Z"),ZoneOffset.UTC)
    @Test fun monthlySnapshotsStopAtEachClosingAndReadHistoricalCatalogueWithoutWrites(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize();catalog.saveCategory("c",null,"Историческая категория","c-create")
            catalog.saveProduct(ProductInput("p",null,"Старое имя","c","2","1","10",null,true,"p-create"))
            val work=WorkspaceRepository(db);work.initialize()
            val shifts=ShiftRepository(db,clock("08"));shifts.initializeCashierFixture()
            suspend fun saleAndClose(tag: String,openHour: String,saleHour: String,closeHour: String,productRevision: Long,minor: Long): String {
                val before=shifts.snapshot()
                val id=ShiftRepository(db,clock(openHour)).open("open-$tag",before.revision,before.last?.id,before.ownerId)
                work.cart(CartAction.Add("p",productRevision,emptySet(),null,"line-$tag"),"add-$tag",work.snapshot().cart.sessionId)
                val active=shifts.snapshot();val w=work.snapshot()
                val draft=CheckoutRepository(db).save(CheckoutRepository(db).revision(),CheckoutDraft("sale-$tag",w.cart.sessionId,w.cartRevision,id,active.revision,active.active!!.actorId,parts=listOf(PaymentInput("card",minor))))
                val receipt=SaleRepository(db,clock(saleHour)).commit(draft);SaleRepository(db).acknowledge(receipt)
                val after=shifts.snapshot()
                ShiftRepository(db,clock(closeHour)).close("close-$tag",id,after.revision,ShiftRules.token(after),0,after.active!!.actorId)
                return id
            }
            val first=saleAndClose("first","08","09","15",1,200)
            val p=db.catalog().product("p")!!;db.catalog().updateProduct(p.copy(name="Новое имя",price="4",revision=2))
            val c=db.catalog().categorySnapshot().single();db.catalog().updateCategory(c.copy(name="Новая категория",revision=2))
            val second=saleAndClose("second","16","17","23",2,400)
            val before=shifts.snapshot();val balance=db.catalog().balance("p")
            val oldReport=db.ownerAnalytics(first,true);val newReport=db.ownerAnalytics(second,true)
            assertEquals(1,oldReport.shift!!.number);assertEquals(2,newReport.shift!!.number)
            assertEquals(200.toBigInteger(),oldReport.current.sales);assertEquals(1,oldReport.current.checks)
            assertEquals(600.toBigInteger(),newReport.current.sales);assertEquals(2,newReport.current.checks)
            assertEquals("Старое имя",oldReport.current.products.single().name)
            assertEquals("Историческая категория",oldReport.current.categories.single().name)
            assertTrue(newReport.current.products.any {it.name=="Новое имя"})
            assertEquals(100.toBigInteger(),oldReport.current.profit)
            assertEquals(before,shifts.snapshot());assertEquals(balance,db.catalog().balance("p"));assertEquals(2,db.sales().history().size)
        } finally {db.close()}
    }
    @Test fun rendersReferenceColorsAndPaginatesWithoutDroppingProductsOrCategories() {
        val sales=(1..60).map {i->OwnerSale("r$i","shift",i*3600000L,200,0,listOf(ReportDetailLine("l$i","p$i","Товар $i · длинное историческое название","c$i","Категория $i","piece",BigDecimal.ONE,200,BigInteger.valueOf(100)) ))}
        val current=OwnerAnalyticsRules.build(sales,emptyList(),sales.associateBy {it.id},ZoneOffset.UTC)
        val report=OwnerAnalyticsReport("Коммерческая аналитика","Смена · тестовые данные",0,current,null,"предыдущая смена")
        val pages=OwnerAnalyticsImage.pages(report)
        assertEquals(60,pages.filter {it.kind!=OwnerPageKind.CATEGORIES}.sumOf {it.rows.size})
        assertEquals(52,pages.filter {it.kind==OwnerPageKind.CATEGORIES}.sumOf {it.rows.size})
        val folder=File(context.filesDir,"qa").apply {mkdirs()}
        pages.indices.forEach {index->
            val bytes=OwnerAnalyticsImage.render(report,index)
            val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            try {assertEquals(1080,bitmap.width);assertTrue(bitmap.width+bitmap.height<=10000);assertEquals(android.graphics.Color.rgb(13,22,38),bitmap.getPixel(5,5))} finally {bitmap.recycle()}
            File(folder,"owner-analytics-${index+1}.png").writeBytes(bytes)
        }
        val pdf=OwnerAnalyticsImage.pdf(report)
        assertEquals("%PDF",pdf.take(4).toByteArray().toString(Charsets.US_ASCII))
        File(folder,"owner-analytics.pdf").writeBytes(pdf)
    }
}
