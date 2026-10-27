package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

object InventoryCodec {
    const val KEY="inventory"
    fun encode(d: InventoryDocument): String {
        val c=d.config
        return JSONObject().put("version",1).put("config",JSONObject().put("enabled",c.enabled).put("frequency",c.frequency).put("products",JSONArray(c.productIds.sorted())).put("lastCompleted",c.lastCompleted ?: JSONObject.NULL))
            .put("draft",d.draft?.let {s->JSONObject().put("id",s.id).put("type",s.type).put("startedAt",s.startedAt).put("scheduledDate",s.scheduledDate).put("products",JSONArray(s.products.map {p->JSONObject().put("id",p.id).put("name",p.name).put("category",p.category).put("unit",p.unit).put("revision",p.revision).put("stockRevision",p.stockRevision).put("stock",p.stock).put("cost",p.cost)}))} ?: JSONObject.NULL).toString()
    }
    fun decode(raw: String): InventoryDocument=try {
        val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString()=="1");val c=j.getJSONObject("config");require(c.get("enabled") is Boolean && c.has("lastCompleted") && j.has("draft"))
        val ids=c.getJSONArray("products");val selected=(0 until ids.length()).map {ids.getString(it)};require(selected.distinct().size==selected.size)
        fun long(o: JSONObject,k: String): Long {require(o.get(k) is Number);return o.get(k).toString().toLong()}
        InventoryDocument(InventoryConfig(c.getBoolean("enabled"),c.getString("frequency"),selected.toSet(),if(c.isNull("lastCompleted")) null else c.getString("lastCompleted")),if(j.isNull("draft")) null else j.getJSONObject("draft").let {s->val a=s.getJSONArray("products")
            InventoryDraft(s.getString("id"),s.getString("type"),long(s,"startedAt"),s.getString("scheduledDate"),(0 until a.length()).map {i->val p=a.getJSONObject(i);InventoryProduct(p.getString("id"),p.getString("name"),p.getString("category"),p.getString("unit"),long(p,"revision"),long(p,"stockRevision"),p.getString("stock"),p.getString("cost"))})
        }).also(InventoryRules::validate)
    } catch(_:Exception) {throw CatalogFailure("inventoryFormat","Не удалось прочитать инвентаризацию. Данные не сброшены")}
}
data class InventorySnapshot(val ownerId: String,val revision: Long,val document: InventoryDocument,val products: List<InventoryProduct>,val today: String)
interface InventoryAccess {
    suspend fun snapshot(): InventorySnapshot
    suspend fun commit(c: InventoryCommand): String
    suspend fun outcome(c: InventoryCommand): String?
}
class InventoryRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemDefaultZone(),private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null): InventoryAccess {
    private suspend fun products(): List<InventoryProduct> {
        val categories=db.catalog().categorySnapshot().associate {it.id to it.name}
        return db.catalog().allProducts().filter {it.type=="simple" && !it.noStockTracking}.map {p->val b=db.catalog().balance(p.id) ?: error("Нет остатка")
            InventoryProduct(p.id,p.name,categories[p.categoryId] ?: "Без категории",p.unit,p.revision,b.revision,b.quantity,p.cost)
        }.sortedWith(compareBy({CatalogRules.key(it.category)},{CatalogRules.key(it.name)},{it.id}))
    }
    override suspend fun snapshot(): InventorySnapshot {
        InstallationGate.write {db.withTransaction {CatalogRepository(db).initialize()}}
        return db.withTransaction {val doc=db.workspace().document(InventoryCodec.KEY)
            InventorySnapshot(db.catalog().config()!!.ownerId,doc?.revision ?: 0,doc?.let {InventoryCodec.decode(it.payload)} ?: InventoryDocument(),products(),LocalDate.now(clock).toString())
        }
    }
    override suspend fun outcome(c: InventoryCommand): String? {
        c.validate();return db.catalog().result(c.commandId)?.let {if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения");it.resultId}
    }
    override suspend fun commit(c: InventoryCommand): String {
        val c=c.copy(action=when(val a=c.action) {is InventoryAction.Configure->a.copy(config=a.config.copy(productIds=a.config.productIds.toSet()));is InventoryAction.Start->a})
        c.validate()
        val id=InstallationGate.write {db.withTransaction {
            if(db.catalog().config()?.ownerId!=c.actorId) throw CatalogFailure("owner","Изменять инвентаризацию может только локальный владелец")
            outcome(c)?.let {return@withTransaction it}
            val doc=db.workspace().document(InventoryCodec.KEY) ?: WorkspaceDocument(InventoryCodec.KEY,0,InventoryCodec.encode(InventoryDocument()))
            if(doc.revision!=c.revision) throw CatalogFailure("conflict","Инвентаризация изменилась. Перечитайте данные")
            val old=InventoryCodec.decode(doc.payload);val eligible=products().associateBy {it.id}
            val next=when(val a=c.action) {
                is InventoryAction.Configure->{
                    if(a.config.lastCompleted!=old.config.lastCompleted || !a.config.enabled) throw CatalogFailure("inventory","Нельзя подменить завершение пересчёта")
                    if(!eligible.keys.containsAll(a.config.productIds)) throw CatalogFailure("inventory","Выберите доступные товары с учётом остатков")
                    old.copy(config=a.config.copy(productIds=a.config.productIds.toSet()))
                }
                is InventoryAction.Start->{
                    if(old.draft!=null) throw CatalogFailure("inventory","Сначала завершите текущую инвентаризацию")
                    if(!old.config.enabled || old.config.productIds.isEmpty() || !eligible.keys.containsAll(old.config.productIds)) throw CatalogFailure("inventory","Сохраните список доступных товаров для пересчёта")
                    val today=LocalDate.now(clock);val scheduled=if(a.type=="adhoc") today else InventoryRules.period(today,old.config.frequency)
                    old.copy(draft=InventoryDraft(a.id,a.type,clock.millis(),scheduled.toString(),old.config.productIds.sorted().map {eligible.getValue(it)}))
                }
            }
            InventoryRules.validate(next);val target=next.draft?.id ?: InventoryCodec.KEY
            db.workspace().put(doc.copy(revision=Math.addExact(doc.revision,1),payload=InventoryCodec.encode(next)));afterWrite?.invoke()
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),target));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,if(c.action is InventoryAction.Start) "inventory.start" else "inventory.configure",target,clock.millis()));afterWrite?.invoke()
            target
        }}
        afterCommit?.invoke();return id
    }
    suspend fun requireProductUnused(id: String) {
        val d=db.workspace().document(InventoryCodec.KEY)?.let {InventoryCodec.decode(it.payload)} ?: return
        if(id in d.config.productIds || d.draft?.products?.any {it.id==id}==true) throw CatalogFailure("used","Товар выбран в настройках или активной инвентаризации")
    }
}
