package com.mendelev.mpos.domain.catalog

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Unit scales are exact; changing the display unit never changes a selling price. */
enum class QuantityUnit(val dimension: String, val factor: Long) {
    PIECE("count", 1), KG("mass", 1000), G("mass", 1), L("volume", 1000), ML("volume", 1);

    fun convert(quantity: BigDecimal, to: QuantityUnit): BigDecimal {
        if (dimension != to.dimension) throw CatalogFailure("unit", "Несовместимые единицы измерения")
        return quantity.multiply(BigDecimal.valueOf(factor)).divide(BigDecimal.valueOf(to.factor))
    }
}

sealed interface RecipeProduct {
    val id: String
    val unit: QuantityUnit

    data class Simple(
        override val id: String,
        override val unit: QuantityUnit,
        val cost: BigDecimal,
        val noStockTracking: Boolean = false,
        val allowNegativeStock: Boolean = false,
    ) : RecipeProduct

    /** Components specify physical quantities, not a hidden number of recipe batches. */
    data class Composite(
        override val id: String,
        override val unit: QuantityUnit,
        val yield: BigDecimal,
        val components: List<RecipeComponent>,
    ) : RecipeProduct
}

data class RecipeComponent(val productId: String, val quantity: BigDecimal, val unit: QuantityUnit)
data class RecipeRequest(val productId: String, val quantity: BigDecimal, val unit: QuantityUnit)
data class StockShortage(val productId: String, val required: BigDecimal, val available: BigDecimal)

/** Pure preview calculation. This class never writes stock or records a sale. */
class RecipeCalculator(products: Collection<RecipeProduct>) {
    // Copy mutable caller collections: a calculation uses a single catalogue snapshot.
    private val products = products.map {
        if (it is RecipeProduct.Composite) it.copy(components = it.components.toList()) else it
    }.associateBy { it.id }

    init {
        if (this.products.size != products.size || products.any { it.id.isBlank() })
            throw CatalogFailure("recipe", "Повторяющийся или пустой идентификатор товара")
    }

    fun consumption(requests: List<RecipeRequest>): Map<String, BigDecimal> =
        expand(requests).filterKeys { !(products.getValue(it) as RecipeProduct.Simple).noStockTracking }
            .mapValues { (_, value) -> value.stockDecimal() }

    /** Physical portions only: no reservations/negative-sale permission, no decimal intermediate rounding. */
    fun physicalPortions(request: RecipeRequest, balances: Map<String, BigDecimal>): BigDecimal? =
        expand(listOf(request)).filterKeys { !(products.getValue(it) as RecipeProduct.Simple).noStockTracking }
            .map { (id, required) -> (Fraction.of(balances[id] ?: BigDecimal.ZERO) / required).floor() }
            .minOrNull()

    /** Includes the cost of ingredients whose stock tracking is disabled. No monetary rounding here. */
    fun cost(requests: List<RecipeRequest>): BigDecimal = expand(requests).entries.fold(Fraction.ZERO) { sum, (id, qty) ->
        val cost = (products.getValue(id) as RecipeProduct.Simple).cost
        if (cost.signum() < 0) throw CatalogFailure("cost", "Себестоимость не может быть отрицательной")
        sum + qty * Fraction.of(cost)
    }.decimal()

    /** Monetary report boundary: round the exact rational once, without the scale12 stock conversion. */
    fun costMinor(requests: List<RecipeRequest>): BigInteger {
        val exact=expand(requests).entries.fold(Fraction.ZERO) {sum,(id,qty)->
            val cost=(products.getValue(id) as RecipeProduct.Simple).cost
            if(cost.signum()<0) throw CatalogFailure("cost","Себестоимость не может быть отрицательной")
            sum+qty*Fraction.of(cost)
        }
        return BigDecimal(exact.numerator).multiply(BigDecimal(100)).divide(BigDecimal(exact.denominator),0,RoundingMode.HALF_UP).toBigIntegerExact()
    }

