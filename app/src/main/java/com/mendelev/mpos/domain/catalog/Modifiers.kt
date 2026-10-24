package com.mendelev.mpos.domain.catalog

import java.math.BigDecimal

data class ModifierOptionInput(val id: String, val productId: String, val posName: String = "", val quantity: String = "1", val priceDelta: String = "0")
data class ModifierGroupInput(val id: String, val name: String, val minimum: String = "0", val maximum: String = "1", val options: List<ModifierOptionInput> = emptyList())

object ModifierRules {
    fun validate(ownerId: String, groups: List<ModifierGroupInput>, existingIds: Set<String>? = null): List<ModifierGroupInput> {
        fun invalid(message: String): Nothing = throw CatalogFailure("modifier", message)
        if(groups.size > 20 || groups.map { it.id }.distinct().size != groups.size) invalid("Проверьте число и идентификаторы групп")
        val allOptions=mutableSetOf<String>()
        return groups.map { g ->
            if(g.id.isBlank() || g.id.length>100) invalid("Некорректный идентификатор группы")
            val min=g.minimum.toIntOrNull() ?: invalid("Минимум вариантов должен быть целым числом")
            val max=g.maximum.toIntOrNull() ?: invalid("Максимум вариантов должен быть целым числом")
            if(min<0 || max !in 1..100 || min>max || min>g.options.size || g.options.isEmpty() || g.options.size>100)
                invalid("Проверьте минимум, максимум и число вариантов группы")
            if(g.options.map { it.productId }.distinct().size!=g.options.size) invalid("Товар повторяется в группе")
            g.copy(name=CatalogRules.name(g.name,100),minimum=min.toString(),maximum=max.toString(),options=g.options.map { o ->
                if(o.id.isBlank() || o.id.length>100 || !allOptions.add(o.id)) invalid("Повторяющийся или пустой идентификатор варианта")
                if(o.productId.isBlank() || o.productId==ownerId || (existingIds!=null && o.productId !in existingIds))
                    invalid("Выберите существующий товар, отличный от самой карточки")
                val qty=CatalogRules.decimal(o.quantity,12,"Количество модификатора")
                if(qty.toBigDecimal().signum()<=0) invalid("Количество модификатора должно быть больше нуля")
                val raw=o.priceDelta.trim().replace(',', '.')
                val amount=CatalogRules.decimal(raw.removePrefix("-"),2,"Доплата")
                o.copy(posName=o.posName.trim().also { if(it.codePointCount(0,it.length)>200) invalid("Название варианта: до 200 символов") },
                    quantity=qty,priceDelta=if(raw.startsWith('-') && amount!="0") "-$amount" else amount)
            })
        }
    }

    /** For the workspace in stage02. Each option can be selected once; priceDelta is per base item. */
    fun selectionPrice(basePrice: BigDecimal, groups: List<ModifierGroupInput>, selected: Set<String>): BigDecimal {
        val options=groups.flatMap { it.options }
        if(basePrice.signum()<0 || selected.any { id -> options.none { it.id==id } }) throw CatalogFailure("modifier", "Неизвестный вариант или некорректная цена")
        groups.forEach { g ->
            val count=g.options.count { it.id in selected }
            if(count !in g.minimum.toInt()..g.maximum.toInt()) throw CatalogFailure("modifier", "Проверьте выбор группы: ${g.name}")
        }
        val result=options.filter { it.id in selected }.fold(basePrice) { sum,o -> sum+o.priceDelta.toBigDecimal() }
        if(result.signum()<0) throw CatalogFailure("modifier", "Итоговая цена не может быть отрицательной")
        return result
    }
}

/** Configures one base item only. Expansion uses RecipeCalculator; saving a sale is a later stage. */
data class ModifierConfiguration(val price: BigDecimal, val requests: List<RecipeRequest>)
fun configureModifiers(ownerId: String, basePrice: BigDecimal, groups: List<ModifierGroupInput>, selected: Set<String>, products: List<RecipeProduct>): ModifierConfiguration {
    val byId=products.associateBy { it.id }
    val owner=byId[ownerId] ?: throw CatalogFailure("modifier","Основной товар не найден")
    val valid=ModifierRules.validate(ownerId,groups,byId.keys)
    val price=ModifierRules.selectionPrice(basePrice,valid,selected)
    val requests=listOf(RecipeRequest(ownerId,BigDecimal.ONE,owner.unit)) + valid.flatMap { it.options }.filter { it.id in selected }.map {
        RecipeRequest(it.productId,it.quantity.toBigDecimal(),byId.getValue(it.productId).unit)
    }
    return ModifierConfiguration(price,requests)
}
