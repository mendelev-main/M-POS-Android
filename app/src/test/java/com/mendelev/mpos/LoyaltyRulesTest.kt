package com.mendelev.mpos

import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.data.local.*
import org.junit.Assert.*
import org.junit.Test

class LoyaltyRulesTest {
    private fun line(id: String,price: String,qty: String="1")=CartLine(id,id,id,price,false,price,qty,"шт",emptyList(),emptyList(),emptyMap())
    private val p=LoyaltyProgram("p","Каждый шестой",5,listOf("a","b"),listOf("a","b"),rewards=1)
    private fun cart(lines: List<CartLine>,programs: List<LoyaltyProgram> = listOf(p))=CurrentCart(lines=lines,sessionId="fixture",loyalty=LoyaltySelection("a".repeat(64),LoyaltyCustomer("customer","Тест","375290000000",active=true,consentVersion=1),programs,programs.map {it.id}.toSet()))
    @Test fun cheapestEligibleGiftUsesNetDiscountAndNeverDelivery() {
        val c=cart(listOf(line("a","10","2"),line("b","6"))).copy(details=OrderDetails(type="delivery"))
        val q=LoyaltyRules.quote(c,mapOf("b" to SaleDiscount("fixed","1")),DeliveryCharge("d","Доставка",500,1))
        assertEquals(2500L,q.total)
        assertEquals(500L,q.delivery!!.fee)
        assertEquals(0L,q.lines.single {it.id=="b"}.net)
        assertEquals(500L,LoyaltyRules.allocation(c,mapOf("b" to SaleDiscount("fixed","1"))).gifts.single().discount)
    }
    @Test fun programsNeverReuseSameUnitAndFractionalRemainderStaysPayable() {
        val c=cart(listOf(line("a","2","2.5")),listOf(p,p.copy(id="p2"),p.copy(id="p3")))
        val allocation=LoyaltyRules.allocation(c,emptyMap())
        assertEquals(2,allocation.gifts.size)
        assertEquals(100L,LoyaltyRules.quote(c,emptyMap()).total)
        assertEquals(400L,allocation.gifts.sumOf {it.discount})
    }
    @Test fun missingGiftDoesNotMakeAnotherItemOrDeliveryFree() {
        val c=cart(listOf(line("unrelated","3")))
        assertTrue(LoyaltyRules.allocation(c,emptyMap()).gifts.isEmpty())
        assertEquals(300L,LoyaltyRules.quote(c,emptyMap()).total)
        val free=cart(listOf(line("a","10")))
        val q=LoyaltyRules.quote(free,emptyMap())
        assertEquals(0L,q.total)
        assertEquals(0L,SaleMoney.payments(q,listOf(PaymentInput("cash",0,0)),0).cash)
    }
    @Test fun selectionAndHistoricalGiftSurviveCodecAndRemovingCustomerClearsGifts() {
        val c=cart(listOf(line("a","10","2")))
        assertEquals(c,WorkspaceCodec.cart(WorkspaceCodec.cart(c)))
        assertEquals(1000L,LoyaltyRules.quote(WorkspaceCodec.cart(WorkspaceCodec.cart(c)),emptyMap()).total)
        assertEquals(2000L,LoyaltyRules.quote(c.copy(loyalty=null),emptyMap()).total)
    }
    @Test fun membershipOrBalanceCannotBeInventedLocally() {
        val c=cart(listOf(line("a","10")))
        try {LoyaltyRules.valid(c.loyalty!!.copy(customer=c.loyalty.customer.copy(active=false)));fail()} catch(_:IllegalArgumentException) {}
        try {LoyaltyRules.valid(c.loyalty!!.copy(programs=listOf(p.copy(rewards=0))));fail()} catch(_:IllegalArgumentException) {}
    }
}
