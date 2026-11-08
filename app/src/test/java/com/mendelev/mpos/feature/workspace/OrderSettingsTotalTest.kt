package com.mendelev.mpos.feature.workspace

import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import org.junit.Assert.*
import org.junit.Test

class OrderSettingsTotalTest {
    @Test fun deliveryIsAddedOnceAfterRoundedDiscountedLines() {
        val line=CartLine("line","p","Товар","0.05",false,"0.05","3","piece",emptyList(),emptyList(),emptyMap())
        val cart=CurrentCart(lines=listOf(line),details=OrderDetails(type="delivery"))
        val tariff=DeliveryCharge("city","По городу",500,1)
        val discounts=mapOf("line" to SaleDiscount("percent","10"))
        assertEquals(514L,orderSettingsTotal(cart,discounts,tariff))
        assertEquals(14L,orderSettingsTotal(cart.copy(details=OrderDetails()),discounts,tariff))
        assertEquals(14L,orderSettingsTotal(cart,discounts,null))
    }
    @Test fun zeroFeeIsASelectedTariffAndEmptyOrderCanShowDelivery() {
        val cart=CurrentCart(details=OrderDetails(type="delivery"))
        assertEquals(0L,orderSettingsTotal(cart,emptyMap(),DeliveryCharge("free","Бесплатно",0,1)))
        assertEquals(500L,orderSettingsTotal(cart,emptyMap(),DeliveryCharge("city","По городу",500,1)))
    }
    @Test fun savedLineDiscountOverridesImportedDefaultsAndCanBeExplicitlyCleared() {
        val line=CartLine("line","p","Товар","10",false,"10","2","piece",emptyList(),emptyList(),emptyMap(),discount=SaleDiscount("fixed","1"),discountEdited=true)
        val cart=CurrentCart(lines=listOf(line))
        assertEquals(1800L,orderSettingsTotal(cart,emptyMap(),null))
        val cleared=cart.copy(lines=listOf(line.copy(discount=null)))
        assertEquals(2000L,orderSettingsTotal(cleared,mapOf("line" to SaleDiscount("percent","50")),null))
    }

}
