package com.mendelev.mpos.domain.warehouse

import java.time.*

/** Inclusive local calendar dates; query instants are [start,end), including DST transitions. */
data class StockPeriod(val from: String,val to: String) {
    fun bounds(at: Long,zone: ZoneId): LongRange {
        fun date(raw: String): LocalDate {
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(raw)) {"Укажите даты в формате ГГГГ-ММ-ДД"}
            return LocalDate.parse(raw)
        }
        val start=date(from);val last=date(to);val today=Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        require(start<=last) {"Начало периода позже окончания"};require(last<=today) {"Дата окончания не может быть в будущем"}
        return start.atStartOfDay(zone).toInstant().toEpochMilli() until last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    companion object {
        fun preset(key: String,at: Long,zone: ZoneId): StockPeriod {
            val end=Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            val start=when(key) {"today"->end;"7"->end.minusDays(6);"month"->end.withDayOfMonth(1);"quarter"->end.withMonth(((end.monthValue-1)/3)*3+1).withDayOfMonth(1);else->error("Неизвестный период")}
            return StockPeriod(start.toString(),end.toString())
        }
    }
}
