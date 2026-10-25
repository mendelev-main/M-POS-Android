package com.mendelev.mpos.domain.catalog

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class RecipeCalculatorTest {
    private fun n(value: String) = BigDecimal(value)
    private val piece = QuantityUnit.PIECE
    private fun ingredient(id: String, qty: String, unit: QuantityUnit = piece) = RecipeComponent(id, n(qty), unit)
    private fun request(id: String, qty: String = "1", unit: QuantityUnit = piece) = RecipeRequest(id, n(qty), unit)
    private fun simple(id: String = "a", unit: QuantityUnit = piece, noStock: Boolean = false, negative: Boolean = false) =
        RecipeProduct.Simple(id, unit, n("2"), noStock, negative)
    private fun recipe(id: String, yield: String, vararg components: RecipeComponent) =
        RecipeProduct.Composite(id, piece, n(yield), components.toList())
    private fun equal(expected: String, actual: BigDecimal?) = assertEquals(0, n(expected).compareTo(requireNotNull(actual)))
    private fun fails(kind: String, block: () -> Unit) {
        val failure = assertThrows(CatalogFailure::class.java, block)
        assertEquals(kind, failure.kind)
    }

    @Test fun physicalUnitsConvertExactlyAndRejectOtherDimensions() {
        equal("0.0004", QuantityUnit.G.convert(n("0.4"), QuantityUnit.KG))
        equal("250", QuantityUnit.L.convert(n("0.25"), QuantityUnit.ML))
        equal("1.23", QuantityUnit.G.convert(QuantityUnit.KG.convert(n("1.23"), QuantityUnit.G), QuantityUnit.KG))
        fails("unit") { QuantityUnit.KG.convert(n("1"), QuantityUnit.L) }
        fails("unit") { piece.convert(n("1"), QuantityUnit.G) }
    }

    @Test fun thirdsAccumulateBeforeRoundingIncludingNestedRecipesAndRepeatedIngredients() {
        val calc = RecipeCalculator(listOf(simple(), recipe("third", "3", ingredient("a", "1")),
            recipe("nested", "1", ingredient("third", "3"), ingredient("a", "0.5"))))
        equal("0.333333333333", calc.consumption(listOf(request("third")))["a"])
        equal("1", calc.consumption(List(3) { request("third") })["a"])
        equal("1.5", calc.consumption(listOf(request("nested")))["a"])
        equal("3", calc.cost(listOf(request("nested"))))
    }

    @Test fun subGramConsumptionSurvivesAndDisplayUnitsDoNotChangeStock() {
        val calc = RecipeCalculator(listOf(simple(unit = QuantityUnit.KG),
            recipe("cup", "1", ingredient("a", "0.4", QuantityUnit.G))))
        equal("0.0004", calc.consumption(listOf(request("cup")))["a"])
        equal("0.04", calc.consumption(listOf(request("cup", "100")))["a"])
        equal("0.0008", calc.cost(listOf(request("cup"))))
    }

    @Test fun flagsApplyIndependentlyToEachIngredientAndCostIncludesUntrackedProducts() {
        val calc = RecipeCalculator(listOf(simple("free", noStock = true), simple("minus", negative = true), simple("limited"),
            recipe("meal", "1", ingredient("free", "5"), ingredient("minus", "2"), ingredient("limited", "1"))))
        val order = listOf(request("meal"))
        assertEquals(setOf("minus", "limited"), calc.consumption(order).keys)
        equal("16", calc.cost(order))
        val shortages = calc.shortages(order, mapOf("minus" to n("-3"), "limited" to n("2")), mapOf("limited" to n("1.5")))
        assertEquals(listOf("limited"), shortages.map { it.productId })
        equal("0.5", shortages.single().available)
        fails("stock") { calc.shortages(order, emptyMap()) }
    }

    @Test fun cyclesMissingReferencesInvalidYieldAndUnitsFailInsteadOfPartialResults() {
        fails("cycle") { RecipeCalculator(listOf(recipe("x", "1", ingredient("x", "1")))).consumption(listOf(request("x"))) }
        fails("reference") { RecipeCalculator(listOf(recipe("x", "1", ingredient("gone", "1")))).consumption(listOf(request("x"))) }
        fails("recipe") { RecipeCalculator(listOf(simple(), recipe("x", "0", ingredient("a", "1")))).consumption(listOf(request("x"))) }
        fails("recipe") { RecipeCalculator(listOf(simple(), recipe("x", "1", ingredient("a", "0")))).consumption(listOf(request("x"))) }
        fails("unit") { RecipeCalculator(listOf(simple())).consumption(listOf(request("a", unit = QuantityUnit.KG))) }
        fails("quantity") { RecipeCalculator(listOf(simple())).consumption(listOf(request("a", "-1"))) }
        fails("recipe") { RecipeCalculator(listOf(simple(), simple())) }
    }

    @Test fun quantitiesBelowStoragePrecisionAreRejectedRatherThanSilentlyLost() {
        val calc = RecipeCalculator(listOf(simple(), recipe("tiny", "10000000000000", ingredient("a", "1"))))
        fails("precision") { calc.consumption(listOf(request("tiny"))) }
    }
}
