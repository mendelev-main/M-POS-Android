package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*

/** A native executable snapshot. References are actual candidate revisions, never claimed Web revisions. */
internal data class WebAdoptedCart(val cart: CurrentCart,val discounts: Map<String,SaleDiscount>,val delivery: DeliveryCharge?,val quote: SaleQuote)
internal object WebCartAdoption {
    fun map(bound: WebBoundRecipeCart, products: List<ProductEntity>,source: WebBackupDocument): WebAdoptedCart {
        val op=bound.operation
        val graph=bound.graph ?: fail()
        val target=products.associateBy {it.id}
        val nodes=graph.associateBy {it.id}
        val lines=bound.lines.map {l->
            val s=l.source
            val product=WebIdentity.mapped("product",s.sourceProductId ?: fail())
            val requests=l.requestsPerSoldUnit ?: fail()
            val references=linkedMapOf<String,Long>()
            fun visit(id: String) {
                if(id in references)return
                references[id]=target[id]?.revision?.takeIf {it>0} ?: fail()
                when(val node=nodes[id] ?: fail()) {
                    is RecipeProduct.Composite->node.components.forEach {visit(it.productId)}
                    is RecipeProduct.Simple->Unit
                }
            }
            visit(product);requests.forEach {visit(it.productId)}
            val options=s.modifiers.map {m->
                val group=m.groupId ?: fail();val option=m.optionId ?: fail()
                ChosenOption(WebIdentity.mapped("modifierOption",CatalogRules.fingerprint(product,group,option)),
                    m.name ?: fail(),m.quantity ?: fail(),m.priceDelta ?: fail())
            }
            CartLine(CatalogRules.fingerprint("web.cart.line.v1",op.origin,s.position),product,s.name ?: fail(),s.basePrice,s.manualPrice,
                s.price ?: fail(),s.quantity ?: fail(),l.physicalUnit ?: fail(),options,requests,references,l.basis ?: fail())
        }
        val type=when(op.orderType) {"На месте"->"here";"С собой"->"takeaway";"Доставка"->"delivery";else->fail()}
        val raw=if(op.location=="current") WebValues.obj(source.root.fields["currentOrderSession"]) else WebValues.obj(WebValues.rows(source.root.fields["parked"])[op.location.substringAfter(":").toInt()])
        val customer=raw["customer"]?.let(WebValues::obj).orEmpty()
        val cart=CurrentCart(lines,graph,op.origin,op.createdAt ?: fail(),OrderRules.details(OrderDetails(op.label.orEmpty(),type,op.comment.orEmpty(),WebValues.text(customer["address"]),WebValues.text(customer["phone"]))))
        val discounts=bound.lines.mapIndexedNotNull {i,l->l.source.discount?.let {lines[i].id to SaleDiscount(it.type,it.value,it.name ?: "Скидка из Web")}}.toMap()
        // This is an immutable imported tariff snapshot, not a new entry in the tariff directory.
        val delivery=if(type=="delivery")DeliveryCharge(CatalogRules.fingerprint("web.delivery.v1",op.origin),"Доставка из Web",op.delivery ?: fail(),1) else null
        val quote=SaleMoney.quote(lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,discounts[it.id])},type,delivery)
        if(quote.total!=op.comparedTotal || op.savedTotal!=null && quote.total!=op.savedTotal)fail()
        val actual=RecipeCalculator(graph).consumption(CartEngine.requests(cart)).mapValues {CatalogRules.canonical(it.value)}
        if(actual!=bound.nativeConsumption || !WebRecipeBinding.sourceConsumptionMatches(bound.exactSourceConsumption,actual))fail()
        return WebAdoptedCart(cart,discounts,delivery,quote)
    }
    private fun fail(): Nothing=throw BackupFailure("webCartAdoption","Открытый заказ не прошёл сверку цены, единиц и рецептуры. Рабочие данные не изменены")
}
