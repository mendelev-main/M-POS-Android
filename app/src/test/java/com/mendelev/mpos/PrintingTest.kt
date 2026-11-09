package com.mendelev.mpos

import com.mendelev.mpos.print.*
import com.mendelev.mpos.data.local.PrinterConfiguration
import org.junit.Assert.*
import org.junit.Test

class PrintingTest {
    private class Claims: PrintClaims {
        val values=mutableMapOf<String,String>();var fail=false
        override fun claim(key: String): Boolean {if(fail) error("disk");if(key in values) return false;values[key]="sending";return true}
        override fun finish(key: String,status: String) {values[key]=status}
    }
    @Test fun uncertainSendNeverRetriesSameJobAndManualReprintHasNewKey() {
        val claims=Claims();var sends=0
        val dispatcher=PrintDispatcher(claims,PrintTransport {_,_->sends++;error("lost connection")})
        assertFalse(dispatcher.dispatch("sale:1","127.0.0.1") {byteArrayOf(1)}.sent)
        assertEquals("unknown",claims.values["sale:1"])
        assertTrue(dispatcher.dispatch("sale:1","127.0.0.1") {byteArrayOf(1)}.duplicate)
        assertEquals(1,sends)
        dispatcher.dispatch("manual:2","127.0.0.1") {byteArrayOf(1)}
        assertEquals(2,sends)
    }
    @Test fun failedDurableClaimOrRendererNeverTouchesTransport() {
        val claims=Claims();claims.fail=true;var sends=0
        val dispatcher=PrintDispatcher(claims,PrintTransport {_,_->sends++})
        assertFalse(dispatcher.dispatch("job","127.0.0.1") {byteArrayOf(1)}.sent)
        assertFalse(dispatcher.dispatch("render","127.0.0.1") {error("too long")}.sent)
        assertEquals(0,sends);assertTrue(claims.values.isEmpty())
    }
    @Test fun lostCompletionWriteDoesNotResendDeliveredBytes() {
        var claimed=false;var sends=0
        val claims=object: PrintClaims {
            override fun claim(key: String): Boolean {if(claimed) return false;claimed=true;return true}
            override fun finish(key: String,status: String) {error("disk failure after send")}
        }
        val dispatcher=PrintDispatcher(claims,PrintTransport {_,_->sends++})
        assertFalse(dispatcher.dispatch("job","127.0.0.1") {byteArrayOf(1)}.sent)
        assertTrue(dispatcher.dispatch("job","127.0.0.1") {byteArrayOf(1)}.duplicate)
        assertEquals(1,sends)
    }
    @Test fun persistedAmountsAndMixedPaymentsArePrintedWithoutRecalculation() {
        val d=PrintDocument("r1",PrintKind.RECEIPT,0,items=listOf(PrintItem("Кофе","3","0.05",14,1)),total=14,gross=15,discount=1,delivery=0,payments=listOf(PrintPayment("cash",4,10,6),PrintPayment("card",10)))
        val page=PrintDocuments.page(d,PrinterConfiguration(paperWidth="80"))
        assertEquals("0.14 BYN",page.rows.single {it.left=="Кофе"}.right)
        assertEquals("0.14 BYN",page.rows.single {it.left=="Итого"}.right)
        assertEquals("0.04 BYN",page.rows.single {it.left=="Платёж 1 · Наличные"}.right)
        assertEquals("0.06 BYN",page.rows.single {it.left=="Сдача"}.right)
        assertEquals(576,page.width)
    }
    @Test fun kitchenFiltersCategoriesAndDoesNotPrintMoneyOrPayment() {
        val d=PrintDocument("k",PrintKind.KITCHEN,0,label="Стол 4",items=listOf(PrintItem("Кофе","2","5.00",1000,category="Бар",comment="Без сахара"),PrintItem("Суп","1","8.00",800,category="Кухня")))
        val page=PrintDocuments.page(d,PrinterConfiguration(paperWidth="58",categories="Бар"))
        assertEquals(384,page.width)
        assertTrue(page.rows.any {it.left=="2 × Кофе" && it.size==38f})
        assertTrue(page.rows.any {it.left.contains("Без сахара")})
        assertFalse(page.rows.any {it.left.contains("Суп") || it.left.contains("BYN") || it.right!=null})
    }
    @Test fun kitchenDeltaUsesExactQuantitiesAndIgnoresDecreases() {
        val current=listOf(PrintItem("Кофе","3","0",0,id="a"),PrintItem("Весовой","0.000000002","0",0,id="b"))
        val delta=KitchenPrintDelta.items(current,mapOf("a" to "2","b" to "0.000000001"))
        assertEquals(listOf("1","0.000000001"),delta.map {it.quantity})
        assertTrue(KitchenPrintDelta.items(current,mapOf("a" to "4","b" to "1")).isEmpty())
    }
    @Test fun cashDrawerOnlyAcceptsEscPulseAndSinglePrinterOwnsIt() {
        assertArrayEquals(byteArrayOf(27,112,0,25,-6),EscPosCommands.drawer("1B,70,00,19,FA"))
        listOf("1B,40","1B,70,02,19,FA","1B,70,00,FA,19").forEach {try {EscPosCommands.drawer(it);fail()} catch(_:IllegalArgumentException) {}}
    }
    @Test fun billIsUnpaidAndHasNoPaymentLines() {
        val page=PrintDocuments.page(PrintDocument("bill",PrintKind.BILL,0,total=100,payments=listOf(PrintPayment("cash",100))),PrinterConfiguration())
        assertTrue(page.rows.any {it.left=="СЧЁТ · НЕ ОПЛАЧЕН"})
        assertFalse(page.rows.any {it.left=="Наличные"})
    }
    @Test fun routingHonorsKitchenReceiptAutoAndCashRules() {
        val receipt=PrinterConfiguration(id="receipt",autoReceipt=false)
        val kitchen=PrinterConfiguration(id="kitchen",printReceipts=false,printOrders=true)
        assertEquals(listOf(kitchen),PrinterRouting.targets(PrintKind.KITCHEN,listOf(receipt,kitchen),true))
        assertTrue(PrinterRouting.targets(PrintKind.RECEIPT,listOf(receipt,kitchen),true).isEmpty())
        assertEquals(listOf(receipt),PrinterRouting.targets(PrintKind.RECEIPT,listOf(receipt,kitchen)))
        assertEquals(listOf(receipt),PrinterRouting.targets(PrintKind.SHIFT,listOf(receipt,kitchen),true))
        val d=PrintDocument("r",PrintKind.RECEIPT,0,payments=listOf(PrintPayment("card",100)))
        assertFalse(PrinterRouting.opensDrawer(d))
        assertTrue(PrinterRouting.opensDrawer(d.copy(payments=listOf(PrintPayment("cash",40,50,10),PrintPayment("card",60)))))
        assertFalse(PrinterRouting.opensDrawer(d.copy(kind=PrintKind.BILL)))
    }
}
