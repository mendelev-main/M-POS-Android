package com.mendelev.mpos.domain.catalog

import org.junit.Assert.*
import org.junit.Test

class CatalogPresentationTest {
    @Test fun searchMatchesWebCaseYoWhitespaceAndCategory() {
        assertTrue(CatalogPresentationRules.matches("Ёлка  2","Кофе"," елка 2 "))
        assertTrue(CatalogPresentationRules.matches("Капучино","Горячие напитки","ГОРЯЧИЕ\u00a0НАПИТКИ"))
        assertTrue(CatalogPresentationRules.matches("Йогурт","Десерты","ЙОГУРТ"))
        assertFalse(CatalogPresentationRules.matches("Чай","Напитки","кофе"))
        assertTrue(CatalogPresentationRules.compareNames("Товар 2","Товар 10")<0)
    }
    @Test fun metadataValidationAndFingerprintDoNotChangeLegacyCommands() {
        val base=ProductInput("p",null,"Чай",null,"2","1","0",null,true,"create")
        assertEquals(base.fingerprint(),base.copy(details=null).fingerprint())
        val edited=base.copy(details=ProductDetails(" A-1 "," заметка\nдля сотрудников ","☕" )).validated()
        assertEquals("A-1",edited.details!!.sku);assertEquals("заметка\nдля сотрудников",edited.details!!.note)
        assertNotEquals(base.fingerprint(),edited.fingerprint())
        assertTrue(runCatching {base.copy(details=ProductDetails(sku="x".repeat(81))).validated()}.isFailure)
        assertTrue(runCatching {base.copy(details=ProductDetails(note="x".repeat(2001))).validated()}.isFailure)
        assertTrue(runCatching {base.copy(details=ProductDetails(symbol="АБВГ")).validated()}.isFailure)
    }
}
