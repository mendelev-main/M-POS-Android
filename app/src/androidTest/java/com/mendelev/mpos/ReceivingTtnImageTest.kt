package com.mendelev.mpos

import android.graphics.BitmapFactory
import com.mendelev.mpos.telegram.*
import org.junit.Assert.*
import org.junit.Test

class ReceivingTtnImageTest {
    @Test fun passportInvoiceFitsTwelveOrdinaryRowsOnOnePage() {
        val report=ReceivingTtn("2434","13.11.2026","ООО «Поставщик»","14.11.2026 · 01:26","Иванова А.А.",
            List(12) {TtnRow("Товар ${it+1}","1 уп. × 2 кг","2 кг","10,00 / кг","20,00")},"240,00","")
        val image=ReceivingTtnImage(report,androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.getFont(com.mendelev.mpos.R.font.manrope));assertEquals(1,image.pages)
        val bytes=image.render(0)
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir,"ttn-compact-preview.png").writeBytes(bytes)
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        try {assertTrue(bitmap.height<=1600);assertEquals(android.graphics.Color.rgb(34,38,49),bitmap.getPixel(0,0));assertEquals(android.graphics.Color.rgb(27,31,39),bitmap.getPixel(1100,150))} finally {bitmap.recycle()}
    }
    @Test fun longInvoiceRendersEveryPageAsBoundedPng() {
        val report=ReceivingTtn("000124","14.11.2026","Поставщик с длинным названием ".repeat(5),"14.11.2026 · 12:45","Иванова А.А.",
            List(70) {TtnRow("Товар $it с длинным названием ".repeat(3),"2 кор. × 6 л","12 л","2,50 / л","30,00")},"2100,00","")
        val image=ReceivingTtnImage(report,androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.getFont(com.mendelev.mpos.R.font.manrope))
        assertTrue(image.pages>1)
        repeat(image.pages) {page->
            val bytes=image.render(page);assertTrue(bytes.size in 1..10_000_000)
            val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            try {assertEquals(1200,bitmap.width);assertTrue(bitmap.height in 320..1600)} finally {bitmap.recycle()}
        }
    }
}
