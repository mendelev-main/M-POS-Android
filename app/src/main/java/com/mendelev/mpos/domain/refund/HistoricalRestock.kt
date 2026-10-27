package com.mendelev.mpos.domain.refund

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

/** Candidate only: the caller must validate and write every change in one receipt-linked transaction. */
data class RestockBalance(val quantity: String,val revision: Long)
data class RestockChange(val productId: String,val delta: String,val before: String,val after: String,val expectedRevision: Long,val nextRevision: Long)
object HistoricalRestock {
    private fun invalid(message: String): Nothing=throw CatalogFailure("refund",message)
    private fun quantity(raw: String,positive: Boolean=false): BigDecimal {
        if(raw.length>128 || !raw.matches(Regex("-?[0-9]+(?:\\.[0-9]{1,12})?"))) invalid("Некорректное количество возврата или остатка")
        val value=raw.toBigDecimal()
        if(positive && (value.signum()<=0 || value.precision()-value.scale()>18)) invalid("Некорректный сохранённый расход чека")
        return value
    }
    fun plan(consumption: Map<String,String>,sold: Collection<RecipeProduct>,current: Collection<RecipeProduct>,balances: Map<String,RestockBalance>): List<RestockChange> {
        val historical=sold.associateBy {it.id};val actual=current.associateBy {it.id}
        if(historical.size!=sold.size || actual.size!=current.size) invalid("Повторные товары в снимке возврата")
        return consumption.toSortedMap().map {(id,raw)->
            if(id.isBlank()) invalid("Не указан исходный товар возврата")
            val old=historical[id] as? RecipeProduct.Simple ?: invalid("Нет исходного складского товара в чеке")
            val product=actual[id] as? RecipeProduct.Simple ?: invalid("Не найден исходный складской товар")
            if(old.unit!=product.unit) invalid("Базовая единица исходного товара изменилась")
            val balance=balances[id] ?: invalid("Не найден остаток исходного товара")
            if(balance.revision<0 || balance.revision==Long.MAX_VALUE) invalid("Некорректная ревизия остатка")
            val delta=quantity(raw,true);val before=quantity(balance.quantity);val after=before+delta
            val result=CatalogRules.canonical(after)
            if(result.length>128) invalid("Остаток возврата слишком велик")
            // Current recipe, display unit, tracking and negative-stock flags never recalculate historical consumption.
            RestockChange(id,CatalogRules.canonical(delta),CatalogRules.canonical(before),result,balance.revision,balance.revision+1)
        }
    }
}
