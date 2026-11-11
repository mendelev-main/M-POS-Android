package com.mendelev.mpos

import com.mendelev.mpos.print.*
import com.mendelev.mpos.fixture.WebReceiptReference
import com.mendelev.mpos.data.local.PrinterConfiguration
import com.mendelev.mpos.domain.workspace.ChosenOption
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Independent frozen Web layout is the oracle, with identical logical input. No printer I/O. */
class ReceiptWebParityTest {
    private fun same(d: PrintDocument,json: JSONObject,printer: PrinterConfiguration) {
        json.put("__printerConfig",JSONObject().put("paperWidth",printer.paperWidth.toInt())
            .put("paymentReceiptTitle",printer.receiptTitle).put("registerLabel",printer.registerLabel)
            .put("printPaymentComments",printer.paymentComments).put("receiptRandomPhrases",org.json.JSONArray(printer.phrases.lines().filter {it.isNotBlank()})))
        val expected=WebReceiptReference.build(json,{it.first()},PrintDocuments::receiptDate)
        val actual=PrintDocuments.page(d,printer) {it.first()}
        assertEquals(expected.width,actual.width);assertEquals(expected.margin,actual.margin)
        assertEquals(expected.top,actual.top);assertEquals(expected.bottom,actual.bottom)
        assertEquals(expected.rows.size,actual.rows.size)
        expected.rows.forEachIndexed {i,r->
            assertEquals("row $i / paper ${printer.paperWidth}",PrintRow(r.left,if(r.kind==WebReceiptReference.Kind.PAIR)r.right else null,r.size,r.weight,r.centered,r.kind==WebReceiptReference.Kind.SEPARATOR,r.gap),actual.rows[i])
        }
    }
    private fun base()=JSONObject("""{"employeeName":"Анна","orderType":"На месте","timestamp":0,"receiptDisplayNumber":"#42","orderLabel":"Стол 4","total":9.5,"subtotalBeforeDiscounts":10,"productDiscountTotal":1,"deliveryFee":0.5,"customer":{"name":"Тест","phone":"375290000000"},"items":[{"name":"Кофе","qty":2,"price":5,"discountType":"fixed","discountValue":0.5,"discountName":"Акция","comment":"Без сахара","selectedModifiers":[{"name":"Молоко","qty":2,"priceDelta":0.5}]}],"payments":[{"method":"cash","amount":4.5,"cashGiven":5,"change":0.5},{"method":"card","amount":5}]}""")
    private val document=PrintDocument("fixture",PrintKind.RECEIPT,0,label="Стол 4",number="#42",employee="Анна",
        items=listOf(PrintItem("Кофе","2","5",900,100,"Акция",comment="Без сахара",options=listOf(ChosenOption("m","Молоко","2","0.5")))),
        total=950,gross=1000,discount=100,delivery=50,customer="Тест",phone="375290000000",
        payments=listOf(PrintPayment("cash",450,500,50),PrintPayment("card",500)))
    @Test fun paymentAndBillRowsMatchWebForBothPaperWidths() {
        listOf("58","80").forEach {width->
            val p=PrinterConfiguration(paperWidth=width,phrases="Спасибо!")
            same(document,base(),p)
            same(document.copy(kind=PrintKind.BILL),base().put("__printDocumentType","bill"),p)
            same(document.copy(payments=listOf(PrintPayment("cash",950,1000,50))),
                base().put("payments",org.json.JSONArray("""[{"method":"cash","amount":9.5,"cashGiven":10,"change":0.5}]""")),p.copy(paymentComments=false))
        }
    }
    @Test fun kitchenRowsMatchWebForBothPaperWidths() {
        listOf("58","80").forEach {width->same(document.copy(kind=PrintKind.KITCHEN),
            base().put("__printDocumentType","kitchen"),PrinterConfiguration(paperWidth=width))}
    }
    @Test fun shiftRowsMatchWebForBothPaperWidths() {
        val j=JSONObject("""{"__printDocumentType":"shift-close","employeeName":"Анна","openedAt":1000,"closedAt":2000,"count":2,"total":20,"cash":8,"card":12,"openingCash":5,"deposits":3,"withdrawals":1,"expectedCash":15,"countedCash":14,"difference":-1}""")
        val values=listOf("Открыта" to PrintDocuments.receiptDate(1000),"Закрыта" to PrintDocuments.receiptDate(2000),"Заказов" to "2","Выручка" to "20.00 BYN","Наличные" to "8.00 BYN","Карта" to "12.00 BYN","Наличные на начало" to "5.00 BYN","Внесения" to "3.00 BYN","Изъятия" to "1.00 BYN","Ожидается в кассе" to "15.00 BYN","Фактически в кассе" to "14.00 BYN","Расхождение" to "-1.00 BYN")
        listOf("58","80").forEach {width->same(PrintDocument("shift",PrintKind.SHIFT,2000,label="Тест кафе",employee="Анна",shiftRows=values),j.put("establishmentName","Тест кафе"),PrinterConfiguration(paperWidth=width))}
    }
    @Test fun loyaltyGiftHasItsOwnDiscountRowWithoutChangingPaymentTotal() {
        val line=com.mendelev.mpos.domain.workspace.CartLine("a","a","Кофе","5",false,"5","2","шт",emptyList(),emptyList(),emptyMap())
        val program=com.mendelev.mpos.domain.loyalty.LoyaltyProgram("p","Кофе в подарок",5,listOf("a"),listOf("a"),rewards=1)
        val cart=com.mendelev.mpos.domain.workspace.CurrentCart(lines=listOf(line),sessionId="fixture",loyalty=com.mendelev.mpos.domain.loyalty.LoyaltySelection("a".repeat(64),com.mendelev.mpos.domain.loyalty.LoyaltyCustomer("c","Тест","375290000000",active=true,consentVersion=1),listOf(program),setOf("p")))
        val quote=com.mendelev.mpos.domain.loyalty.LoyaltyRules.quote(cart,emptyMap())
        val d=PrintDocuments.cart(cart,quote,PrintKind.BILL,"Анна",emptyMap())
        assertEquals(500L,d.total);assertEquals(0L,d.discount);assertEquals(1000L,d.items.single().net)
        val rows=PrintDocuments.page(d,PrinterConfiguration()).rows
        assertEquals("−5.00 BYN",rows.single {it.left=="Лояльность · Кофе в подарок"}.right)
        assertEquals("5.00 BYN",rows.single {it.left=="Итого"}.right)
    }
    @Test fun loyaltyUsesConfirmedEventAndTwelveDotsPerLine() {
        val rows=LoyaltyReceiptRows.progress("""{"programs":[{"id":"p","name":"Кофе","required_quantity":14,"progress":1,"rewards":0}],"events":[{"programId":"p","progress":3,"rewards":1}]}""")
        assertEquals(listOf("Кофе" to ((List(12) {"●"}.joinToString(" "))+"\n● ●")),rows)
        assertTrue(LoyaltyReceiptRows.progress("""{"programs":[{"required_quantity":0}]}""").isEmpty())
    }
}
