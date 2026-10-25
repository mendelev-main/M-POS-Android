package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.shift.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.util.UUID

class ShiftRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemDefaultZone(),private val afterWrite: (() -> Unit)?=null) {
    private val dao=db.shifts()
    suspend fun initialize() {CatalogRepository(db).initialize();db.withTransaction {dao.initialize(ShiftRegisterEntity())}}
    fun observe(): Flow<ShiftSnapshot> = dao.observe().filterNotNull().map {snapshot()}
    suspend fun snapshot(): ShiftSnapshot=db.withTransaction {
        val config=db.catalog().config() ?: error("Хранилище не открыто")
        val register=dao.register() ?: error("Касса не открыта")
        val last=register.lastShiftId?.let {dao.shift(it) ?: error("Не найдена смена")}
        val active=dao.active()
        check(active.size<=1 && active.all {it.id==last?.id && it.activeSlot==1 && it.closedAt==null}) {"Нарушен журнал смен"}
        check(last==null || last.registerId==config.registerId && (last.closedAt==null)==(last.activeSlot==1)) {"Нарушен статус смены"}
        val cash=if(last==null) 0L else if(last.closedAt!=null) last.expectedCash ?: error("Не сохранён итог смены") else
            db.sales().inShift(last.id).fold(last.openingCash) {sum,r->
                check(r.cash>=0 && r.deliveryCashOut>=0)
                Math.subtractExact(Math.addExact(sum,r.cash),r.deliveryCashOut).also {check(it>=0)}
            }
        ShiftSnapshot(config.registerId,config.ownerId,register.revision,last?.domain(),Math.subtractExact(cash,last?.openingCash ?: 0L))
    }
    private suspend fun replay(command: String,fp: String): String? {
        require(command.isNotBlank())
        val old=db.catalog().result(command) ?: return null
        if(old.fingerprint!=fp) throw CatalogFailure("conflict","Команда уже использована для другого изменения")
        check(dao.shift(old.resultId)!=null) {"Не найден результат смены"}
        return old.resultId
    }
    private suspend fun record(command: String,fp: String,shift: Shift,revision: Long,action: String) {
        dao.register(ShiftRegisterEntity(revision=revision+1,lastShiftId=shift.id));afterWrite?.invoke()
        db.catalog().result(ResultEntity(command,fp,shift.id));afterWrite?.invoke()
        db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,shift.actorId,"shift.$action",shift.id,clock.millis()));afterWrite?.invoke()
    }
    suspend fun open(command: String,revision: Long,previousId: String?,actor: String): String=InstallationGate.write {db.withTransaction {
        val fp=CatalogRules.fingerprint("shift.open.v1",revision,previousId,actor)
        replay(command,fp)?.let {return@withTransaction it}
        val state=snapshot();check(state.revision<Long.MAX_VALUE)
        val shift=ShiftRules.open(state,revision,previousId,actor,UUID.randomUUID().toString(),clock.millis(),clock.zone.id)
        dao.insert(shift.entity());afterWrite?.invoke();record(command,fp,shift,state.revision,"open");shift.id
    }}
    suspend fun close(command: String,id: String,revision: Long,token: String,counted: Long,actor: String): String=InstallationGate.write {db.withTransaction {
        val fp=CatalogRules.fingerprint("shift.close.v1",id,revision,token,counted,actor)
        replay(command,fp)?.let {return@withTransaction it}
        val state=snapshot();check(state.revision<Long.MAX_VALUE)
        val shift=ShiftRules.close(state,id,revision,token,counted,actor,clock.millis())
        dao.update(shift.entity());afterWrite?.invoke();record(command,fp,shift,state.revision,"close");shift.id
    }}
    /** Future sale must call this inside its own Room transaction, never just from a button. */
    suspend fun requireActive(id: String,revision: Long): Shift {
        check(db.inTransaction()) {"Допуск продажи требует транзакции"}
        val state=snapshot()
        if(state.revision!=revision || state.active?.id!=id) throw CatalogFailure("shift","Для операции нужна актуальная открытая смена")
        return state.active!!
    }
}
