package com.mendelev.mpos.domain.catalog

import org.junit.Assert.*
import org.junit.Test

class CategoryAppearanceTest {
    @Test fun symbolsCountGraphemesAndPreserveEmojiSequences() {
        assertEquals("👨‍🍳❤️🇧🇾",CategoryAppearance.symbol("  👨‍🍳❤️🇧🇾  "))
        assertEquals("КФ",CategoryAppearance.symbol("КФ"))
        assertEquals("",CategoryAppearance.symbol("   "))
        assertEquals("е́☕К",CategoryAppearance.symbol("е́☕К"))
        for(value in listOf("АБВГ","☕👨‍🍳❤️🇧🇾","К\nФ","a\u0000b","a".repeat(257))) {
            try { CategoryAppearance.symbol(value);fail(value) } catch(_:CatalogFailure) {}
        }
    }
    @Test fun colorsNormalizeAndRejectMalformedValues() {
        assertEquals("#ABCDEF",CategoryAppearance.color(" #abcdef "))
        CategoryAppearance.palette.forEach { assertEquals(it,CategoryAppearance.color(it)) }
        for(value in listOf("red","#fff","#12345678","#GG0000","")) {
            try { CategoryAppearance.color(value);fail(value) } catch(_:CatalogFailure) {}
        }
    }
}
