package com.mendelev.mpos.payment

import com.mendelev.mpos.data.MPosJsonNumbers
import kotlin.math.max

/** Reviewed split-cash arithmetic: round tender first, then compare without cash-screen epsilon. */
object MPosCashTender {
    data class Payment(val cashGiven: Double, val change: Double)
    fun parse(raw: String): Double? {
        val value = MPosJsonNumbers.number(raw.replaceFirst(',', '.'))
        return value.takeIf { it.isFinite() && it >= 0 }
    }
    fun preview(amount: Double, given: Double) = max(0.0, given - amount)
    fun confirm(amount: Double, given: Double): Payment? {
        if (!amount.isFinite() || amount <= 0 || !given.isFinite() || given < 0) return null
        val rounded = MPosJsonNumbers.roundMoney(given)
        if (!rounded.isFinite() || rounded < amount) return null
        val change = MPosJsonNumbers.roundMoney(rounded - amount)
        return Payment(rounded, change).takeIf { change.isFinite() }
    }
    fun denominations(amount: Double): List<Double> =
        (listOf(amount) + listOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0).filter { it >= amount })
            .map { MPosJsonNumbers.roundMoney(it) }.distinct()
}
