package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Economic baseline used only for a draft preview. A future posting must read it again. */
data class ReceivingProduct(val id: String,val name: String,val unit: String,val revision: Long,val stockRevision: Long,val stock: String,val cost: String,val noStockTracking: Boolean)
data class ReceivingLine(val product: ReceivingProduct,val quantity: String,val unit: String,val packSize: String,val contentUnit: String,val total: String="",val priceMode: String="total",val unitPrice: String="") {
    fun purchaseLine()=PurchaseLine(product.id,product.revision,product.name,product.unit,quantity,unit,packSize,contentUnit)
}
data class ReceivingSupplier(val id: String,val name: String,val revision: Long)
data class ReceivingDraft(val id: String,val order: PurchaseOrder?,val invoiceNumber: String="",val invoiceDate: String="",val lines: List<ReceivingLine>,val supplier: ReceivingSupplier?=null) {
    val key get()=order?.id ?: ReceivingRules.STANDALONE
    val supplierName get()=order?.source?.supplierName ?: supplier?.name ?: "Без поставщика"
}
data class ReceivingRecord(val orderId: String,val revision: Long,val draft: ReceivingDraft?)
data class ReceivingCommand(val commandId: String,val actorId: String,val orderId: String,val revision: Long,val draft: ReceivingDraft?) {
    fun validate() {
        listOf(commandId,actorId,orderId).forEach(PurchaseDraftRules::id)
        if(revision<0) throw CatalogFailure("receiving","Некорректная версия черновика")
        draft?.let {ReceivingRules.validate(it);if(it.key!=orderId) throw CatalogFailure("receiving","Неверная ссылка заказа")}
    }
    fun fingerprint(): String {
        validate();val d=draft
        val extended=d?.lines?.any {it.priceMode!="total" || it.unitPrice.isNotEmpty()}==true
        val parts=mutableListOf<Any?>(if(extended) "receiving.draft.v2" else "receiving.draft.v1",actorId,orderId,revision,d!=null)
        if(d!=null) {
            val order=d.order
            if(order!=null) {
                parts.addAll(listOf(d.id,order.revision,order.requestVersion,d.invoiceNumber,d.invoiceDate))
                fun source(s: PurchaseDraft) {parts.add(PurchaseCommand("payload","payload",0,PurchaseAction.Save(s)).fingerprint())}
                source(order.source);parts.addAll(listOf(order.createdAt,order.status,order.updatedAt,order.cancelledAt,order.cancelledBy))
                order.history.forEach {source(it.source);parts.addAll(listOf(it.requestVersion,it.at))}
            } else parts.addAll(listOf("standalone.v1",d.id,d.invoiceNumber,d.invoiceDate,d.supplier?.id,d.supplier?.name,d.supplier?.revision))
            d.lines.forEach {l->val p=l.product;parts.addAll(listOf(p.id,p.name,p.unit,p.revision,p.stockRevision,p.stock,p.cost,p.noStockTracking,l.quantity,l.unit,l.packSize,l.contentUnit,l.total));if(extended) parts.addAll(listOf(l.priceMode,l.unitPrice))}
        }
        return CatalogRules.fingerprint(*parts.toTypedArray())
    }
}
data class ReceivingPreviewLine(val productId: String,val name: String,val actual: String,val expected: String?,val shortage: String?,val overage: String?,val oldStock: String,val newStock: String,val oldCost: String,val newCost: String,val noStockTracking: Boolean,val totalMinor: Long)
data class ReceivingPreview(val lines: List<ReceivingPreviewLine>,val totalMinor: Long) {
    val hasOverage get()=lines.any {it.overage?.toBigDecimal()?.signum()==1}
}
object ReceivingRules {
    const val STANDALONE="receiving:standalone"
    fun quantity(raw: String): BigDecimal {
        val n=raw.toBigDecimalOrNull() ?: throw CatalogFailure("receiving","Повреждён складской baseline")
        val canonical=CatalogRules.canonical(n.abs());PurchaseDraftRules.number(canonical)
        return n
    }
    fun money(raw: String): Long? {
        val n=PurchaseDraftRules.number(raw) ?: return null
        if(n.scale()>2) throw CatalogFailure("receiving","Сумма строки должна быть указана до копейки")
        return try {n.movePointRight(2).longValueExact()} catch(_:ArithmeticException) {throw CatalogFailure("receiving","Сумма строки слишком велика")}
    }
    fun lineTotal(l: ReceivingLine): Long? {
        if(l.priceMode=="total") return money(l.total)
        if(l.priceMode!="price") throw CatalogFailure("receiving","Неизвестный режим суммы")
        val q=PurchaseDraftRules.number(l.quantity);val price=PurchaseDraftRules.number(l.unitPrice)
        if(q?.signum()==0) return 0L
        if(q==null || price==null) return null
        return money((q*price).setScale(2,RoundingMode.HALF_UP).toPlainString())
    }
    fun priceMode(l: ReceivingLine,next: String): ReceivingLine {
        if(next !in setOf("total","price")) throw CatalogFailure("receiving","Неизвестный режим суммы")
        if(next==l.priceMode) return l
        if(next=="price") return l.copy(priceMode=next,unitPrice="",total="")
        val amount=lineTotal(l)?.let {BigDecimal.valueOf(it,2).toPlainString()} ?: ""
        return l.copy(priceMode=next,total=amount,unitPrice="")
    }
    fun validate(d: ReceivingDraft) {
        PurchaseDraftRules.id(d.id);val order=d.order
        if(order!=null) {PurchaseOrderRules.validate(order);require(order.id!=STANDALONE && d.supplier==null)}
        d.supplier?.let {PurchaseDraftRules.id(it.id);require(it.revision>=1 && CatalogRules.name(it.name)==it.name)}
        if(order!=null && order.status!="pending") throw CatalogFailure("receiving","Основание черновика должно быть ожидающим заказом")
        if(d.invoiceNumber.length>120 || d.invoiceNumber.any {it.isISOControl()}) throw CatalogFailure("receiving","Номер накладной: до 120 символов")
        if(d.invoiceDate.isNotEmpty()) try {require(d.invoiceDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")));require(LocalDate.parse(d.invoiceDate).year>=1)} catch(_:Exception) {throw CatalogFailure("receiving","Дата накладной: ГГГГ-ММ-ДД")}
        if((order!=null && d.lines.isEmpty()) || d.lines.map {it.product.id}.distinct().size!=d.lines.size) throw CatalogFailure("receiving","Проверьте строки приёмки")
        val ids=order?.let {(it.source.lines+it.history.flatMap {h->h.source.lines}).map {it.productId}.toSet()}
        if(order!=null && (!ids!!.containsAll(d.lines.map {it.product.id}) || !d.lines.map {it.product.id}.containsAll(order.source.lines.map {it.productId}))) throw CatalogFailure("receiving","Нельзя заменить или убрать строки заказа: для недопоставки укажите 0")
        d.lines.forEach {l->
            val p=l.product;PurchaseDraftRules.id(p.id)
            if(p.revision<1 || p.stockRevision<1 || CatalogRules.name(p.name)!=p.name) throw CatalogFailure("receiving","Повреждён снимок товара")
            quantity(p.stock);val cost=PurchaseDraftRules.number(p.cost) ?: throw CatalogFailure("receiving","Не указана себестоимость baseline")
            if(cost.signum()<0) throw CatalogFailure("receiving","Некорректная себестоимость")
            if(l.quantity.length>64 || l.packSize.length>64 || l.total.length>64 || l.unitPrice.length>64) throw CatalogFailure("receiving","Слишком длинное число")
            PurchaseDraftRules.expected(l.purchaseLine())
            if(l.priceMode !in setOf("total","price")) throw CatalogFailure("receiving","Неизвестный режим суммы")
            if(l.priceMode=="price" && l.total.isNotEmpty()) throw CatalogFailure("receiving","В режиме цены сумма вычисляется автоматически")
            if(l.priceMode=="total" && l.unitPrice.isNotEmpty()) throw CatalogFailure("receiving","Неверная цена в режиме суммы")
            val qty=PurchaseDraftRules.number(l.quantity);val total=lineTotal(l)
            if(qty?.signum()==0 && total!=null && total!=0L) throw CatalogFailure("receiving","При нулевом количестве сумма должна быть 0")
        }
    }
    fun open(id: String,o: PurchaseOrder,products: List<ReceivingProduct>): ReceivingDraft {
        if(o.status!="pending" || o.id==STANDALONE) throw CatalogFailure("receiving","Заказ недоступен для приёмки")
        return ReceivingDraft(id,o,lines=o.source.lines.mapIndexed {i,l->
            val p=products.find {it.id==l.productId && it.unit==l.stockUnit} ?: throw CatalogFailure("receiving","Товар «${l.productName}» недоступен")
            ReceivingLine(p,if(o.expectedQuantities[i]==null) "" else l.quantity,l.unit,l.packSize,l.contentUnit)
        }).also(::validate)
    }
    fun rebase(d: ReceivingDraft,o: PurchaseOrder?,products: List<ReceivingProduct>): ReceivingDraft {
        if(o?.id!=d.order?.id || (o!=null && o.status!="pending")) throw CatalogFailure("receiving","Заказ уже не ожидает приёмку")
        val existing=d.lines.map {l->
            val p=products.find {it.id==l.product.id && it.unit==l.product.unit} ?: throw CatalogFailure("receiving","Товар «${l.product.name}» недоступен или изменилась базовая единица")
            l.copy(product=p)
        }
        val additions=(o?.let {open(d.id,it,products).lines} ?: emptyList()).filter {l->existing.none {it.product.id==l.product.id}}.map {it.copy(quantity="",total="")}
        return d.copy(order=o,lines=existing+additions).also(::validate)
    }
    fun switchUnit(l: ReceivingLine,next: String): ReceivingLine {
        if(next==l.unit) return l
        if(l.unit in PurchaseDraftRules.packs || next in PurchaseDraftRules.packs) return l.copy(unit=next,unitPrice=if(l.priceMode=="price") "" else l.unitPrice)
        val from=quantityUnit(l.unit);val to=quantityUnit(next)
        from.convert(BigDecimal.ONE,to)
        val q=PurchaseDraftRules.number(l.quantity)
        val raw=q?.let {CatalogRules.canonical(from.convert(it,to))} ?: ""
        PurchaseDraftRules.number(raw)
        val price=PurchaseDraftRules.number(l.unitPrice)?.let {CatalogRules.canonical(it.divide(from.convert(BigDecimal.ONE,to)))} ?: ""
        PurchaseDraftRules.number(price)
        return l.copy(quantity=raw,unit=next,unitPrice=price)
    }
    fun current(d: ReceivingDraft,o: PurchaseOrder?,products: List<ReceivingProduct>) {
        if((o!=null && o.status!="pending") || o!=d.order) throw CatalogFailure("conflict","Заказ изменился. Обновите основание черновика; введённые факты сохранятся")
        d.lines.forEach {l->if(products.find {it.id==l.product.id}!=l.product) throw CatalogFailure("conflict","Остаток или карточка «${l.product.name}» изменились. Обновите основание перед расчётом")}
    }
    fun preview(d: ReceivingDraft,o: PurchaseOrder?,products: List<ReceivingProduct>,costScale: Int=12,costRounding: RoundingMode=RoundingMode.HALF_EVEN): ReceivingPreview {
        validate(d);current(d,o,products)
        var sum=0L
        val expected=o?.source?.lines?.mapIndexed {i,l->l.productId to o.expectedQuantities[i]}?.toMap().orEmpty()
        val lines=d.lines.map {l->
            val qty=PurchaseDraftRules.number(l.quantity) ?: throw CatalogFailure("receiving","Заполните количество каждой строки")
            val base=if(qty.signum()==0) BigDecimal.ZERO else PurchaseDraftRules.expected(l.purchaseLine()) ?: throw CatalogFailure("receiving","Укажите количество внутри упаковки «${l.product.name}»")
            val total=lineTotal(l) ?: if(base.signum()==0) 0L else throw CatalogFailure("receiving","Заполните сумму каждой полученной строки")
            if(base.signum()==0 && total!=0L) throw CatalogFailure("receiving","При нулевом количестве сумма должна быть 0")
            sum=try {Math.addExact(sum,total)} catch(_:ArithmeticException) {throw CatalogFailure("receiving","Итог слишком велик")}
            val p=l.product;val stock=quantity(p.stock);val oldCost=p.cost.toBigDecimal();val value=BigDecimal.valueOf(total,2)
            val nextStock=if(p.noStockTracking) stock else stock+base;quantity(CatalogRules.canonical(nextStock))
            val oldQty=stock.max(BigDecimal.ZERO)
            val cost=if(base.signum()==0) oldCost else if(p.noStockTracking) value.divide(base,costScale,costRounding) else (oldQty*oldCost+value).divide(oldQty+base,costScale,costRounding)
            PurchaseDraftRules.number(CatalogRules.canonical(cost))
            val exp=if(o==null) null else if(expected.containsKey(p.id)) expected[p.id] else "0"
            val shortage=exp?.toBigDecimal()?.minus(base)?.max(BigDecimal.ZERO)?.let(CatalogRules::canonical)
            val overage=exp?.toBigDecimal()?.let {base-it}?.max(BigDecimal.ZERO)?.let(CatalogRules::canonical)
            ReceivingPreviewLine(p.id,p.name,CatalogRules.canonical(base),exp,shortage,overage,p.stock,CatalogRules.canonical(nextStock),p.cost,CatalogRules.canonical(cost),p.noStockTracking,total)
        }
        if(lines.none {it.actual.toBigDecimal().signum()>0}) throw CatalogFailure("receiving","Нет полученных товаров. Можно сохранить черновик")
        return ReceivingPreview(lines,sum)
    }
}
