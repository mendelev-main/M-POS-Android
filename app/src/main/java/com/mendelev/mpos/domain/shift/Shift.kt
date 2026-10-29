package com.mendelev.mpos.domain.shift

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules
import java.math.BigDecimal
import com.mendelev.mpos.domain.staff.Employee
import com.mendelev.mpos.domain.staff.EmployeeRules

/** Immutable closed snapshot; money is always BYN minor units. */
data class Shift(val id: String,val registerId: String,val actorId: String,val actorName: String,
    val openedAt: Long,val zoneId: String,val openingCash: Long,val sourceShiftId: String?,
    val closedAt: Long? = null,val countedCash: Long? = null,val expectedCash: Long? = null,val difference: Long? = null) {
    val isOpen: Boolean get()=closedAt==null
}
data class ShiftSnapshot(val registerId: String,val ownerId: String,val revision: Long,val last: Shift?,val saleCashDelta: Long = 0L) {
    val active: Shift? get()=last?.takeIf {it.isOpen}
    val currentCash: Long get()=Math.addExact(last?.openingCash ?: 0L,saleCashDelta)
    val carry: Long get()=last?.countedCash ?: 0L
}
object ShiftRules {
    fun cash(raw: String): Long {
        val text=raw.trim().replace(',','.')
        if(!text.matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?")) || text.length>24) throw CatalogFailure("cash","Введите сумму от 0 с точностью до копейки")
        return try {BigDecimal(text).movePointRight(2).longValueExact()}
        catch(_:ArithmeticException) {throw CatalogFailure("cash","Сумма слишком велика")}
    }
    fun money(minor: Long): String=BigDecimal.valueOf(minor,2).toPlainString().replace('.',',')+" BYN"
    fun token(state: ShiftSnapshot): String {
        val shift=state.active ?: throw CatalogFailure("shift","Смена не открыта")
        return CatalogRules.fingerprint("shift.close.preview.v2",state.registerId,state.revision,shift.id,state.currentCash)
    }
    fun open(state: ShiftSnapshot,expectedRevision: Long,previousId: String?,employee: Employee,id: String,now: Long,zone: String): Shift {
        EmployeeRules.validate(employee)
        if(!employee.selectable) throw CatalogFailure("employee","Выберите кассира")
        if(state.revision!=expectedRevision || state.last?.id!=previousId || state.active!=null) conflict()
        if(state.carry<0 || now<0 || zone.isBlank() || id.isBlank()) throw CatalogFailure("shift","Некорректные данные смены")
        return Shift(id,state.registerId,employee.id,employee.name,now,zone,state.carry,state.last?.id)
    }
    fun close(state: ShiftSnapshot,id: String,revision: Long,token: String,counted: Long,actor: String,now: Long): Shift {
        val shift=state.active ?: conflict()
        if(actor.isBlank() || actor!=shift.actorId) throw CatalogFailure("owner","Недопустимый оператор смены")
        if(shift.id!=id || state.revision!=revision || token!=ShiftRules.token(state)) conflict()
        if(counted<0 || now<shift.openedAt) throw CatalogFailure("cash","Некорректная сумма или время закрытия смены")
        return shift.copy(closedAt=now,countedCash=counted,expectedCash=state.currentCash,difference=counted-state.currentCash)
    }
    private fun conflict(): Nothing=throw CatalogFailure("conflict","Смена изменилась. Откройте подтверждение заново")
}
