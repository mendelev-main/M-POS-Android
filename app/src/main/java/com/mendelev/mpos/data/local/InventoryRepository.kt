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

/** Changes whenever the Android process starts; never persisted as current authority. */
object InventoryRuntime {val generation: String=UUID.randomUUID().toString()}
object InventoryCodec {
    const val KEY="inventory"
    private fun product(p: InventoryProduct)=JSONObject().put("id",p.id).put("name",p.name).put("category",p.category).put("unit",p.unit).put("revision",p.revision).put("stockRevision",p.stockRevision).put("stock",p.stock).put("cost",p.cost)
    private fun long(o: JSONObject,k: String): Long {require(o.get(k) is Number);return o.get(k).toString().toLong()}
    private fun product(p: JSONObject)=InventoryProduct(p.getString("id"),p.getString("name"),p.getString("category"),p.getString("unit"),long(p,"revision"),long(p,"stockRevision"),p.getString("stock"),p.getString("cost"))
    private fun draft(s: InventoryDraft)=JSONObject().put("id",s.id).put("type",s.type).put("startedAt",s.startedAt).put("scheduledDate",s.scheduledDate).put("products",JSONArray(s.products.map(::product)))
        .put("counts",JSONArray(s.counts.map {c->JSONObject().put("product",product(c.product)).put("raw",c.raw).put("generation",c.generation).put("fixed",c.fixed?.let {f->JSONObject().put("product",product(f.product)).put("actual",f.actual).put("delta",f.delta).put("at",f.at).put("actorId",f.actorId).put("commandId",f.commandId).put("movementId",f.movementId).put("stockRevisionAfter",f.stockRevisionAfter)} ?: JSONObject.NULL)}))
    private fun draft(s: JSONObject,countsRequired: Boolean): InventoryDraft {
        val a=s.getJSONArray("products");val counts=if(!countsRequired) emptyList() else s.getJSONArray("counts").let {rows->(0 until rows.length()).map {i->val r=rows.getJSONObject(i);require(r.has("fixed"))
            InventoryCount(product(r.getJSONObject("product")),r.getString("raw"),r.getString("generation"),if(r.isNull("fixed")) null else r.getJSONObject("fixed").let {f->InventoryFix(product(f.getJSONObject("product")),f.getString("actual"),f.getString("delta"),long(f,"at"),f.getString("actorId"),f.getString("commandId"),f.getString("movementId"),long(f,"stockRevisionAfter"))})
        }}
        return InventoryDraft(s.getString("id"),s.getString("type"),long(s,"startedAt"),s.getString("scheduledDate"),(0 until a.length()).map {product(a.getJSONObject(it))},counts)
    }
    fun encode(d: InventoryDocument): String {
        val c=d.config
        return JSONObject().put("version",3).put("config",JSONObject().put("enabled",c.enabled).put("frequency",c.frequency).put("products",JSONArray(c.productIds.sorted())).put("lastCompleted",c.lastCompleted ?: JSONObject.NULL))
            .put("draft",d.draft?.let(::draft) ?: JSONObject.NULL)
            .put("history",JSONArray(d.history.map {r->JSONObject().put("draft",draft(r.draft)).put("cancelled",r.cancelled).put("at",r.at).put("date",r.date).put("actorId",r.actorId).put("commandId",r.commandId).put("lossMinor",r.lossMinor ?: JSONObject.NULL).put("policy",r.policy)})).toString()
    }
    fun decode(raw: String): InventoryDocument=try {
        val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString() in setOf("1","2","3"));val version=j.get("version").toString()
        val c=j.getJSONObject("config");require(c.get("enabled") is Boolean && c.has("lastCompleted") && j.has("draft"))
        val ids=c.getJSONArray("products");val selected=(0 until ids.length()).map {ids.getString(it)};require(selected.distinct().size==selected.size)
        val history=if(version!="3") emptyList() else j.getJSONArray("history").let {a->(0 until a.length()).map {i->val r=a.getJSONObject(i);require(r.get("cancelled") is Boolean && r.has("lossMinor"))
            InventoryRecord(draft(r.getJSONObject("draft"),true),r.getBoolean("cancelled"),long(r,"at"),r.getString("date"),r.getString("actorId"),r.getString("commandId"),if(r.isNull("lossMinor")) null else long(r,"lossMinor"),long(r,"policy").also {require(it==1L)}.toInt())
        }}
        InventoryDocument(InventoryConfig(c.getBoolean("enabled"),c.getString("frequency"),selected.toSet(),if(c.isNull("lastCompleted")) null else c.getString("lastCompleted")),if(j.isNull("draft")) null else draft(j.getJSONObject("draft"),version!="1"),history).also(InventoryRules::validate)
    } catch(_:Exception) {throw CatalogFailure("inventoryFormat","Не удалось прочитать инвентаризацию. Данные не сброшены")}
}

