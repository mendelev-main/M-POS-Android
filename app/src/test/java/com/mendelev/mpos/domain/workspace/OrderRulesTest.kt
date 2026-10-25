package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class OrderRulesTest {
    private fun reject(block: ()->Unit) {try {block();fail()} catch(_:CatalogFailure) {}}
    @Test fun deliveryFieldsAreClearedOnTypeChangeAndTextIsValidated() {
        val delivery=OrderRules.details(OrderDetails("  Стол 1  ","delivery","Без сахара\nС собой","Тестовая 1","000"))
        assertEquals("Стол 1",delivery.label);assertEquals("000",delivery.phone)
        val changed=OrderRules.details(delivery.copy(type="takeaway"))
        assertEquals("",changed.address);assertEquals("",changed.phone);assertEquals(delivery.comment,changed.comment)
        reject {OrderRules.details(delivery.copy(label="x".repeat(81)))}
        reject {OrderRules.details(delivery.copy(phone="000\n123"))}
        reject {OrderRules.details(delivery.copy(type="unexpected"))}
    }
    @Test fun labelsAreUniqueNfcAwareAndSortNaturally() {
        assertEquals("Стол 1 (3)",OrderRules.uniqueLabel("Стол 1",listOf(" стол 1 ","СТОЛ 1 (2)")))
        assertEquals("Стол 1 (3)",OrderRules.uniqueLabel("Стол 1 (2)",listOf("Стол 1","Стол 1 (2)")))
        assertEquals("Й (2)",OrderRules.uniqueLabel("Й",listOf("Й")))
        assertEquals(OrderRules.labelKey("Стол 1"),OrderRules.labelKey("Стол 1"))
        val original="☕".repeat(80);val suffixed=OrderRules.uniqueLabel(original,listOf(original))
        assertEquals(80,suffixed.codePointCount(0,suffixed.length));assertTrue(suffixed.endsWith(" (2)"))
        assertTrue(OrderRules.compareLabels("Стол 2","Стол 10")<0)
        reject {OrderRules.uniqueLabel(" ",emptyList())}
    }
    private val products=listOf(WorkspaceProduct("p","Чай","","2",1,"piece"))
    private val graph=listOf(RecipeProduct.Simple("p",QuantityUnit.PIECE,BigDecimal.ONE))
    private fun cart(id: String,qty: String): CurrentCart {
        val added=CartEngine.apply(CurrentCart(sessionId=id),CartAction.Add("p",1,emptySet(),null,"line-$id"),products,graph,mapOf("p" to BigDecimal.TEN))
        return CartEngine.apply(added,CartAction.SetQuantity(added.lines.single().id,qty,0),products,graph,mapOf("p" to BigDecimal.TEN))
    }
    @Test fun candidateIncludesOtherOrdersButNeverItsOwnOldReserveTwice() {
        val current=cart("current","2");val parked=cart("parked","3")
        val reserve=OrderReservations.total(listOf(parked))
        val same=CartEngine.apply(current,CartAction.SetQuantity(current.lines.single().id,"2",0),products,graph,mapOf("p" to BigDecimal("5")),reserve)
        assertEquals("2",same.lines.single().quantity)
        reject {CartEngine.apply(current,CartAction.Step(current.lines.single().id,1),products,graph,mapOf("p" to BigDecimal("5")),reserve)}
        val decreased=CartEngine.apply(current,CartAction.Step(current.lines.single().id,-1),products,graph,mapOf("p" to BigDecimal.ZERO),reserve)
        assertEquals("1",decreased.lines.single().quantity)
        assertEquals(mapOf("p" to BigDecimal("5")),OrderReservations.total(listOf(current,parked)))
        reject {OrderReservations.total(listOf(current,current))}
        reject {OrderReservations.total(listOf(CurrentCart()))}
    }
    @Test fun flagsDoNotDropTrackedNegativeAllowedReservations() {
        val negative=graph.map {(it as RecipeProduct.Simple).copy(allowNegativeStock=true)}
        val current=CartEngine.apply(CurrentCart(sessionId="a"),CartAction.Add("p",1,emptySet(),null,"a"),products,negative,mapOf("p" to BigDecimal.ZERO),mapOf("p" to BigDecimal.TEN))
        assertEquals(BigDecimal.ONE,OrderReservations.total(listOf(current)).getValue("p"))
        val untracked=graph.map {(it as RecipeProduct.Simple).copy(noStockTracking=true)}
        val other=CartEngine.apply(CurrentCart(sessionId="b"),CartAction.Add("p",1,emptySet(),null,"b"),products,untracked,emptyMap())
        assertTrue(OrderReservations.total(listOf(other)).isEmpty())
    }
}
