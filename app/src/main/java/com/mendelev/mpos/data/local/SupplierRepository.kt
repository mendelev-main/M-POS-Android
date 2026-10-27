package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

/** Existing Room document boundary (like checkout/home), with its own versioned namespace. */
object SupplierCodec {
    const val KEY="suppliers"
    fun encode(rows: List<Supplier>): String=JSONObject().put("version",1).put("items",JSONArray(rows.sortedBy {it.id}.map {s->
        JSONObject().put("id",s.id).put("name",s.name).put("revision",s.revision).put("deleted",s.deleted).put("products",JSONArray(s.productIds.sorted()))
    })).toString()
    fun decode(raw: String): List<Supplier> {
        try {
            val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString()=="1")
            val a=j.getJSONArray("items")
            val rows=(0 until a.length()).map {i->
                val s=a.getJSONObject(i);val p=s.getJSONArray("products")
                val ids=(0 until p.length()).map {p.getString(it)}
                require(ids.size==ids.distinct().size)
                require(s.get("revision") is Number && s.get("deleted") is Boolean)
                val row=Supplier(s.getString("id"),s.getString("name"),s.get("revision").toString().toLong(),ids.toSet(),s.getBoolean("deleted"))
                val valid=SupplierCommand("validate","owner",SupplierAction.Save(row.id,row.revision,row.name,row.productIds)).validated().action as SupplierAction.Save
                require(valid.name==row.name && (!row.deleted || ids.isEmpty()))
                row
            }
            require(rows.map {it.id}.distinct().size==rows.size)
            return rows
        } catch(_:Exception) {throw CatalogFailure("supplierFormat","Не удалось прочитать поставщиков. Данные не сброшены")}
    }
}
data class SupplierProduct(val id: String,val name: String,val category: String)
data class SupplierSnapshot(val ownerId: String,val suppliers: List<Supplier>,val products: List<SupplierProduct>)
interface SupplierAccess {
    suspend fun snapshot(): SupplierSnapshot
    suspend fun commit(command: SupplierCommand): String
    suspend fun outcome(command: SupplierCommand): String?
}
class SupplierRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemUTC(),
    private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null): SupplierAccess {
    private suspend fun initialize()=InstallationGate.write {db.withTransaction {
        CatalogRepository(db).initialize()
        if(db.workspace().document(SupplierCodec.KEY)==null) db.workspace().put(WorkspaceDocument(SupplierCodec.KEY,0,SupplierCodec.encode(emptyList())))
    }}
    override suspend fun snapshot(): SupplierSnapshot {
        initialize()
        return db.withTransaction {
            val rows=SupplierCodec.decode(db.workspace().document(SupplierCodec.KEY)!!.payload)
            val products=db.catalog().allProducts().filter {it.type=="simple"}
            val ids=products.map {it.id}.toSet()
            check(rows.filterNot {it.deleted}.all {ids.containsAll(it.productIds)}) {"Нарушены ссылки товаров поставщика"}
            val groups=db.catalog().categorySnapshot().associate {it.id to it.name}
            SupplierSnapshot(db.catalog().config()!!.ownerId,rows.filterNot {it.deleted}.sortedWith(compareBy({CatalogRules.key(it.name)},{it.id})),
                products.sortedWith(compareBy({CatalogRules.key(it.name)},{it.id})).map {SupplierProduct(it.id,it.name,groups[it.categoryId] ?: "Без категории")})
        }
    }
    override suspend fun outcome(command: SupplierCommand): String? {
        val c=command.validated()
        return db.catalog().result(c.commandId)?.let {
            if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
            it.resultId
        }
    }
    override suspend fun commit(command: SupplierCommand): String {
        val c=command.validated()
        val id=InstallationGate.write {db.withTransaction {
            val config=db.catalog().config() ?: error("Хранилище не открыто")
            if(c.actorId!=config.ownerId) throw CatalogFailure("owner","Изменять поставщиков может только локальный владелец")
            outcome(c)?.let {return@withTransaction it}
            val doc=db.workspace().document(SupplierCodec.KEY) ?: WorkspaceDocument(SupplierCodec.KEY,0,SupplierCodec.encode(emptyList()))
            val next=SupplierRules.apply(SupplierCodec.decode(doc.payload),c,db.catalog().allProducts().associate {it.id to it.type})
            val target=when(val a=c.action) {is SupplierAction.Save->a.id;is SupplierAction.Delete->a.id}
            val now=clock.millis();require(now>=0) {"Некорректное время операции"}
            db.workspace().put(doc.copy(revision=Math.addExact(doc.revision,1),payload=SupplierCodec.encode(next)));afterWrite?.invoke()
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),target));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,if(c.action is SupplierAction.Save) "supplier.save" else "supplier.delete",target,now));afterWrite?.invoke()
            target
        }}
        afterCommit?.invoke()
        return id
    }
    suspend fun requireProductUnlinked(id: String) {
        val doc=db.workspace().document(SupplierCodec.KEY) ?: return
        if(SupplierCodec.decode(doc.payload).any {!it.deleted && id in it.productIds})
            throw CatalogFailure("used","Товар выбран у поставщика: сначала измените товары поставщика")
    }
}