data class InventorySnapshot(val ownerId: String,val revision: Long,val document: InventoryDocument,val products: List<InventoryProduct>,val today: String,val generation: String="")
interface InventoryAccess {
    suspend fun snapshot(): InventorySnapshot
    suspend fun commit(c: InventoryCommand): String
    suspend fun outcome(c: InventoryCommand): String?
}
class InventoryRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemDefaultZone(),private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null,private val generation: String=InventoryRuntime.generation): InventoryAccess {
    private suspend fun products(): List<InventoryProduct> {
        val categories=db.catalog().categorySnapshot().associate {it.id to it.name}
        return db.catalog().allProducts().filter {it.type=="simple" && !it.noStockTracking}.map {p->val b=db.catalog().balance(p.id) ?: error("Нет остатка")
            InventoryProduct(p.id,p.name,categories[p.categoryId] ?: "Без категории",p.unit,p.revision,b.revision,b.quantity,p.cost)
        }.sortedWith(compareBy({CatalogRules.key(it.category)},{CatalogRules.key(it.name)},{it.id}))
    }
    override suspend fun snapshot(): InventorySnapshot {
        InstallationGate.write {db.withTransaction {CatalogRepository(db).initialize()}}
        return db.withTransaction {val doc=db.workspace().document(InventoryCodec.KEY)
            InventorySnapshot(db.catalog().config()!!.ownerId,doc?.revision ?: 0,doc?.let {InventoryCodec.decode(it.payload)} ?: InventoryDocument(),products(),LocalDate.now(clock).toString(),generation)
        }
    }
    override suspend fun outcome(c: InventoryCommand): String? {
        c.validate();return db.catalog().result(c.commandId)?.let {if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения");it.resultId}
    }
    override suspend fun commit(c: InventoryCommand): String {
        val c=c.copy(action=when(val a=c.action) {is InventoryAction.Configure->a.copy(config=a.config.copy(productIds=a.config.productIds.toSet()));else->a})
        c.validate()
        val id=InstallationGate.write {db.withTransaction {
            if(db.catalog().config()?.ownerId!=c.actorId) throw CatalogFailure("owner","Изменять инвентаризацию может только локальный владелец")
            outcome(c)?.let {return@withTransaction it}
            val doc=db.workspace().document(InventoryCodec.KEY) ?: WorkspaceDocument(InventoryCodec.KEY,0,InventoryCodec.encode(InventoryDocument()))
            if(doc.revision!=c.revision) throw CatalogFailure("conflict","Инвентаризация изменилась. Перечитайте данные")
            val old=InventoryCodec.decode(doc.payload);val eligible=if(c.action is InventoryAction.Finish) emptyMap() else products().associateBy {it.id}
            fun counted(draftId: String,productId: String): Pair<InventoryDraft,InventoryProduct> {
                val draft=old.draft?.takeIf {it.id==draftId && it.products.any {p->p.id==productId}} ?: throw CatalogFailure("inventory","Товар не входит в активный пересчёт")
                if(draft.counts.any {it.product.id==productId && it.fixed!=null}) throw CatalogFailure("inventory","Остаток уже зафиксирован")
                val live=eligible[productId] ?: throw CatalogFailure("inventory","Для товара недоступен учёт остатков")
                return draft to live
            }
            fun fresh(saved: InventoryProduct,live: InventoryProduct,token: String) {
                if(token!=generation || !InventoryRules.sameStock(saved,live)) throw CatalogFailure("stale","Остаток товара «${live.name}» изменился или пересчёт устарел. Пересчитайте товар и заново введите количество")
            }
            val next=when(val a=c.action) {
                is InventoryAction.Configure->{
                    if(a.config.lastCompleted!=old.config.lastCompleted || !a.config.enabled) throw CatalogFailure("inventory","Нельзя подменить завершение пересчёта")
                    if(!eligible.keys.containsAll(a.config.productIds)) throw CatalogFailure("inventory","Выберите доступные товары с учётом остатков")
                    old.copy(config=a.config.copy(productIds=a.config.productIds.toSet()))
                }
                is InventoryAction.Start->{
                    if(old.draft!=null) throw CatalogFailure("inventory","Сначала завершите текущую инвентаризацию")
                    if(old.history.any {it.id==a.id}) throw CatalogFailure("inventory","Этот документ инвентаризации уже закрыт")
                    if(!old.config.enabled || old.config.productIds.isEmpty() || !eligible.keys.containsAll(old.config.productIds)) throw CatalogFailure("inventory","Сохраните список доступных товаров для пересчёта")
                    val today=LocalDate.now(clock);val scheduled=if(a.type=="adhoc") today else InventoryRules.period(today,old.config.frequency)
                    old.copy(draft=InventoryDraft(a.id,a.type,clock.millis(),scheduled.toString(),old.config.productIds.sorted().map {eligible.getValue(it)}))
                }
                is InventoryAction.Count->{
                    val (draft,live)=counted(a.draftId,a.product.id);fresh(a.product,live,a.generation)
                    old.copy(draft=draft.copy(counts=draft.counts.filterNot {it.product.id==a.product.id}+InventoryCount(live,a.raw,a.generation)))
                }
                is InventoryAction.Fix->{
                    val (draft,live)=counted(a.draftId,a.productId)
                    val count=draft.counts.find {it.product.id==a.productId} ?: throw CatalogFailure("inventory","Сначала сохраните фактический остаток")
                    if(a.generation!=generation) throw CatalogFailure("stale","Пересчитайте товар после перезапуска")
                    fresh(count.product,live,count.generation)
                    val actual=InventoryRules.actual(count.raw);val delta=InventoryRules.delta(count)
                    val balance=db.catalog().balance(live.id) ?: error("Нет остатка")
                    val revision=Math.addExact(balance.revision,1);val movementId=UUID.randomUUID().toString();val at=clock.millis();require(at>=0)
                    val fixed=InventoryFix(live,actual,delta,at,c.actorId,c.commandId,movementId,revision)
                    db.catalog().movement(MovementEntity(movementId,live.id,delta,"inventory",CatalogRules.fingerprint("inventory.stock.v1",c.commandId,live.id),at,draft.id));afterWrite?.invoke()
                    db.catalog().updateBalance(balance.copy(quantity=actual,revision=revision));afterWrite?.invoke()
                    old.copy(draft=draft.copy(counts=draft.counts.map {if(it.product.id==live.id) it.copy(fixed=fixed) else it}))
                }
                is InventoryAction.Finish->{
                    val draft=old.draft?.takeIf {it.id==a.draftId} ?: throw CatalogFailure("inventory","Инвентаризация уже закрыта или изменилась")
                    val at=clock.millis();require(at>=0);val date=LocalDate.now(clock).toString()
                    val summary=InventoryClosingRules.summary(draft)
                    val record=InventoryRecord(draft,a.cancelled,at,date,c.actorId,c.commandId,summary.lossMinor)
                    InventoryClosingRules.validate(record)
                    val config=if(!a.cancelled && draft.type=="scheduled") old.config.copy(lastCompleted=date) else old.config
                    old.copy(config=config,draft=null,history=old.history+record)
                }
            }
            InventoryRules.validate(next);val target=(c.action as? InventoryAction.Finish)?.draftId ?: next.draft?.id ?: InventoryCodec.KEY
            db.workspace().put(doc.copy(revision=Math.addExact(doc.revision,1),payload=InventoryCodec.encode(next)));afterWrite?.invoke()
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),target));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,when(c.action) {is InventoryAction.Start->"inventory.start";is InventoryAction.Configure->"inventory.configure";is InventoryAction.Count->"inventory.count";is InventoryAction.Fix->"inventory.fix";is InventoryAction.Finish->if(c.action.cancelled) "inventory.cancel" else "inventory.complete"},target,clock.millis()));afterWrite?.invoke()
            target
        }}
        afterCommit?.invoke();return id
    }
    suspend fun requireProductUnused(id: String) {
        val d=db.workspace().document(InventoryCodec.KEY)?.let {InventoryCodec.decode(it.payload)} ?: return
        if(id in d.config.productIds || d.draft?.products?.any {it.id==id}==true || d.history.any {r->r.draft.products.any {it.id==id}}) throw CatalogFailure("used","Товар выбран в настройках, активной инвентаризации или её истории")
    }
}
