package com.mendelev.mpos

import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.print.*
import com.mendelev.mpos.data.local.PrinterConfiguration
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PrinterRenderingTest {
    @Test fun rasterDimensionsAndDurableUnknownResultSurviveReopen() {
        val d=PrintDocument("fixture",PrintKind.RECEIPT,0,employee="Анна",items=listOf(PrintItem("Кофе","1","2.00",200)),total=200,payments=listOf(PrintPayment("card",200)))
        listOf("58","80").forEach {width->
            val p=PrinterConfiguration(name="Тест",ip="127.0.0.1",paperWidth=width)
            val bitmap=ReceiptRenderer.render(PrintDocuments.page(d,p))
            try {assertEquals(if(width=="58")384 else 576,bitmap.width);assertTrue(bitmap.height>100)} finally {bitmap.recycle()}
            val bytes=ReceiptRenderer.encode(d,p)
            assertArrayEquals(byteArrayOf(27,64,29,118,48,0),bytes.take(6).toByteArray())
            assertTrue(bytes.size>1000)
            // Web Android ignores the legacy text flag and rasterizes both modes identically.
            assertArrayEquals(bytes,ReceiptRenderer.encode(d,p.copy(printMode="text")))
            assertArrayEquals(byteArrayOf(10,29,86,66,0),bytes.takeLast(5).toByteArray())
        }
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"print-jobs-${UUID.randomUUID()}")
        try {
            var sends=0
            val transport=PrintTransport {_,_->sends++;error("uncertain")}
            PrintDispatcher(FilePrintClaims(dir),transport).dispatch("job","127.0.0.1") {byteArrayOf(1)}
            val reopened=PrintDispatcher(FilePrintClaims(dir),transport).dispatch("job","127.0.0.1") {byteArrayOf(1)}
            assertTrue(reopened.duplicate);assertEquals(1,sends)
        } finally {dir.deleteRecursively()}
    }
}
