package com.mendelev.mpos.domain.refund

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.sale.SalePayment
import com.mendelev.mpos.domain.sale.SaleQuote

/** Minor BYN units. Courier reversal is a cash credit, independent of customer payment method. */
data class RefundAmounts(
    val total: Long,
    val cash: Long,
    val card: Long,
    val deliveryCashCredit: Long,
    val cashDelta: Long,
    val expectedCashAfter: Long,
)

object RefundMoney {
    private fun invalid(message: String): Nothing = throw CatalogFailure("refund", message)
    private fun add(a: Long, b: Long): Long = try { Math.addExact(a, b) }
        catch (_: ArithmeticException) { invalid("Сумма возврата слишком велика") }

    /** Uses the immutable receipt only; never reprices current goods or delivery tariffs. */
    fun plan(quote: SaleQuote, payment: SalePayment, currentCash: Long): RefundAmounts {
        if (currentCash < 0 || quote.total < 0 || quote.subtotal < 0 || quote.gross < 0 ||
            quote.discount < 0 || payment.cash < 0 || payment.card < 0 ||
            payment.deliveryCashOut < 0 || payment.expectedCashAfter < 0) invalid("Повреждены суммы чека")
        if (quote.lines.isEmpty() || quote.lines.size > 500 ||
            quote.lines.any { it.id.isBlank() || it.gross < 0 || it.net < 0 || it.discount < 0 ||
                add(it.net, it.discount) != it.gross } ||
            quote.lines.map { it.id }.distinct().size != quote.lines.size) invalid("Повреждены строки чека")
        if (quote.lines.fold(0L) { sum, line -> add(sum, line.gross) } != quote.gross ||
            quote.lines.fold(0L) { sum, line -> add(sum, line.net) } != quote.subtotal ||
            quote.lines.fold(0L) { sum, line -> add(sum, line.discount) } != quote.discount)
            invalid("Суммы строк не совпадают с чеком")
        val fee = quote.delivery?.fee ?: 0L
        if (fee < 0 || payment.deliveryCashOut != fee || add(quote.subtotal, fee) != quote.total)
            invalid("Повреждён расход доставки")
        if (payment.parts.isEmpty() || payment.parts.size > 10) invalid("Повреждена оплата чека")
        var cash = 0L
        var card = 0L
        payment.parts.forEach { part ->
            if (part.amount < 0) invalid("Повреждена сумма оплаты")
            when (part.method) {
                "cash" -> {
                    val given = part.given ?: invalid("Нет принятой суммы")
                    if (given < part.amount || part.change != given - part.amount) invalid("Повреждена сдача")
                    cash = add(cash, part.amount)
                }
                "card" -> {
                    if (part.given != null || part.change != null) invalid("Повреждена оплата картой")
                    card = add(card, part.amount)
                }
                else -> invalid("Неизвестный способ оплаты")
            }
        }
        if (cash != payment.cash || card != payment.card || add(cash, card) != quote.total)
            invalid("Оплата не совпадает с чеком")
        // Both operands are nonnegative Longs: their signed difference cannot overflow.
        val delta = fee - cash
        val after = add(currentCash, delta)
        if (after < 0) invalid("Недостаточно наличных для возврата")
        return RefundAmounts(quote.total, cash, card, fee, delta, after)
    }
}
