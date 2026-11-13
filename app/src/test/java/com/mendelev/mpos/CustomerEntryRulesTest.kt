package com.mendelev.mpos

import com.mendelev.mpos.domain.loyalty.*
import org.junit.Assert.*
import org.junit.Test

class CustomerEntryRulesTest {
    @Test fun accountQrRequiresFullPhoneAndExactUniqueCustomerNotOnlySuffix() {
        val first=LoyaltyCustomer("one","Первый","+375291234567")
        val other=LoyaltyCustomer("two","Второй","375331234567")
        assertEquals("375291234567",CustomerEntryRules.qrPhone(" +375291234567 "))
        assertEquals(first,CustomerEntryRules.qrMatch(listOf(other,first),"375291234567"))
        for(raw in listOf("4567","https://example.org/375291234567","tel:+375291234567","+48123456789","375291234567extra",""))
            assertThrows(IllegalArgumentException::class.java) {CustomerEntryRules.qrPhone(raw)}
        assertThrows(IllegalArgumentException::class.java) {CustomerEntryRules.qrMatch(listOf(other),"375291234567")}
        assertThrows(IllegalArgumentException::class.java) {CustomerEntryRules.qrMatch(listOf(first,first.copy(id="duplicate")),"375291234567")}
    }
    @Test fun phonePasteAndFixedPrefixMatchWebAndroidAdapter() {
        assertEquals("291234567",CustomerEntryRules.national("+375 (29) 123-45-67"))
        assertEquals("291234567",CustomerEntryRules.national("375291234567"))
        assertEquals("291234567",CustomerEntryRules.national("29 123 45 67"))
        assertEquals("",CustomerEntryRules.national("+48 123456789"))
        assertEquals("4567",CustomerEntryRules.suffix("+375 (29) 123-45-67"))
        assertEquals("",CustomerEntryRules.suffix("абв"))
        assertEquals("Тест" to "+375291234567",CustomerEntryRules.registration(" Тест ","29 123 45 67"))
    }
    @Test fun invalidRegistrationFailsAndSearchNeverChoosesNonMatchingServerRows() {
        for((name,phone) in listOf("" to "291234567","Тест" to "1234","Тест" to "+48 123456789")) {
            try {CustomerEntryRules.registration(name,phone);fail()} catch(_:IllegalArgumentException) {}
        }
        val rows=listOf(LoyaltyCustomer("one","Первый","+375 29 1234567"),LoyaltyCustomer("two","Второй","375291238888"))
        assertEquals(listOf("one"),CustomerEntryRules.matching(rows,"4567").map {it.id})
    }
}
