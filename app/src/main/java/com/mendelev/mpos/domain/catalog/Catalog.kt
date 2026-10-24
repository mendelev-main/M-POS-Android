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
) {
    fun validated(): ProductInput = copy(name = CatalogRules.name(name),
        price = CatalogRules.decimal(price, 2, "Цена"), cost = CatalogRules.decimal(cost, 8, "Себестоимость"),
        stock = CatalogRules.decimal(stock, 9, "Остаток"))
    fun fingerprint(): String = CatalogRules.fingerprint(id, expectedRevision, name, categoryId, price, cost,
        if (stockEdited || expectedRevision == null) stock else null, expectedStockRevision, stockEdited)
}
