package com.mendelev.mpos.domain.shift

import com.mendelev.mpos.domain.catalog.*

data class ShiftCashCommand(val commandId: String,val shiftId: String,val revision: Long,val actorId: String,val type: String,val amount: Long,val note: String) {
    fun validate() {
        require(listOf(commandId,shiftId,actorId).all {it.isNotBlank() && it.length<=100})
        require(revision>=0 && type in setOf("deposit","withdrawal") && amount>0)
        require(note==note.trim() && note.length<=300 && note.none {it.isISOControl()}) {"Комментарий — до 300 символов без управляющих знаков"}
    }
    fun fingerprint(): String {validate();return CatalogRules.fingerprint("shift.cash.v1",shiftId,revision,actorId,type,amount,note)}
}
data class ShiftCashMovement(val id: String,val command: ShiftCashCommand,val at: Long,val before: Long,val after: Long)
object ShiftCashRules {
    fun amount(raw: String)=ShiftRules.cash(raw).also {if(it<=0) throw CatalogFailure("cash","Введите сумму больше нуля")}
    fun after(before: Long,type: String,amount: Long): Long {
        require(before>=0 && amount>0)
        val next=try {when(type) {"deposit"->Math.addExact(before,amount);"withdrawal"->Math.subtractExact(before,amount);else->error("Неверный вид операции")}} catch(_:ArithmeticException) {throw CatalogFailure("cash","Сумма кассы выходит за допустимый диапазон")}
        if(next<0) throw CatalogFailure("cash","Недостаточно наличных в кассе")
        return next
    }
    fun validate(m: ShiftCashMovement) {m.command.validate();require(m.id.isNotBlank() && m.id.length<=100 && m.at>=0 && m.after==after(m.before,m.command.type,m.command.amount))}
}
