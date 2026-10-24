package com.mendelev.mpos.domain.catalog

import org.junit.Assert.*
import org.junit.Test

class CatalogRulesTest {
    @Test fun exactDecimalsAndSearch() {
        assertEquals("2.5", CatalogRules.decimal(" 2,50 ",2,"Цена"))
        assertEquals("0.0004", CatalogRules.decimal("0.000400000",9,"Остаток"))
        assertEquals("черный чай",CatalogRules.key("  ЧЁРНЫЙ   чай "))
        assertEquals("0.6",CatalogRules.canonical("1".toBigDecimal() - "0.0004".toBigDecimal()*1000.toBigDecimal()))
    }
    @Test fun malformedAndOverflowDoNotBecomeZero() {
        listOf("", "12abc", "NaN", "Infinity", "1e3", "1,2.3", "-1", "1 000", "2.501", "1000000000").forEach {
            assertThrows(CatalogFailure::class.java) { CatalogRules.decimal(it,2,"Цена") }
        }
        assertThrows(CatalogFailure::class.java) { CatalogRules.name(" ") }
        assertThrows(CatalogFailure::class.java) { CatalogRules.name("a".repeat(201)) }
    }
    @Test fun fingerprintsHaveUnambiguousBoundaries() {
        assertNotEquals(CatalogRules.fingerprint("ab","c"),CatalogRules.fingerprint("a","bc"))
        assertEquals(CatalogRules.fingerprint("x",null,2),CatalogRules.fingerprint("x",null,2))
    }
}
