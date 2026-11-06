package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

/** Sale counts/prices stay in source basis; requests are physical quantities per ONE sold source unit. */
data class WebBoundRecipeLine(val source: WebOpenLine,val basis: String?,val physicalUnit: String?,val physicalPerSoldUnit: String?,
    val requestsPerSoldUnit: List<RecipeRequest>?,val nativeConsumption: Map<String,String>?,val costMinor: Long?,val blockers: Set<String>)
data class WebBoundRecipeCart(val origin: String,val location: String,val operation: WebOpenOperation,val lines: List<WebBoundRecipeLine>,val graph: List<RecipeProduct>?,
    val exactSourceConsumption: Map<String,String>?,val nativeConsumption: Map<String,String>?,val costMinor: Long?,val blockers: Set<String>)
data class WebRecipeBindingResult(val sourceSha256: String,val carts: List<WebBoundRecipeCart>,val aggregateConsumption: Map<String,String>?,
    val shortages: List<StockShortage>?,val blockers: Set<String>) {
    override fun toString()="WebRecipeBindingResult(carts=${carts.size}, blockers=${blockers.size})"
}

/** Read-only adapter. No CartEngine.Add/repricing, guessed stock/cost/revisions or financial writes. */
object WebRecipeBinding {
    fun bind(document: WebBackupDocument): WebRecipeBindingResult {
        val catalog=WebCatalogMapping.map(document);val byId=catalog.products.associateBy {it.id}
        val sourceIds=catalog.products.associateBy {it.sourceId}
        val operations=WebOpenOperations.map(document)
        val rawProducts=WebValues.rows(document.root.fields["products"]).map(WebValues::obj).associateBy {WebIdentity.source(it["id"])}
        // Shared recipe DAGs may expand exponentially despite a small file. Bound work before native expansion.
        val complexity=mutableMapOf<String,Long>()
        fun work(id: String): Long=complexity[id] ?: (1L+byId.getValue(id).components.fold(0L) {a,c->(a+work(c.productId)).coerceAtMost(100_001L)}).coerceAtMost(100_001L).also {complexity[id]=it}
        var budget=0L
        operations.flatMap {it.lines}.forEach {l->
            (listOf(l.sourceProductId)+l.modifiers.map {it.sourceProductId}).forEach {id->sourceIds[id]?.let {budget+=work(it.id);if(budget>100_000L)backupBad("recipeBindingLimit")}}
        }
        val allBlockers=linkedSetOf("nativeCartEncodingPending")
        fun reachable(roots: List<String>): List<WebCatalogProduct> {
            val visited=linkedSetOf<String>()
            fun visit(id: String) {if(visited.add(id))byId.getValue(id).components.forEach {visit(it.productId)}}
            roots.forEach(::visit);return visited.map(byId::getValue)
        }
        fun graph(products: List<WebCatalogProduct>): List<RecipeProduct>? {
            if(products.any {it.unit==null || it.type=="simple" && it.cost==null || it.components.any {c->c.unit==null}})return null
            return products.map {p->if(p.type=="simple") RecipeProduct.Simple(p.id,quantityUnit(p.unit!!),p.cost!!.toBigDecimal(),p.noStockTracking,p.allowNegativeStock)
                else RecipeProduct.Composite(p.id,quantityUnit(p.unit!!),p.recipeYield.toBigDecimal(),p.components.map {RecipeComponent(it.productId,it.quantity.toBigDecimal(),quantityUnit(it.unit!!))})}
        }
        // Pinned productIngredients multiplies source batch counts. No per-edge rounding or unit-price conversion.
        fun exact(roots: List<Pair<WebCatalogProduct,BigDecimal>>,includeUntracked: Boolean=false): Map<String,BigDecimal> {
            val result=linkedMapOf<String,BigDecimal>()
            fun visit(p: WebCatalogProduct,count: BigDecimal) {
                if(p.type=="simple") {if(includeUntracked || !p.noStockTracking)result[p.id]=(result[p.id] ?: BigDecimal.ZERO)+count}
                else WebValues.rows(rawProducts.getValue(p.sourceId)["components"]).forEach {value->
                    val c=WebValues.obj(value);val child=sourceIds.getValue(WebIdentity.source(c["productId"]))
                    visit(child,count*WebValues.decimal(c["qty"]))
                }
            }
            roots.forEach {(p,q)->visit(p,q)};return result
        }
        val carts=operations.map {op->
            val b=op.blockers.toMutableSet();b.add("nativeCartEncodingPending")
            if(op.payment!=null)b.add("sourceRecipeAtExportOnly")
            val roots=mutableListOf<Pair<WebCatalogProduct,BigDecimal>>()
            val allGraph=linkedMapOf<String,RecipeProduct>()
            var complete=op.lines.isNotEmpty()
            val lines=op.lines.map {line->
                val gaps=line.blockers.toMutableSet()
                val p=sourceIds[line.sourceProductId]
                if(line.price!=null && line.price.toBigDecimal().stripTrailingZeros().scale()>2)gaps.add("nativeSalePricePrecisionUnsupported")
                if(p?.type=="simple" && line.unit!=null && line.unit!=p.unit)gaps.add("sourceLineUnitDiffersFromStock")
                val selected=mutableSetOf<String>()
                val lineRoots=mutableListOf<Pair<WebCatalogProduct,BigDecimal>>()
                if(p==null)gaps.add("soldProductUnavailable") else lineRoots.add(p to BigDecimal.ONE)
                if(p!=null)for(m in line.modifiers) {
                    val child=sourceIds[m.sourceProductId]
                    if(child==null || m.quantity==null)gaps.add("modifierProductOrQuantityUnavailable") else lineRoots.add(child to m.quantity.toBigDecimal())
                    val groupId=m.groupId?.let {WebIdentity.mapped("modifierGroup",p.id+":"+it)}
                    val optionId=if(m.groupId!=null && m.optionId!=null)WebIdentity.mapped("modifierOption",CatalogRules.fingerprint(p.id,m.groupId,m.optionId)) else null
                    val group=p.modifierGroups.find {it.id==groupId};val option=group?.options?.find {it.id==optionId}
                    if(option==null)gaps.add("modifierDefinitionUnavailable") else {
                        selected.add(option.id)
                        val physical=child?.let {it.recipeYield.toBigDecimal()* (m.quantity?.toBigDecimal() ?: BigDecimal.ZERO)}
                        if(child?.id!=option.productId || physical==null || physical.compareTo(option.quantity.toBigDecimal())!=0 || m.priceDelta?.toBigDecimal()?.compareTo(option.priceDelta.toBigDecimal())!=0)
                            gaps.add("modifierDefinitionChanged")
                    }
                }
                if(p!=null && p.modifierGroups.any {g->val n=g.options.count {it.id in selected};n<g.minimum.toInt() || n>g.maximum.toInt()})gaps.add("modifierSelectionOutOfBounds")
                val rootsKnown=p!=null && line.quantity!=null && line.modifiers.all {it.sourceProductId in sourceIds && it.quantity!=null}
                val products=reachable(lineRoots.map {it.first.id})
                products.forEach {gaps.addAll(it.blockers)}
                val nodes=graph(products)
                if(nodes==null)gaps.add("recipeUnitOrCostUnknown")
                val requests=if(rootsKnown && products.all {it.unit!=null})lineRoots.map {(product,count)->
                    RecipeRequest(product.id,count*product.recipeYield.toBigDecimal(),quantityUnit(product.unit!!))
                } else null
                var consumption: Map<String,String>?=null;var cost: Long?=null
                if(rootsKnown) {
                    val q=line.quantity!!.toBigDecimal();lineRoots.forEach {(product,count)->roots.add(product to count*q)}
                    if(q.stripTrailingZeros().scale()>9)gaps.add("nativeSaleQuantityPrecisionUnsupported")
                    val expected=exact(lineRoots.map {(product,count)->product to count*q})
                    if(nodes!=null && requests!=null)try {
                        val calculator=RecipeCalculator(nodes);val total=requests.map {it.copy(quantity=it.quantity*q)}
                        val calculated=calculator.consumption(total)
                        if(same(expected,calculated))consumption=strings(calculated) else gaps.add("stockPrecisionMismatch")
                        val nativeCost=calculator.costMinor(total).longValueExact()
                        val sourceCost=exact(lineRoots.map {(product,count)->product to count*q},true).entries.fold(BigDecimal.ZERO) {a,(id,count)->a+count*byId.getValue(id).cost!!.toBigDecimal()}
                            .setScale(2,java.math.RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                        if(nativeCost==sourceCost)cost=nativeCost else gaps.add("recipeCostMismatch")
                    } catch(e:CatalogFailure) {gaps.add("recipeCalculation:${e.kind}")} catch(_:ArithmeticException) {gaps.add("recipeCalculationOverflow")}
                }
                if(nodes!=null)nodes.forEach {allGraph[it.id]=it}
                if(consumption==null)complete=false
                b.addAll(gaps)
                WebBoundRecipeLine(line,p?.let {if(it.type=="composite")"recipe-batch" else "stock-unit"},p?.unit,p?.recipeYield,
                    requests,consumption,cost,gaps.toSet())
            }
            val sourceComplete=lines.isNotEmpty() && lines.all {it.requestsPerSoldUnit!=null}
            val expected=if(sourceComplete)strings(exact(roots)) else null
            val consumption=if(complete)merge(lines.map {it.nativeConsumption!!}) else null
            val cost=if(lines.isNotEmpty() && lines.all {it.costMinor!=null})try {lines.fold(0L) {a,l->Math.addExact(a,l.costMinor!!)}} catch(_:ArithmeticException) {b.add("recipeCalculationOverflow");null} else null
            if(!sourceComplete || !complete)b.add("recipeBindingIncomplete")
            WebBoundRecipeCart(op.origin,op.location,op,lines,if(complete)allGraph.values.toList() else null,expected,consumption,cost,b.toSet())
        }
        val total=if(carts.all {it.nativeConsumption!=null})merge(carts.map {it.nativeConsumption!!}) else null
        var shortages: List<StockShortage>?=null
        if(total==null)allBlockers.add("aggregateReservationUnknown") else {
            val tracked=total.keys.map(byId::getValue)
            if(tracked.any {it.stock==null})allBlockers.add("stockBalanceUnknown") else shortages=tracked.mapNotNull {p->
                val required=total.getValue(p.id).toBigDecimal();val available=p.stock!!.toBigDecimal()
                if(!p.allowNegativeStock && required>available)StockShortage(p.id,required,available) else null
            }
        }
        if(!shortages.isNullOrEmpty())allBlockers.add("stockShortageIncludingParked")
        return WebRecipeBindingResult(document.preview.sha256,carts,total,shortages,allBlockers.toSet())
    }
    private fun same(a: Map<String,BigDecimal>,b: Map<String,BigDecimal>)=a.keys==b.keys && a.all {(id,v)->v.compareTo(b.getValue(id))==0}
    private fun strings(values: Map<String,BigDecimal>)=values.mapValues {CatalogRules.canonical(it.value)}
    private fun merge(values: List<Map<String,String>>): Map<String,String> {
        val sums=linkedMapOf<String,BigDecimal>();values.forEach {it.forEach {(id,q)->sums[id]=(sums[id] ?: BigDecimal.ZERO)+q.toBigDecimal()}};return strings(sums)
    }
}
