package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

data class PurchaseProduct(val id: String,val name: String,val revision: Long,val unit: String,val category: String,
    val stock: String,val purchaseUnit: String="",val packSize: String="") {
    fun blankLine()=PurchaseLine(id,revision,name,unit,"",purchaseUnit.ifBlank {unit},packSize,unit)
}
data class PurchaseLine(val productId: String,val productRevision: Long,val productName: String,val stockUnit: String,
    val quantity: String,val unit: String,val packSize: String="",val contentUnit: String=stockUnit)
data class PurchaseDraft(val id: String,val supplierId: String,val supplierRevision: Long,val supplierName: String,val lines: List<PurchaseLine>)
sealed interface PurchaseAction {
    data class Save(val draft: PurchaseDraft): PurchaseAction
    data object Clear: PurchaseAction
}
data class PurchaseCommand(val commandId: String,val actorId: String,val revision: Long,val action: PurchaseAction) {
    fun validate() {
        PurchaseDraftRules.id(commandId);PurchaseDraftRules.id(actorId)
        if(revision<0) throw CatalogFailure("purchase","Некорректная версия черновика")
        if(action is PurchaseAction.Save) PurchaseDraftRules.validate(action.draft)
    }
    fun fingerprint(): String {
        validate()
        val d=(action as? PurchaseAction.Save)?.draft
        val parts=mutableListOf<Any?>("purchase.draft.v1",actorId,revision,if(d==null) "clear" else "save")
        if(d!=null) {
            parts.addAll(listOf(d.id,d.supplierId,d.supplierRevision,d.supplierName))
            d.lines.forEach {parts.addAll(listOf(it.productId,it.productRevision,it.productName,it.stockUnit,it.quantity,it.unit,it.packSize,it.contentUnit))}
        }
        return CatalogRules.fingerprint(*parts.toTypedArray())
    }
}
object PurchaseDraftRules {
    val packs=setOf("pack","box","bottle")
    fun id(value: String) {if(value.isBlank() || value.length>200) throw CatalogFailure("purchase","Некорректный идентификатор закупки")}
    fun number(raw: String): BigDecimal? {
        if(raw.isBlank()) return null
        val s=raw.trim().replace(',','.')
        if(s.length>64 || !s.matches(Regex("[0-9]+(?:\\.[0-9]*)?"))) throw CatalogFailure("quantity","Введите неотрицательное количество")
        return s.toBigDecimal().also {if(it.scale()>12 || it.precision()-it.scale()>18) throw CatalogFailure("precision","Количество вне допустимой точности склада")}
    }
    private fun exactBase(n: BigDecimal): BigDecimal=n.also {
        val c=it.stripTrailingZeros()
        if(c.scale()>12 || c.precision()-c.scale()>18) throw CatalogFailure("precision","Пересчёт вне допустимой точности склада")
    }
    fun expected(line: PurchaseLine): BigDecimal? {
        val base=quantityUnit(line.stockUnit)
        val qty=number(line.quantity)
        number(line.packSize)
        if(line.unit in packs) {
            quantityUnit(line.contentUnit).convert(BigDecimal.ONE,base)
            val size=number(line.packSize)
            if(size!=null && size.signum()<=0) throw CatalogFailure("pack","Количество в единице закупки должно быть больше нуля")
            if(qty==null || qty.signum()==0 || size==null) return null
            return exactBase(quantityUnit(line.contentUnit).convert(qty*size,base))
        }
        val from=quantityUnit(line.unit)
        from.convert(BigDecimal.ONE,base)
        quantityUnit(line.contentUnit).convert(BigDecimal.ONE,base)
        return qty?.takeIf {it.signum()>0}?.let {exactBase(from.convert(it,base))}
    }
    fun validate(d: PurchaseDraft) {
        id(d.id);id(d.supplierId)
        if(d.supplierRevision<1 || CatalogRules.name(d.supplierName)!=d.supplierName) throw CatalogFailure("purchase","Некорректный снимок поставщика")
        if(d.lines.map {it.productId}.distinct().size!=d.lines.size) throw CatalogFailure("purchase","Товар повторяется в черновике")
        d.lines.forEach {l->
            id(l.productId)
            if(l.productRevision<1 || CatalogRules.name(l.productName)!=l.productName) throw CatalogFailure("purchase","Некорректный снимок товара")
            if(l.quantity.length>64 || l.packSize.length>64) throw CatalogFailure("quantity","Слишком длинное количество")
            expected(l)
        }
    }
    fun validateReferences(d: PurchaseDraft,suppliers: List<Supplier>,products: List<PurchaseProduct>) {
        validate(d)
        val supplier=suppliers.find {it.id==d.supplierId && !it.deleted}
        if(supplier==null || supplier.revision!=d.supplierRevision || supplier.name!=d.supplierName)
            throw CatalogFailure("conflict","Поставщик изменился. Обновите ссылки черновика")
        val ps=products.associateBy {it.id}
        d.lines.forEach {l->
            val p=ps[l.productId]
            if(p==null || l.productId !in supplier.productIds || p.revision!=l.productRevision || p.name!=l.productName || p.unit!=l.stockUnit)
                throw CatalogFailure("conflict","Товар «${l.productName}» изменился или больше не закреплён за поставщиком. Обновите ссылки")
        }
    }
    fun revalidate(d: PurchaseDraft,suppliers: List<Supplier>,products: List<PurchaseProduct>): PurchaseDraft {
        val s=suppliers.find {it.id==d.supplierId && !it.deleted} ?: throw CatalogFailure("supplier","Поставщик недоступен")
        val ps=products.associateBy {it.id}
        return d.copy(supplierRevision=s.revision,supplierName=s.name,lines=d.lines.map {l->
            val p=ps[l.productId] ?: throw CatalogFailure("product","Товар «${l.productName}» недоступен. Уберите строку")
            if(p.id !in s.productIds || p.unit!=l.stockUnit) throw CatalogFailure("product","Проверьте привязку и единицу товара «${l.productName}». Уберите строку при необходимости")
            l.copy(productRevision=p.revision,productName=p.name)
        }).also {validateReferences(it,suppliers,products)}
    }
}
fun purchaseUnitLabel(code: String)=when(code) {"pack"->"упаковка";"box"->"коробка";"bottle"->"бутылка";else->unitLabel(code)}
