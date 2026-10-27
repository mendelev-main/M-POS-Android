package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

object ReceivingCodec {
    const val KEY="receiving-drafts"
    fun encode(rows: List<ReceivingRecord>): String=JSONObject().put("version",1).put("items",JSONArray(rows.map {r->
        JSONObject().put("orderId",r.orderId).put("revision",r.revision).put("draft",r.draft?.let {d->
            JSONObject().put("id",d.id).put("order",JSONObject(PurchaseOrderCodec.encode(listOf(d.order))))
                .put("invoiceNumber",d.invoiceNumber).put("invoiceDate",d.invoiceDate).put("lines",JSONArray(d.lines.map {l->val p=l.product
                    JSONObject().put("product",JSONObject().put("id",p.id).put("name",p.name).put("unit",p.unit).put("revision",p.revision).put("stockRevision",p.stockRevision).put("stock",p.stock).put("cost",p.cost).put("noStockTracking",p.noStockTracking))
                        .put("quantity",l.quantity).put("unit",l.unit).put("packSize",l.packSize).put("contentUnit",l.contentUnit).put("total",l.total)
                }))
        } ?: JSONObject.NULL)
    })).toString()
    fun decode(raw: String): List<ReceivingRecord> {
        try {
            val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString()=="1");val rows=j.getJSONArray("items")
            fun long(o: JSONObject,k: String): Long {require(o.get(k) is Number);return o.get(k).toString().toLong()}
            return (0 until rows.length()).map {i->
                val r=rows.getJSONObject(i);val orderId=r.getString("orderId");PurchaseDraftRules.id(orderId);require(r.has("draft"))
                val d=if(r.isNull("draft")) null else r.getJSONObject("draft").let {d->val a=d.getJSONArray("lines")
                    ReceivingDraft(d.getString("id"),PurchaseOrderCodec.decode(d.getJSONObject("order").toString()).single(),d.getString("invoiceNumber"),d.getString("invoiceDate"),
                        (0 until a.length()).map {n->val l=a.getJSONObject(n);val p=l.getJSONObject("product");require(p.get("noStockTracking") is Boolean)
                            ReceivingLine(ReceivingProduct(p.getString("id"),p.getString("name"),p.getString("unit"),long(p,"revision"),long(p,"stockRevision"),p.getString("stock"),p.getString("cost"),p.getBoolean("noStockTracking")),l.getString("quantity"),l.getString("unit"),l.getString("packSize"),l.getString("contentUnit"),l.getString("total"))
                        }).also {ReceivingRules.validate(it);require(it.order.id==orderId)}
                }
                ReceivingRecord(orderId,long(r,"revision"),d).also {require(it.revision>=1)}
            }.also {require(it.map {it.orderId}.distinct().size==it.size && it.mapNotNull {it.draft?.id}.distinct().size==it.count {it.draft!=null})}
        } catch(_:Exception) {throw CatalogFailure("receivingFormat","Не удалось прочитать черновики приёмки. Данные не сброшены")}
    }
}
data class ReceivingSnapshot(val ownerId: String,val orders: List<PurchaseOrder>,val records: List<ReceivingRecord>,val products: List<ReceivingProduct>)
interface ReceivingAccess {
    suspend fun snapshot(): ReceivingSnapshot
    suspend fun outcome(c: ReceivingCommand): String?
    suspend fun commit(c: ReceivingCommand): String
}
class ReceivingRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC(),private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null): ReceivingAccess {
    override suspend fun snapshot(): ReceivingSnapshot {
        InstallationGate.write {db.withTransaction {CatalogRepository(db).initialize()}}
        return db.withTransaction {
            val records=db.workspace().document(ReceivingCodec.KEY)?.let {ReceivingCodec.decode(it.payload)}.orEmpty()
            val products=db.catalog().allProducts().filter {it.type=="simple"}.map {p->val b=db.catalog().balance(p.id) ?: error("Нет складского baseline")
                ReceivingProduct(p.id,p.name,p.unit,p.revision,b.revision,b.quantity,p.cost,p.noStockTracking)
            }
            ReceivingSnapshot(db.catalog().config()!!.ownerId,PurchaseOrderStore(db).all().sortedByDescending {it.createdAt},records,products)
        }
    }
    override suspend fun outcome(c: ReceivingCommand): String? {
        c.validate()
        return db.catalog().result(c.commandId)?.let {
            if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
            it.resultId
        }
    }
    override suspend fun commit(c: ReceivingCommand): String {
        val c=c.copy(draft=c.draft?.copy(lines=c.draft.lines.toList()));c.validate()
        val id=InstallationGate.write {db.withTransaction {
            if(db.catalog().config()?.ownerId!=c.actorId) throw CatalogFailure("owner","Изменять приёмку может только локальный владелец")
            outcome(c)?.let {return@withTransaction it}
            val doc=db.workspace().document(ReceivingCodec.KEY) ?: WorkspaceDocument(ReceivingCodec.KEY,0,ReceivingCodec.encode(emptyList()))
            val rows=ReceivingCodec.decode(doc.payload);val old=rows.find {it.orderId==c.orderId}
            if((old?.revision ?: 0)!=c.revision) throw CatalogFailure("conflict","Черновик приёмки изменился. Перечитайте его; ввод сохранён")
            val d=c.draft
            if(d!=null) {
                if(rows.any {it.orderId!=c.orderId && it.draft?.id==d.id}) throw CatalogFailure("conflict","Идентификатор черновика уже используется другим заказом")
                val order=PurchaseOrderStore(db).all().find {it.id==c.orderId} ?: throw CatalogFailure("receiving","Заказ недоступен")
                if(order.status!="pending" || order!=d.order) throw CatalogFailure("conflict","Заказ изменился. Обновите основание черновика")
                if(old?.draft!=null && old.draft.id!=d.id) throw CatalogFailure("conflict","У заказа уже есть другой черновик")
                if(old?.draft!=null && !d.lines.map {it.product.id}.containsAll(old.draft.lines.map {it.product.id})) throw CatalogFailure("receiving","Нельзя незаметно удалить введённые факты приёмки")
                d.lines.forEach {l->val p=db.catalog().product(l.product.id)
                    if(p==null || p.type!="simple" || p.unit!=l.product.unit) throw CatalogFailure("receiving","Товар «${l.product.name}» недоступен")
                }
            }
            val record=ReceivingRecord(c.orderId,Math.addExact(c.revision,1),d)
            val next=if(old==null) rows+record else rows.map {if(it.orderId==c.orderId) record else it}
            val now=clock.millis();require(now>=0)
            db.workspace().put(doc.copy(revision=Math.addExact(doc.revision,1),payload=ReceivingCodec.encode(next)));afterWrite?.invoke()
            val target=d?.id ?: c.orderId
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),target));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,if(d==null) "receiving.draft.clear" else "receiving.draft.save",c.orderId,now));afterWrite?.invoke()
            target
        }}
        afterCommit?.invoke();return id
    }
}
