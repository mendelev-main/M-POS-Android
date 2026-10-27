package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

object PurchaseDraftCodec {
    const val KEY="purchase-draft"
    fun encode(d: PurchaseDraft?): String=JSONObject().put("version",1).put("draft",d?.let {
        JSONObject().put("id",it.id).put("supplierId",it.supplierId).put("supplierRevision",it.supplierRevision).put("supplierName",it.supplierName)
            .put("lines",JSONArray(it.lines.map {l->JSONObject().put("id",l.productId).put("revision",l.productRevision).put("name",l.productName)
                .put("stockUnit",l.stockUnit).put("quantity",l.quantity).put("unit",l.unit).put("packSize",l.packSize).put("contentUnit",l.contentUnit)}))
    } ?: JSONObject.NULL).toString()
    fun decode(raw: String): PurchaseDraft? {
        try {
            val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString()=="1");require(j.has("draft"))
            if(j.isNull("draft")) return null
            val d=j.getJSONObject("draft");val a=d.getJSONArray("lines")
            fun revision(j: JSONObject,key: String): Long {require(j.get(key) is Number);return j.get(key).toString().toLong()}
            return PurchaseDraft(d.getString("id"),d.getString("supplierId"),revision(d,"supplierRevision"),d.getString("supplierName"),
                (0 until a.length()).map {i->val l=a.getJSONObject(i);PurchaseLine(l.getString("id"),revision(l,"revision"),l.getString("name"),l.getString("stockUnit"),
                    l.getString("quantity"),l.getString("unit"),l.getString("packSize"),l.getString("contentUnit"))}).also(PurchaseDraftRules::validate)
        } catch(_:Exception) {throw CatalogFailure("purchaseFormat","Не удалось прочитать черновик закупки. Данные не сброшены")}
    }
}
data class PurchaseSnapshot(val revision: Long,val ownerId: String,val draft: PurchaseDraft?,val suppliers: List<Supplier>,val products: List<PurchaseProduct>,val orders: List<PurchaseOrder> = emptyList())
interface PurchaseAccess {
    suspend fun snapshot(): PurchaseSnapshot
    suspend fun outcome(command: PurchaseCommand): String?
    suspend fun commit(command: PurchaseCommand): String
    suspend fun mutate(command: PurchaseOrderCommand): String = error("Order mutation unavailable")
    suspend fun mutationOutcome(command: PurchaseOrderCommand): String? = error("Order mutation unavailable")
}
class PurchaseDraftRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC(),
    private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null): PurchaseAccess {
    override suspend fun mutate(command: PurchaseOrderCommand)=PurchaseOrderRepository(db).commit(command)
    override suspend fun mutationOutcome(command: PurchaseOrderCommand)=PurchaseOrderRepository(db).outcome(command)
    private suspend fun suppliers()=db.workspace().document(SupplierCodec.KEY)?.let {SupplierCodec.decode(it.payload)}.orEmpty()
    private suspend fun products(): List<PurchaseProduct> {
        val cats=db.catalog().categorySnapshot().associate {it.id to it.name};val stocks=db.catalog().allBalances().associate {it.productId to it.quantity}
        return db.catalog().allProducts().filter {it.type=="simple"}.sortedWith(compareBy({CatalogRules.key(it.name)},{it.id})).map {
            PurchaseProduct(it.id,it.name,it.revision,it.unit,cats[it.categoryId] ?: "Без категории",stocks[it.id] ?: "0",it.purchaseLabel,it.packSize ?: "")
        }
    }
    override suspend fun snapshot(): PurchaseSnapshot {
        InstallationGate.write {db.withTransaction {
            CatalogRepository(db).initialize()
            if(db.workspace().document(PurchaseDraftCodec.KEY)==null) db.workspace().put(WorkspaceDocument(PurchaseDraftCodec.KEY,0,PurchaseDraftCodec.encode(null)))
        }}
        return db.withTransaction {
            val doc=db.workspace().document(PurchaseDraftCodec.KEY)!!
            PurchaseSnapshot(doc.revision,db.catalog().config()!!.ownerId,PurchaseDraftCodec.decode(doc.payload),suppliers().filterNot {it.deleted},products(),PurchaseOrderStore(db).all().sortedWith(compareByDescending<PurchaseOrder> {it.createdAt}.thenByDescending {it.id}))
        }
    }
    override suspend fun outcome(command: PurchaseCommand): String? {
        command.validate()
        return db.catalog().result(command.commandId)?.let {
            if(it.fingerprint!=command.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
            it.resultId
        }
    }
    override suspend fun commit(command: PurchaseCommand): String {
        val command=command.copy(action=when(val a=command.action) {is PurchaseAction.Save -> PurchaseAction.Save(a.draft.copy(lines=a.draft.lines.toList()));is PurchaseAction.Place -> a.copy(draft=a.draft.copy(lines=a.draft.lines.toList()));PurchaseAction.Clear -> PurchaseAction.Clear})
        command.validate()
        val id=InstallationGate.write {db.withTransaction {
            val owner=db.catalog().config()?.ownerId ?: error("Хранилище не открыто")
            if(command.actorId!=owner) throw CatalogFailure("owner","Изменять закупки может только локальный владелец")
            outcome(command)?.let {return@withTransaction it}
            val doc=db.workspace().document(PurchaseDraftCodec.KEY) ?: WorkspaceDocument(PurchaseDraftCodec.KEY,0,PurchaseDraftCodec.encode(null))
            val old=PurchaseDraftCodec.decode(doc.payload)
            if(doc.revision!=command.revision) throw CatalogFailure("conflict","Черновик изменился. Перечитайте его перед сохранением")
            val place=command.action as? PurchaseAction.Place
            val draft=when(val a=command.action) {is PurchaseAction.Save->a.draft;is PurchaseAction.Place->a.draft;PurchaseAction.Clear->null}
            if(draft!=null) {
                if(old!=null && old.id!=draft.id) throw CatalogFailure("conflict","В кассе уже открыт другой черновик")
                if(old!=null && old.supplierId!=draft.supplierId && draft.lines.isNotEmpty()) throw CatalogFailure("supplier","Сменить поставщика можно только в пустом черновике")
                PurchaseDraftRules.validateReferences(draft,suppliers(),products())
            }
            val now=clock.millis();require(now>=0)
            if(place!=null) {
                if(old==null || old!=draft) throw CatalogFailure("conflict","Сначала сохраните текущий черновик перед формированием заказа")
                val order=PurchaseOrderRules.create(place.orderId,place.draft,now)
                PurchaseOrderStore(db).append(order);afterWrite?.invoke()
                order.source.lines.forEach {l->
                    SplitPaymentRepository(db).requireProductUnlocked(l.productId)
                    val product=db.catalog().product(l.productId) ?: throw CatalogFailure("product","Товар недоступен")
                    db.catalog().updateProduct(product.copy(revision=Math.addExact(product.revision,1),purchaseLabel=l.unit,packSize=PurchaseOrderRules.defaultPackSize(l)))
                    afterWrite?.invoke()
                }
            }
            val target=place?.orderId ?: draft?.id ?: PurchaseDraftCodec.KEY
            db.workspace().put(doc.copy(revision=Math.addExact(doc.revision,1),payload=PurchaseDraftCodec.encode(if(place==null) draft else null)));afterWrite?.invoke()
            db.catalog().result(ResultEntity(command.commandId,command.fingerprint(),target));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command.commandId,command.actorId,if(place!=null) "purchase.order.place" else if(draft==null) "purchase.draft.clear" else "purchase.draft.save",target,now));afterWrite?.invoke()
            target
        }}
        afterCommit?.invoke();return id
    }
    suspend fun requireSupplierUnused(id: String) {
        PurchaseOrderStore(db).requireSupplierUnused(id)
        ReceivingReferenceGuard(db).supplier(id)
        val d=db.workspace().document(PurchaseDraftCodec.KEY)?.let {PurchaseDraftCodec.decode(it.payload)}
        if(d?.supplierId==id) throw CatalogFailure("used","Поставщик выбран в черновике закупки: сначала удалите черновик")
    }
    suspend fun requireProductUnused(id: String) {
        PurchaseOrderStore(db).requireProductUnused(id)
        ReceivingReferenceGuard(db).product(id)
        val d=db.workspace().document(PurchaseDraftCodec.KEY)?.let {PurchaseDraftCodec.decode(it.payload)}
        if(d?.lines?.any {it.productId==id}==true) throw CatalogFailure("used","Товар выбран в черновике закупки: сначала уберите строку")
    }
}
