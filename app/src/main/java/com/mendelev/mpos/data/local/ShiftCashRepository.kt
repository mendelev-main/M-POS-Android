package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.shift.*
import org.json.*
import java.time.Clock
import java.util.UUID

object ShiftCashCodec {
    const val KEY="shift_cash"
    fun encode(rows: List<ShiftCashMovement>)=JSONObject().put("version",1).put("items",JSONArray(rows.map {m->val c=m.command;JSONObject().put("id",m.id).put("commandId",c.commandId).put("shiftId",c.shiftId).put("revision",c.revision).put("actorId",c.actorId).put("type",c.type).put("amount",c.amount).put("note",c.note).put("at",m.at).put("before",m.before).put("after",m.after)})).toString()
    fun decode(raw: String): List<ShiftCashMovement> =try {
        val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString()=="1");val a=j.getJSONArray("items")
        fun long(o: JSONObject,k: String): Long {require(o.get(k) is Number);return o.get(k).toString().toLong()}
        (0 until a.length()).map {i->val m=a.getJSONObject(i);ShiftCashMovement(m.getString("id"),ShiftCashCommand(m.getString("commandId"),m.getString("shiftId"),long(m,"revision"),m.getString("actorId"),m.getString("type"),long(m,"amount"),m.getString("note")),long(m,"at"),long(m,"before"),long(m,"after")).also(ShiftCashRules::validate)}.also {rows->require(rows.map {it.id}.distinct().size==rows.size && rows.map {it.command.commandId}.distinct().size==rows.size)}
    } catch(_:Exception) {throw CatalogFailure("cashFormat","Не удалось прочитать движения наличных. Данные не сброшены")}
    suspend fun all(db: CatalogDatabase): List<ShiftCashMovement> {
        val rows=db.workspace().document(KEY)?.let {decode(it.payload)}.orEmpty()
        for(id in rows.map {it.command.shiftId}.distinct()) check(db.shifts().shift(id)!=null) {"Не найдена смена движения наличных"}
        return rows
    }
}
interface ShiftCashAccess {
    suspend fun history(id: String): List<ShiftCashMovement>
    suspend fun outcome(c: ShiftCashCommand): String?
    suspend fun commit(c: ShiftCashCommand): String
}
class ShiftCashRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemDefaultZone(),private val afterWrite: (() -> Unit)?=null,private val afterCommit: (() -> Unit)?=null): ShiftCashAccess {
    override suspend fun history(id: String)=db.withTransaction {ShiftCashCodec.all(db).filter {it.command.shiftId==id}.sortedWith(compareByDescending<ShiftCashMovement> {it.at}.thenByDescending {it.id})}
    override suspend fun outcome(c: ShiftCashCommand): String? {c.validate();return db.catalog().result(c.commandId)?.let {if(it.fingerprint!=c.fingerprint()) throw CatalogFailure("conflict","Команда уже использована для другой операции");check(ShiftCashCodec.all(db).any {m->m.id==it.resultId && m.command==c}) {"Не найден сохранённый результат наличных"};it.resultId}}
    override suspend fun commit(c: ShiftCashCommand): String {
        c.validate()
        val id=InstallationGate.write {db.withTransaction {
            if(db.catalog().config()?.ownerId!=c.actorId) throw CatalogFailure("owner","Движения наличных доступны локальному владельцу")
            outcome(c)?.let {return@withTransaction it}
            val s=ShiftRepository(db,clock).snapshot();val active=s.active
            if(s.revision!=c.revision || active?.id!=c.shiftId) throw CatalogFailure("conflict","Смена изменилась. Откройте операцию заново")
            SplitPaymentRepository(db).requireNone()
            val at=clock.millis();require(at>=active.openedAt)
            val m=ShiftCashMovement(UUID.randomUUID().toString(),c,at,s.currentCash,ShiftCashRules.after(s.currentCash,c.type,c.amount));ShiftCashRules.validate(m)
            val old=db.workspace().document(ShiftCashCodec.KEY);val rows=old?.let {ShiftCashCodec.decode(it.payload)}.orEmpty()
            db.workspace().put(WorkspaceDocument(ShiftCashCodec.KEY,Math.addExact(old?.revision ?: 0,1),ShiftCashCodec.encode(rows+m)));afterWrite?.invoke()
            db.shifts().register(ShiftRegisterEntity(revision=Math.addExact(s.revision,1),lastShiftId=active.id));afterWrite?.invoke()
            db.catalog().result(ResultEntity(c.commandId,c.fingerprint(),m.id));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),c.commandId,c.actorId,"shift.cash.${c.type}",m.id,at));afterWrite?.invoke()
            m.id
        }}
        afterCommit?.invoke();return id
    }
}
