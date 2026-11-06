package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*

/** Deleted IDs remain reserved: a stale editor can never target a recreated supplier (ABA). */
data class Supplier(val id: String,val name: String,val revision: Long,val productIds: Set<String>,val deleted: Boolean=false)
sealed interface SupplierAction {
    data class Save(val id: String,val revision: Long?,val name: String,val productIds: Set<String>): SupplierAction
    data class Delete(val id: String,val revision: Long): SupplierAction
}
data class SupplierCommand(val commandId: String,val actorId: String,val action: SupplierAction) {
    fun validated(): SupplierCommand {
        fun identity(value: String) {if(value.isBlank() || value.length>200) throw CatalogFailure("supplier","Некорректный идентификатор поставщика или команды")}
        identity(commandId);identity(actorId)
        val action=when(val a=this.action) {
            is SupplierAction.Save -> {
                identity(a.id);a.productIds.forEach(::identity)
                if(a.revision!=null && a.revision<1) throw CatalogFailure("conflict","Некорректная версия поставщика")
                a.copy(name=CatalogRules.name(a.name).replace(Regex("\\s+")," "),productIds=a.productIds.toSet())
            }
            is SupplierAction.Delete -> {identity(a.id);if(a.revision<1) throw CatalogFailure("conflict","Некорректная версия поставщика");a}
        }
        return copy(action=action)
    }
    fun fingerprint(): String = when(val a=validated().action) {
        is SupplierAction.Save -> CatalogRules.fingerprint("supplier.save.v1",actorId,a.id,a.revision,a.name,*a.productIds.sorted().toTypedArray())
        is SupplierAction.Delete -> CatalogRules.fingerprint("supplier.delete.v1",actorId,a.id,a.revision)
    }
}
object SupplierRules {
    fun apply(records: List<Supplier>,command: SupplierCommand,productTypes: Map<String,String>): List<Supplier> {
        val a=command.validated().action
        val id=when(a) {is SupplierAction.Save->a.id;is SupplierAction.Delete->a.id}
        val old=records.singleOrNull {it.id==id}
        fun conflict(): Nothing=throw CatalogFailure("conflict","Поставщик изменился. Перечитайте карточку перед сохранением")
        val next=when(a) {
            is SupplierAction.Save -> {
                if(old?.revision!=a.revision || old?.deleted==true) conflict()
                if(a.productIds.any {productTypes[it]!="simple"}) throw CatalogFailure("supplierProducts","Выберите существующие простые товары")
                Supplier(a.id,a.name,Math.addExact(old?.revision ?: 0L,1),a.productIds.toSet())
            }
            is SupplierAction.Delete -> {
                if(old==null || old.deleted || old.revision!=a.revision) conflict()
                old.copy(revision=Math.addExact(old.revision,1),deleted=true,productIds=emptySet())
            }
        }
        return records.filterNot {it.id==id}+next
    }
}
