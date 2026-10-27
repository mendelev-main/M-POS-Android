package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class InventoryConfig(val enabled: Boolean=false,val frequency: String="monthly",val productIds: Set<String> = emptySet(),val lastCompleted: String?=null)
data class InventoryProduct(val id: String,val name: String,val category: String,val unit: String,val revision: Long,val stockRevision: Long,val stock: String,val cost: String)
data class InventoryDraft(val id: String,val type: String,val startedAt: Long,val scheduledDate: String,val products: List<InventoryProduct>)
data class InventoryDocument(val config: InventoryConfig=InventoryConfig(),val draft: InventoryDraft?=null)
sealed interface InventoryAction {
    data class Configure(val config: InventoryConfig): InventoryAction
    data class Start(val id: String,val type: String): InventoryAction
}
data class InventoryCommand(val commandId: String,val actorId: String,val revision: Long,val action: InventoryAction) {
    fun validate() {
        PurchaseDraftRules.id(commandId);PurchaseDraftRules.id(actorId);require(revision>=0)
        when(val a=action) {is InventoryAction.Configure->InventoryRules.validate(a.config);is InventoryAction.Start->{PurchaseDraftRules.id(a.id);require(a.type in setOf("scheduled","adhoc"))}}
    }
    fun fingerprint(): String {
        validate();val p=mutableListOf<Any?>("inventory.v1",actorId,revision)
        when(val a=action) {
            is InventoryAction.Configure->{p.addAll(listOf("configure",a.config.enabled,a.config.frequency,a.config.lastCompleted));p.addAll(a.config.productIds.sorted())}
            is InventoryAction.Start->p.addAll(listOf("start",a.id,a.type))
        }
        return CatalogRules.fingerprint(*p.toTypedArray())
    }
}
object InventoryRules {
    fun validate(c: InventoryConfig) {
        require(c.frequency in setOf("weekly","monthly","quarterly"));c.productIds.forEach(PurchaseDraftRules::id)
        if(c.enabled && c.productIds.isEmpty()) throw CatalogFailure("inventory","Выберите хотя бы один товар")
        c.lastCompleted?.let {require(LocalDate.parse(it).toString()==it)}
    }
    fun validate(d: InventoryDocument) {
        validate(d.config)
        d.draft?.let {s->
            PurchaseDraftRules.id(s.id);require(s.startedAt>=0 && s.type in setOf("scheduled","adhoc"));require(LocalDate.parse(s.scheduledDate).toString()==s.scheduledDate)
            require(s.products.isNotEmpty() && s.products.map {it.id}.distinct().size==s.products.size)
            s.products.forEach {p->PurchaseDraftRules.id(p.id);require(p.revision>=1 && p.stockRevision>=1 && CatalogRules.name(p.name)==p.name);quantityUnit(p.unit);ReceivingRules.quantity(p.stock);require(PurchaseDraftRules.number(p.cost)!=null)}
        }
    }
    fun period(date: LocalDate,frequency: String): LocalDate=when(frequency) {
        "weekly"->date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        "monthly"->date.withDayOfMonth(1)
        "quarterly"->LocalDate.of(date.year,((date.monthValue-1)/3)*3+1,1)
        else->throw CatalogFailure("inventory","Неизвестная периодичность")
    }
    fun due(c: InventoryConfig,today: LocalDate): Boolean {
        validate(c);if(!c.enabled || c.productIds.isEmpty()) return false
        val start=period(today,c.frequency)
        return !today.isAfter(start.plusDays(6)) && c.lastCompleted?.let {period(LocalDate.parse(it),c.frequency)==start}!=true
    }
}
