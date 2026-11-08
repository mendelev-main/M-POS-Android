package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CartItemOptionsTest {
    private val products=listOf(WorkspaceProduct("p","Чай","","10",1,"piece"))
    private val graph=listOf(RecipeProduct.Simple("p",QuantityUnit.PIECE,BigDecimal.ONE,false,false))
    private val balances=mapOf("p" to BigDecimal(3))
    private fun cart()=CartEngine.apply(CurrentCart(sessionId="cart"),CartAction.Add("p",1,emptySet(),null,"line"),products,graph,balances)
    private fun apply(c: CurrentCart,a: CartAction)=CartEngine.apply(c,a,products,graph,balances)
    private fun refusal(block: ()->Unit) {try {block();fail()} catch(_: CatalogFailure) {}}
    @Test fun quantityCommentAndDiscountAreAppliedTogetherAndRevalidationPreservesThem() {
        val original=cart()
        val discount=SaleDiscount("fixed","1","Сотруднику")
        val next=apply(original,CartAction.SetOptions("line","2"," Без сахара ","staff",discount,0))
        assertEquals("1",original.lines.single().quantity);assertEquals("",original.lines.single().comment)
        val line=next.lines.single()
        assertEquals("Без сахара",line.comment);assertEquals("2",line.quantity);assertTrue(line.discountEdited)
        assertEquals(1800L,SaleMoney.quote(listOf(SaleLineInput(line.id,line.unitPrice,line.quantity,line.discount))).total)
        assertEquals(line,CartRevalidator.preview(next,products,graph,balances,emptyMap()).after.lines.single())
        val cleared=apply(next,CartAction.SetOptions("line","2","","",null,1)).lines.single()
        assertNull(cleared.discount);assertEquals("",cleared.comment);assertTrue(cleared.discountEdited)
    }
    @Test fun shortagesAndInvalidInputCannotPartiallyChangeTheLine() {
        val original=cart()
        refusal {apply(original,CartAction.SetOptions("line","4","Новый комментарий","staff",SaleDiscount("percent","10"),0))}
        refusal {apply(original,CartAction.SetOptions("line","0","","",null,0))}
        refusal {apply(original,CartAction.SetOptions("line","1","x".repeat(1001),"",null,0))}
        refusal {apply(original,CartAction.SetOptions("line","1","","staff",SaleDiscount("percent","101"),0))}
        assertEquals("1",original.lines.single().quantity);assertNull(original.lines.single().discount)
    }
}
