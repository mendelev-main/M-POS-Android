package com.mendelev.mpos.domain.refund

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.sale.*
import org.junit.Assert.*
import org.junit.Test

class RefundMoneyTest {
    private fun quote(delivery: Boolean = false) = SaleMoney.quote(
        listOf(SaleLineInput("goods", "20", "1")),
        if (delivery) "delivery" else "here",
        if (delivery) DeliveryCharge("courier", "Доставка", 500, 1) else null,
    )
    private fun refused(block: () -> Unit) {
        try { block(); fail("Expected refusal") } catch (_: CatalogFailure) { }
    }

    @Test fun deliveryCardReturnsWholeChequeAndCreditsCourierCash() {
        val q = quote(true)
        val p = SaleMoney.payments(q, listOf(PaymentInput("card", 2500)), 500)
        assertEquals(RefundAmounts(2500, 0, 2500, 500, 500, 500), RefundMoney.plan(q, p, 0))
    }
    @Test fun deliveryCashUsesNetDrawerEffectWithoutRequiringExtraFloat() {
        val q = quote(true)
        val p = SaleMoney.payments(q, listOf(PaymentInput("cash", 2500, 3000)), 0)
        assertEquals(RefundAmounts(2500, 2500, 0, 500, -2000, 0), RefundMoney.plan(q, p, 2000))
        refused { RefundMoney.plan(q, p, 1999) }
    }
    @Test fun mixedDeliveryPreservesOriginalMethods() {
        val q = quote(true)
        val p = SaleMoney.payments(q, listOf(PaymentInput("cash", 1000, 1000), PaymentInput("card", 1500)), 0)
        assertEquals(RefundAmounts(2500, 1000, 1500, 500, -500, 0), RefundMoney.plan(q, p, 500))
    }
    @Test fun mixedRefundNeverReturnsTenderOrChangeTwice() {
        val q = SaleMoney.quote(listOf(SaleLineInput("goods", "100", "1")))
        val p = SaleMoney.payments(q, listOf(PaymentInput("cash", 4000, 5000), PaymentInput("card", 6000)), 0)
        assertEquals(RefundAmounts(10000, 4000, 6000, 0, -4000, 1000), RefundMoney.plan(q, p, 5000))
        refused { RefundMoney.plan(q, p, 3999) }
    }
    @Test fun crossShiftUsesTodaysBalanceAndZeroChequeIsValid() {
        val q = SaleMoney.quote(listOf(SaleLineInput("goods", "10", "1")))
        val p = SaleMoney.payments(q, listOf(PaymentInput("cash", 1000, 1000)), 0)
        assertEquals(1000L, RefundMoney.plan(q, p, 2000).expectedCashAfter)
        val zero = SaleMoney.quote(listOf(SaleLineInput("free", "0", "1")))
        val free = SaleMoney.payments(zero, listOf(PaymentInput("cash", 0, 0)), 0)
        assertEquals(RefundAmounts(0, 0, 0, 0, 0, 0), RefundMoney.plan(zero, free, 0))
    }
    @Test fun corruptReceiptAndOverflowRefuseBeforeProducingPlan() {
        val q = quote(true)
        val p = SaleMoney.payments(q, listOf(PaymentInput("card", 2500)), 500)
        refused { RefundMoney.plan(q, p, -1) }
        refused { RefundMoney.plan(q, p, Long.MAX_VALUE) }
        refused { RefundMoney.plan(q, p.copy(deliveryCashOut = 0), 0) }
        refused { RefundMoney.plan(q.copy(total = 2400), p, 0) }
        refused { RefundMoney.plan(q.copy(subtotal = 1900), p, 0) }
        refused { RefundMoney.plan(q.copy(lines = q.lines + q.lines), p, 0) }
        refused { RefundMoney.plan(q, p.copy(card = -1), 0) }
        refused { RefundMoney.plan(q, p.copy(parts = emptyList()), 0) }
        refused { RefundMoney.plan(q, p.copy(parts = listOf(PaymentAllocation("bank", 2500, null, null))), 0) }
        refused { RefundMoney.plan(q, p.copy(parts = listOf(PaymentAllocation("cash", 2500, 3000, 0))), 0) }
        refused { RefundMoney.plan(q, p.copy(parts = listOf(PaymentAllocation("card", 2500, 2500, null))), 0) }
        refused { RefundMoney.plan(q.copy(lines = listOf(SaleLineMoney("goods", Long.MAX_VALUE, Long.MAX_VALUE, 1))), p, 0) }
    }
}
