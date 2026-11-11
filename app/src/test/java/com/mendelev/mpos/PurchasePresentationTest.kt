package com.mendelev.mpos

import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.purchasing.purchaseOrderViewText
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PurchasePresentationTest {
    @Test fun supplierCopyUsesHistoricalNamesUnitsAndLocalDateWithoutInternalAuditFields() {
        val draft=PurchaseDraft("draft","supplier",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","24","bottle","500","ml")))
        val order=PurchaseOrderRules.create("internal-order-id",draft,Instant.parse("2026-11-10T09:00:00Z").toEpochMilli())
        val text=purchaseOrderViewText(order,ZoneId.of("Europe/Moscow"))
        assertTrue(text.startsWith("Заказ поставщику: Поставка\nДата: 10.11.2026 12:00\n\n"))
        assertTrue(text.contains("1. Молоко — 24 бутылка × 500 мл"))
        assertTrue(text.endsWith("Просьба подтвердить наличие и сроки поставки."))
        assertFalse(text.contains(order.id));assertFalse(text.contains("Версия"));assertFalse(text.contains("UTC"))
        assertEquals(listOf("12"),order.expectedQuantities)
    }
}
