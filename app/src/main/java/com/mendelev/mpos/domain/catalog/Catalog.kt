package com.mendelev.mpos.domain.catalog

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale

class CatalogFailure(val kind: String, message: String) : IllegalArgumentException(message)

object CatalogRules {
    fun key(text: String): String = text.trim().lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("\\s+"), " ")
    fun name(value: String, max: Int = 200): String = value.trim().also {
        if (it.isEmpty() || it.codePointCount(0, it.length) > max) throw CatalogFailure("name", "Введите название от 1 до $max символов")
    }
    fun decimal(raw: String, scale: Int, field: String): String {
        val value = raw.trim().replace(',', '.')
        if (!Regex("[0-9]+(?:\\.[0-9]+)?").matches(value)) throw CatalogFailure(field, "Введите корректное число: $field")
        val n = value.toBigDecimal()
        if (n.scale() > scale || n > BigDecimal("999999999." + "9".repeat(scale)))
            throw CatalogFailure(field, "Проверьте $field: максимум $scale знаков после запятой")
        return canonical(n)
    }
    fun canonical(n: BigDecimal): String = if (n.signum() == 0) "0" else n.stripTrailingZeros().toPlainString()
    fun fingerprint(vararg parts: Any?): String {
        val bytes = parts.joinToString("") { if (it == null) "N;" else { val s = it.toString(); "S${s.length}:$s" } }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

data class ProductInput(
    val id: String, val expectedRevision: Long?, val name: String, val categoryId: String?,
    val price: String, val cost: String, val stock: String, val expectedStockRevision: Long?,
    val stockEdited: Boolean, val commandId: String,
    val type: String = "simple", val unit: String = "piece", val displayUnit: String = unit,
    val noStockTracking: Boolean = false, val allowNegativeStock: Boolean = false,
    val minimumStock: String? = null, val purchaseLabel: String = "", val packSize: String? = null,
    val recipeYield: String = "1", val components: List<IngredientInput> = emptyList(),
    val modifierGroups: List<ModifierGroupInput> = emptyList(), val photoId: String? = null,
) {
    fun validated(): ProductInput {
        if (type !in listOf("simple", "composite")) throw CatalogFailure("type", "Выберите тип товара")
        val base = quantityUnit(unit); val display = quantityUnit(displayUnit)
        display.convert(BigDecimal.ONE, base)
        val stockValue = stock.trim().replace(',', '.')
        val negative = stockValue.startsWith('-')
        val validatedStock = CatalogRules.decimal(stockValue.removePrefix("-"), 12, "Остаток")
        if (negative && (stockEdited || expectedRevision == null) && !allowNegativeStock)
            throw CatalogFailure("Остаток", "Для отрицательного остатка разрешите продажу в минус")
        if (purchaseLabel.isNotEmpty() && purchaseLabel !in listOf("pack", "box", "bottle"))
            quantityUnit(purchaseLabel).convert(BigDecimal.ONE, base)
        val normalized = copy(name = CatalogRules.name(name), price = CatalogRules.decimal(price, 2, "Цена"),
            cost = if (type == "simple") CatalogRules.decimal(cost, 12, "Себестоимость") else "0",
            stock = if (negative && validatedStock != "0") "-$validatedStock" else validatedStock,
            minimumStock = minimumStock?.takeIf { it.isNotBlank() }?.let { CatalogRules.decimal(it, 12, "Минимум") },
            packSize = packSize?.takeIf { it.isNotBlank() }?.let { CatalogRules.decimal(it, 12, "Упаковка") },
            purchaseLabel = purchaseLabel.trim(), recipeYield = CatalogRules.decimal(recipeYield, 12, "Выход"),
            components = components.map { it.copy(quantity = CatalogRules.decimal(it.quantity, 12, "Ингредиент")) })
        if (normalized.packSize?.toBigDecimal()?.signum() == 0) throw CatalogFailure("pack", "Размер упаковки должен быть больше нуля")
        if (type == "composite") {
            if (noStockTracking || allowNegativeStock || minimumStock != null || packSize != null || purchaseLabel.isNotBlank())
                throw CatalogFailure("type", "Складские параметры доступны только простому товару")
            if (normalized.stock != "0") throw CatalogFailure("recipe", "У составного товара нет собственного остатка")
            if (normalized.recipeYield.toBigDecimal().signum() <= 0 || components.isEmpty())
                throw CatalogFailure("recipe", "Задайте положительный выход и состав")
        } else if (components.isNotEmpty()) throw CatalogFailure("recipe", "Простой товар не может иметь состав")
        if(photoId != null && !Regex("[0-9a-f-]{36}\\.jpg").matches(photoId)) throw CatalogFailure("photo", "Некорректная ссылка на фото")
        return normalized.copy(modifierGroups=ModifierRules.validate(id,modifierGroups))
    }
    fun fingerprint(): String {
        if(photoId==null && modifierGroups.isEmpty()) return configurationFingerprint()
        val parts=modifierGroups.flatMap { g -> listOf(g.id,g.name,g.minimum,g.maximum,g.options.size.toString()) +
            g.options.flatMap { listOf(it.id,it.productId,it.posName,it.quantity,it.priceDelta) } }
        return CatalogRules.fingerprint("product-01c",configurationFingerprint(),photoId,modifierGroups.size,*parts.toTypedArray())
    }
    private fun configurationFingerprint(): String {
        val legacy = arrayOf<Any?>(id, expectedRevision, name, categoryId, price, cost,
            if (stockEdited || expectedRevision == null) stock else null, expectedStockRevision, stockEdited)
        // Existing completed 01a commands remain replayable after migration.
        if (type == "simple" && unit == "piece" && displayUnit == "piece" && !noStockTracking && !allowNegativeStock &&
            minimumStock == null && purchaseLabel.isEmpty() && packSize == null && recipeYield == "1" && components.isEmpty())
            return CatalogRules.fingerprint(*legacy)
        return CatalogRules.fingerprint(*legacy, type, unit, displayUnit, noStockTracking, allowNegativeStock,
            minimumStock, purchaseLabel, packSize, recipeYield,
            *components.flatMap { listOf(it.productId, it.quantity, it.unit) }.toTypedArray())
    }

}

data class IngredientInput(val productId: String, val quantity: String, val unit: String)

fun quantityUnit(code: String): QuantityUnit = QuantityUnit.entries.find { it.name.lowercase(Locale.ROOT) == code }
    ?: throw CatalogFailure("unit", "Выберите единицу измерения")

fun quantityCode(unit: QuantityUnit): String = unit.name.lowercase(Locale.ROOT)
fun unitLabel(code: String): String = when (code) {
    "piece" -> "шт"; "kg" -> "кг"; "g" -> "г"; "l" -> "л"; "ml" -> "мл"; else -> code
}
