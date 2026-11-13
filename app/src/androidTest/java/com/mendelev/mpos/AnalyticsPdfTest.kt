package com.mendelev.mpos

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.feature.report.AnalyticsPdf
import com.mendelev.mpos.feature.report.reportQuantities
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

/** PDF/data integration only: no Activity, Compose UI navigation or real chooser/message sending. */
class AnalyticsPdfTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun report(count: Int=3): SalesReport {
        val day=LocalDate.parse("2026-11-13");val p=ReportPeriod(day.minusDays(6),day,ZoneId.of("Europe/Minsk"))
        val total=count*100L
        val base=SalesReports.build(p,p.endExclusive,listOf(ReportSale("s",p.start,total,total/2,total-total/2,0,0,"here")),emptyList())
        val lines=(0 until count).map {i->ReportDetailLine("l$i","p$i","Товар ${i.toString().padStart(4,'0')}","c","Категория","piece",BigDecimal.ONE,100,50.toBigInteger())}
        val facts=listOf(ReportDetailSale("s","a","Иванова Анна Александровна",lines))
        val detailed=base.copy(details=ReportGroups.build(base,facts))
        val consumption=listOf(ConsumptionRow("milk",listOf("Молоко"),"l",BigDecimal("1.236789123456"),BigDecimal.ZERO))
        val warehouse=WarehouseReports.build(p,listOf(WarehouseProduct("milk","Молоко","l",null,BigDecimal.ONE)),emptyList(),listOf(WarehousePurchase("milk",p.start,BigDecimal("4.56789"),"l",500)))
        return detailed.copy(trend=ReportTrends.build(detailed,facts),consumption=consumption,warehouse=warehouse)
    }
    private inline fun <T> read(file: File,block: (PdfRenderer)->T): T {
        val renderer=PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY))
        return try {block(renderer)} finally {renderer.close()}
    }
    @Test fun onePageContainsAllSectionsAndDarkLightRenderingIsNotBlank() {
        for(dark in listOf(false,true)) {
            val report=report();val file=AnalyticsPdf.newFile(context,report)
            try {
                AnalyticsPdf.write(context,file,report,AnalyticsPdf.Options(dark))
                read(file) {pdf->
                    assertEquals(1,pdf.pageCount)
                    pdf.openPage(0).use {page->
                        assertTrue(page.height>800)
                        val text=page.textContents.joinToString("\n") {it.text}
                        listOf("Учёт и аналитика","Категории","Оплата","Сотрудники","Товары","Движение товаров","Товар 0002","Молоко").forEach {assertTrue("Missing $it",text.contains(it))}
                        assertTrue(text.contains("1,24 л"));assertTrue(text.contains("4,57 л"))
                        val bitmap=Bitmap.createBitmap(640,(page.height*640L/page.width).toInt(),Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val expected=if(dark) Color.rgb(23,26,33) else Color.rgb(243,244,246)
                            assertEquals(expected,bitmap.getPixel(1,1))
                            var changed=0
                            for(y in 0 until bitmap.height step 10) for(x in 0 until bitmap.width step 10) if(bitmap.getPixel(x,y)!=expected) changed++
                            assertTrue(changed>200)
                            // Fixture images only, retained in debug cache for visual inspection, no customer data.
                            val image=File(context.cacheDir,if(dark) "analytics-pdf-dark.png" else "analytics-pdf-light.png")
                            image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                        } finally {bitmap.recycle()}
                    }
                }
                val share=AnalyticsPdf.shareIntent(context,file)
                assertEquals(Intent.ACTION_SEND,share.action);assertEquals("application/pdf",share.type)
                assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
                assertEquals(0,share.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val uri=share.clipData!!.getItemAt(0).uri
                assertEquals("${context.packageName}.reports",uri.authority)
                context.contentResolver.openInputStream(uri)!!.use {assertTrue(it.readBytes().isNotEmpty())}
            } finally {file.delete()}
        }
    }
    @Test fun veryLongReportRemainsOnePageAndLastProductIsNotDropped() {
        val report=report(500);val file=AnalyticsPdf.newFile(context,report)
        try {
            AnalyticsPdf.write(context,file,report,AnalyticsPdf.Options(true))
            read(file) {pdf->
                assertEquals(1,pdf.pageCount)
                pdf.openPage(0).use {page->
                    assertEquals(14400,page.height)
                    val text=page.textContents.joinToString("\n") {it.text}
                    assertTrue((0 until 500).all {i->text.contains("Товар ${i.toString().padStart(4,'0')}")});assertTrue(text.contains("Движение товаров"));assertTrue(text.contains("Молоко"))
                }
            }
        } finally {file.delete()}
    }
    @Test fun cancellationLeavesNoPartialDocumentAndProviderRejectsOtherCacheFiles() {
        val report=report(100);val file=AnalyticsPdf.newFile(context,report)
        try {
            for(limit in listOf(25,300)) {
                var calls=0
                assertThrows(CancellationException::class.java) {AnalyticsPdf.write(context,file,report,AnalyticsPdf.Options(true)) {if(++calls>limit) throw CancellationException("fixture")}}
                assertFalse(file.exists());assertFalse(File(file.parentFile,file.name+".partial").exists())
            }
            val outside=File(context.cacheDir,"not-shared.pdf").apply {writeText("fixture")}
            try {assertThrows(IllegalArgumentException::class.java) {AnalyticsPdf.shareIntent(context,outside)}} finally {outside.delete()}
        } finally {file.delete()}
    }
    @Test fun litersAndKgOnlyRoundForDisplay() {
        val liters=BigDecimal("1.235678912345")
        assertEquals("1,24 л",reportQuantities(mapOf("l" to liters)))
        assertEquals("1,24 кг",reportQuantities(mapOf("kg" to liters)))
        assertEquals(BigDecimal("1.235678912345"),liters)
        assertEquals("1,235678912345 мл",reportQuantities(mapOf("ml" to liters)))
    }
}
