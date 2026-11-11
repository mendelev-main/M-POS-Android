package com.mendelev.mpos

import com.mendelev.mpos.data.local.LoyaltyEffectCodec
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.workspace.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LoyaltyReceiptPriceTest {
    private val pizza=CartLine("paid","pizza","Пицца","10",false,"10","1","шт",emptyList(),emptyList(),emptyMap())
    private fun cart(lines: List<CartLine>)=CurrentCart(sessionId="session",lines=lines,
        loyalty=LoyaltySelection("a".repeat(64),LoyaltyCustomer("customer","Тест","+375290000001",active=true,consentVersion=1),emptyList(),datasetId="fixture"))
    @Test fun receiptDistinguishesPaidPizzaFromAlreadyFreeWebsitePizza() {
        val gift=pizza.copy(id="gift",basePrice="0",unitPrice="0",quotedPrice="0")
        val c=cart(listOf(pizza,gift))
        val first=LoyaltyEffectCodec.body("receipt",1000,c,emptyMap())
        val retry=LoyaltyEffectCodec.body("receipt",1000,c,emptyMap())
        assertEquals(first,retry)
        val items=JSONObject(first).getJSONArray("items")
        assertEquals(2,items.length())
        assertEquals("10",items.getJSONObject(0).get("unitPrice").toString())
        assertEquals("0",items.getJSONObject(1).get("unitPrice").toString())
        assertEquals("1",items.getJSONObject(0).get("quantity").toString())
        assertEquals("1",items.getJSONObject(1).get("quantity").toString())
        assertEquals("receipt",JSONObject(first).getString("orderId"))
        assertEquals(0,JSONObject(first).getJSONObject("redemptions").length())
    }
    @Test fun entirelyFreeReceiptRetainsStableIdentityWithoutInventingPaidUnits() {
        val body=JSONObject(LoyaltyEffectCodec.body("free-receipt",1000,cart(listOf(pizza.copy(basePrice="0",unitPrice="0",quotedPrice="0"))),emptyMap()))
        assertEquals("free-receipt",body.getString("orderId"))
        assertEquals("0",body.getJSONArray("items").getJSONObject(0).get("unitPrice").toString())
    }
}
