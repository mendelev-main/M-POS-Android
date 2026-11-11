package com.mendelev.mpos.domain.loyalty

import com.mendelev.mpos.domain.backup.WebIdentity
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.CurrentCart
import java.math.BigDecimal
import java.math.RoundingMode

data class LoyaltyCustomer(val id: String,val name: String,val phone: String,val username: String="",val active: Boolean=false,val consentVersion: Long?=null)
data class LoyaltyProgram(val id: String,val name: String,val required: Int,val earning: List<String>,val reward: List<String>,
    val active: Boolean=true,val progress: String="0",val rewards: Int=0)
data class LoyaltySelection(val target: String,val customer: LoyaltyCustomer,val programs: List<LoyaltyProgram>,val selected: Set<String> = emptySet(),val verified: String="",val datasetId: String="")
data class LoyaltyGift(val programId: String,val programName: String,val lineId: String,val productId: String,val discount: Long)
data class LoyaltyAllocation(val gifts: List<LoyaltyGift>,val byLine: Map<String,Long>)

object LoyaltyRules {
    fun valid(selection: LoyaltySelection): LoyaltySelection {
        fun id(value: String)=Regex("[A-Za-z0-9_-]{1,100}").matches(value)
        require(Regex("[0-9a-f]{64}").matches(selection.target)) {"Не найдено подключение клиента"}
        require(selection.datasetId.length<=100 && selection.datasetId.all {it.isLetterOrDigit() || it in "-_"})
        val customer=selection.customer
        require(id(customer.id) && customer.name.length in 1..100 && customer.phone.length<=40 && customer.username.length<=100) {"Проверьте имя и телефон клиента"}
        require((customer.name+customer.phone).none {it.isISOControl()}) {"Проверьте имя и телефон клиента"}
        require(!customer.active || customer.consentVersion!=null && customer.consentVersion>0)
        require(selection.programs.size<=100 && selection.programs.map {it.id}.distinct().size==selection.programs.size)
        selection.programs.forEach {
            require(id(it.id) && it.name.length in 1..200 && it.required in 1..999999 && it.rewards in 0..1)
            require(it.progress.length<=30 && it.progress.toBigDecimal().signum()>=0)
            require(it.earning.size<=10000 && it.reward.size<=10000 && (it.earning+it.reward).all(::id))
        }
        require(selection.selected.all {id->selection.programs.any {it.id==id && it.active && it.rewards>0}})
        require(selection.selected.isEmpty() || customer.active)
        require(selection.verified.isEmpty() || Regex("[0-9a-f]{64}").matches(selection.verified))
        return selection
    }
    fun matches(nativeId: String,externalId: String)=nativeId==externalId || nativeId==WebIdentity.mapped("product",externalId)
    fun externalId(cart: CurrentCart,nativeId: String): String = cart.loyalty?.programs.orEmpty().flatMap {it.earning+it.reward}.distinct().firstOrNull {matches(nativeId,it)} ?: nativeId
    fun fingerprint(cart: CurrentCart,discounts: Map<String,SaleDiscount>): String=CatalogRules.fingerprint(
        cart.sessionId,cart.loyalty?.target,cart.loyalty?.datasetId,cart.loyalty?.customer?.id,cart.loyalty?.customer?.consentVersion?.toString(),
        cart.loyalty?.selected.orEmpty().sorted().joinToString("|"),
        cart.lines.joinToString("|") {l->val d=discounts[l.id];listOf(l.id,l.productId,l.quantity,l.unitPrice,d?.type,d?.value).joinToString(":")})
    fun allocation(cart: CurrentCart,discounts: Map<String,SaleDiscount>): LoyaltyAllocation {
        val selection=cart.loyalty ?: return LoyaltyAllocation(emptyList(),emptyMap())
        valid(selection)
        if(selection.selected.isEmpty()) return LoyaltyAllocation(emptyList(),emptyMap())
        val base=SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,discounts[it.id])})
        val remaining=cart.lines.associate {it.id to it.quantity.toBigDecimal().setScale(0,RoundingMode.DOWN).toBigInteger()}.toMutableMap()
        fun netUnit(index: Int): BigDecimal {
            val line=cart.lines[index];val price=line.unitPrice.toBigDecimal();val discount=discounts[line.id] ?: return price
            val value=discount.value.replace(',','.').toBigDecimal()
            return when(discount.type) {"percent"->price*(BigDecimal.ONE-value.movePointLeft(2));"fixed"->(price-value).max(BigDecimal.ZERO);else->price}
        }
        val chosen=mutableListOf<Triple<LoyaltyProgram,Int,String>>()
        selection.programs.filter {it.id in selection.selected}.forEach {program->
            val index=cart.lines.indices.filter {index->val line=cart.lines[index]
                remaining.getValue(line.id).signum()>0 && netUnit(index).signum()>0 && program.reward.any {matches(line.productId,it)}
            }.minWithOrNull(compareBy<Int> {cart.lines[it].unitPrice.toBigDecimal()}.thenBy {it})
                ?: return@forEach
            val line=cart.lines[index];remaining[line.id]=remaining.getValue(line.id)-java.math.BigInteger.ONE
            chosen+=Triple(program,index,program.reward.first {matches(line.productId,it)})
        }
        val byLine=linkedMapOf<String,Long>();val gifts=mutableListOf<LoyaltyGift>()
        chosen.groupBy {it.second}.forEach {(index,rows)->
            val line=cart.lines[index];val original=base.lines.single {it.id==line.id}.net
            val paidQuantity=line.quantity.toBigDecimal()-BigDecimal(rows.size)
            val gift=original-SaleMoney.minor(netUnit(index)*paidQuantity)
            require(gift in 0..original)
            byLine[line.id]=gift
            rows.forEachIndexed {offset,(program,_,external)->
                val part=gift/rows.size+if(offset<gift%rows.size) 1 else 0
                gifts+=LoyaltyGift(program.id,program.name,line.id,external,part)
            }
        }
        return LoyaltyAllocation(gifts,byLine)
    }
    fun quote(cart: CurrentCart,discounts: Map<String,SaleDiscount>,delivery: DeliveryCharge?=null): SaleQuote {
        val base=SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,discounts[it.id])},cart.details.type,delivery)
        val allocation=allocation(cart,discounts)
        if(allocation.gifts.isEmpty()) return base
        val lines=base.lines.map {line->val gift=allocation.byLine[line.id] ?: 0L;line.copy(discount=Math.addExact(line.discount,gift),net=line.net-gift)}
        val subtotal=lines.fold(0L) {sum,line->Math.addExact(sum,line.net)}
        return base.copy(lines=lines,subtotal=subtotal,discount=base.gross-subtotal,total=Math.addExact(subtotal,delivery?.fee ?: 0L))
    }
    fun requireVerified(cart: CurrentCart,discounts: Map<String,SaleDiscount>) {
        val selection=cart.loyalty ?: return
        if(selection.datasetId.isBlank()) throw CatalogFailure("loyalty","Выберите клиента заново перед оплатой")
        if(allocation(cart,discounts).gifts.isNotEmpty() && selection.verified!=fingerprint(cart,discounts))
            throw CatalogFailure("loyalty","Перед оплатой проверьте доступность подарка на сервере")
    }
}
