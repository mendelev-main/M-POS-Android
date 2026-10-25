package com.mendelev.mpos.domain.catalog

import org.junit.Assert.*
import org.junit.Test

class CatalogConfigurationRulesTest {
    private fun input() = ProductInput("p",null,"Товар",null,"2","1","0",null,false,"command")
    @Test fun legacyFingerprintStaysCompatibleButEveryNewConfigurationChangeHasItsOwnFingerprint() {
        val i=input().validated()
        assertEquals(CatalogRules.fingerprint(i.id,i.expectedRevision,i.name,i.categoryId,i.price,i.cost,i.stock,i.expectedStockRevision,i.stockEdited),i.fingerprint())
        assertNotEquals(i.fingerprint(),i.copy(noStockTracking=true).fingerprint())
        assertNotEquals(i.fingerprint(),i.copy(allowNegativeStock=true).fingerprint())
        assertNotEquals(i.fingerprint(),i.copy(packSize="2").fingerprint())
        assertNotEquals(i.fingerprint(),i.copy(unit="kg",displayUnit="g").fingerprint())
    }
    @Test fun validationRejectsInvalidFlagsUnitsAndNegativeBalanceWithoutPermission() {
        assertThrows(CatalogFailure::class.java) { input().copy(unit="kg",displayUnit="ml").validated() }
        assertThrows(CatalogFailure::class.java) { input().copy(stock="-1").validated() }
        assertEquals("-1",input().copy(stock="-1",allowNegativeStock=true).validated().stock)
        assertEquals("-1",input().copy(stock="-1",expectedRevision=1).validated().stock)
        assertThrows(CatalogFailure::class.java) { input().copy(packSize="0").validated() }
        assertThrows(CatalogFailure::class.java) { input().copy(type="composite",noStockTracking=true).validated() }
        assertThrows(CatalogFailure::class.java) { input().copy(type="composite",recipeYield="0").validated() }
        assertThrows(CatalogFailure::class.java) { input().copy(components=listOf(IngredientInput("a","1","piece"))).validated() }
    }
}
