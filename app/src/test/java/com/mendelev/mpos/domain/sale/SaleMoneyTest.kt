package com.mendelev.mpos.domain.sale

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class SaleMoneyTest {
    private fun reject(block: () -> Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun netLinesAreRoundedBeforeSummingWithoutRoundingDiscountTwice() {
        val d=SaleDiscount("percent","10")
        val grouped=SaleMoney.quote(listOf(SaleLineInput("a","0.05","3",d)))
        assertEquals(14L,grouped.total);assertEquals(1L,grouped.discount)
        val separate=SaleMoney.quote((1..3).map {SaleLineInput("$it","0.05","1",d)})
        assertEquals(15L,separate.total)
    }
    @Test fun fixedDiscountIsPerUnitCappedAtGoodsAndDeliveryRemainsSeparate() {
        val q=SaleMoney.quote(listOf(SaleLineInput("a","10","2",SaleDiscount("fixed","1"))),"delivery",DeliveryCharge("town","Город",500,1))
        assertEquals(1800L,q.subtotal);assertEquals(200L,q.discount);assertEquals(2300L,q.total)
        assertEquals(0L,SaleMoney.quote(listOf(SaleLineInput("a","10","2",SaleDiscount("fixed","20")))).total)
        reject {SaleMoney.quote(listOf(SaleLineInput("a","10","1",SaleDiscount("percent","100.01"))))}
        reject {SaleMoney.quote(listOf(SaleLineInput("a","10","1")),"delivery")}
        assertEquals(1000L,SaleMoney.quote(listOf(SaleLineInput("a","10","1")),"delivery",DeliveryCharge("free","Бесплатно",0,1)).total)
    }
    @Test fun mixedPaymentChangesCashByNetAmountAndDeliveryIsIssuedOnce() {
        val q=SaleMoney.quote(listOf(SaleLineInput("a","100","1")))
        val p=SaleMoney.payments(q,listOf(PaymentInput("cash",4000,5000),PaymentInput("card",6000)),0)
        assertEquals(4000L,p.cash);assertEquals(6000L,p.card);assertEquals(1000L,p.parts[0].change);assertEquals(4000L,p.expectedCashAfter)
        val delivery=SaleMoney.quote(listOf(SaleLineInput("a","20","1")),"delivery",DeliveryCharge("town","Город",500,1))
        reject {SaleMoney.payments(delivery,listOf(PaymentInput("card",2500)),0)}
        assertEquals(0L,SaleMoney.payments(delivery,listOf(PaymentInput("card",2500)),500).expectedCashAfter)
        assertEquals(2000L,SaleMoney.payments(delivery,listOf(PaymentInput("cash",2500,3000)),0).expectedCashAfter)
    }
    @Test fun equalSplitKeepsEveryKopeckAndZeroPartsAreValid() {
        assertEquals(listOf(334L,333L,333L),SaleMoney.equalParts(1000,3))
        val amounts=SaleMoney.equalParts(1,3);assertEquals(listOf(1L,0L,0L),amounts)
        val q=SaleMoney.quote(listOf(SaleLineInput("a","0.01","1")))
        assertEquals(1L,SaleMoney.payments(q,amounts.map {PaymentInput("cash",it,it)},0).cash)
    }
    @Test fun malformedInputsMismatchedPartsAndOverflowNeverProduceReceiptMoney() {
        listOf("-1","NaN","1e2","1.001").forEach {price->reject {SaleMoney.quote(listOf(SaleLineInput("a",price,"1")))}}
        reject {SaleMoney.quote(listOf(SaleLineInput("a","1","0")))}
        reject {SaleMoney.quote(listOf(SaleLineInput("a","92233720368547758.07","2")))}
        val q=SaleMoney.quote(listOf(SaleLineInput("a","1","1")))
        reject {SaleMoney.payments(q,listOf(PaymentInput("cash",100,99)),0)}
        reject {SaleMoney.payments(q,listOf(PaymentInput("card",100,100)),0)}
        reject {SaleMoney.payments(q,listOf(PaymentInput("card",99)),0)}
        reject {SaleMoney.payments(q,listOf(PaymentInput("cash",100,100)),Long.MAX_VALUE)}
    }
}