    fun shortages(
        requests: List<RecipeRequest>, balances: Map<String, BigDecimal>,
        reservations: Map<String, BigDecimal> = emptyMap(),
    ): List<StockShortage> = consumption(requests).mapNotNull { (id, required) ->
        val product = products.getValue(id) as RecipeProduct.Simple
        val reserved = reservations[id] ?: BigDecimal.ZERO
        if (reserved.signum() < 0) throw CatalogFailure("stock", "Резерв не может быть отрицательным")
        val balance = balances[id] ?: throw CatalogFailure("stock", "Не найден остаток ингредиента")
        val available = balance - reserved
        if (!product.allowNegativeStock && required > available) StockShortage(id, required, available) else null
    }

    private fun expand(requests: List<RecipeRequest>): Map<String, Fraction> {
        val result = linkedMapOf<String, Fraction>()
        fun visit(id: String, quantity: Fraction, unit: QuantityUnit, path: Set<String>) {
            if (id in path) throw CatalogFailure("cycle", "Рецептура содержит цикл")
            if (path.size >= 64) throw CatalogFailure("recipe", "Слишком глубокая рецептура")
            val product = products[id] ?: throw CatalogFailure("reference", "Ингредиент не найден")
            if (unit.dimension != product.unit.dimension) throw CatalogFailure("unit", "Несовместимые единицы измерения")
            val baseQuantity = quantity * Fraction.of(BigDecimal.valueOf(unit.factor)) /
                Fraction.of(BigDecimal.valueOf(product.unit.factor))
            when (product) {
                is RecipeProduct.Simple -> result[id] = (result[id] ?: Fraction.ZERO) + baseQuantity
                is RecipeProduct.Composite -> {
                    if (product.yield.signum() <= 0 || product.components.isEmpty())
                        throw CatalogFailure("recipe", "Задайте положительный выход и состав рецепта")
                    val batches = baseQuantity / Fraction.of(product.yield)
                    product.components.forEach {
                        if (it.quantity.signum() <= 0) throw CatalogFailure("recipe", "Количество ингредиента должно быть больше нуля")
                        visit(it.productId, batches * Fraction.of(it.quantity), it.unit, path + id)
                    }
                }
            }
        }
        requests.forEach {
            if (it.quantity.signum() <= 0) throw CatalogFailure("quantity", "Количество должно быть больше нуля")
            visit(it.productId, Fraction.of(it.quantity), it.unit, emptySet())
        }
        return result
    }
}

/** Exact rational intermediates avoid rounding 1/3 at each edge of a nested recipe. */
private class Fraction private constructor(val numerator: BigInteger, val denominator: BigInteger) {
    operator fun plus(other: Fraction) = normalized(
        numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun times(other: Fraction) = normalized(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: Fraction) = normalized(numerator * other.denominator, denominator * other.numerator)
    fun floor(): BigDecimal = BigDecimal(numerator).divide(BigDecimal(denominator), 0, RoundingMode.FLOOR)
    fun decimal(): BigDecimal = BigDecimal(numerator).divide(BigDecimal(denominator), 12, RoundingMode.HALF_EVEN)
        .stripTrailingZeros()
    fun stockDecimal(): BigDecimal = decimal().also {
        if (it.signum() <= 0 || it.precision() - it.scale() > 18)
            throw CatalogFailure("precision", "Расход вне допустимой точности склада")
    }
    companion object {
        val ZERO = of(BigDecimal.ZERO)
        fun of(value: BigDecimal): Fraction = if (value.scale() >= 0)
            normalized(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
        else normalized(value.unscaledValue() * BigInteger.TEN.pow(-value.scale()), BigInteger.ONE)
        private fun normalized(n: BigInteger, d: BigInteger): Fraction {
            if (d.signum() <= 0) throw CatalogFailure("recipe", "Некорректный делитель рецептуры")
            val gcd = n.gcd(d)
            return Fraction(n / gcd, d / gcd)
        }
    }
}
