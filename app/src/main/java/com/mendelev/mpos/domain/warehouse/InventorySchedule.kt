package com.mendelev.mpos.domain.warehouse

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class InventoryScheduleInfo(val next: LocalDate?,val description: String)
object InventorySchedule {
    fun describe(config: InventoryConfig,today: LocalDate): InventoryScheduleInfo {
        if(!config.enabled || config.productIds.isEmpty()) return InventoryScheduleInfo(null,"После сохранения настроек окно будет открываться автоматически.")
        val start=InventoryRules.period(today,config.frequency)
        val complete=config.lastCompleted?.let {InventoryRules.period(LocalDate.parse(it),config.frequency)==start}==true
        if(!complete && !today.isAfter(start.plusDays(6))) return InventoryScheduleInfo(start,"Окно инвентаризации открыто до "+start.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))+" включительно.")
        val next=when(config.frequency) {"weekly"->start.plusWeeks(1);"quarterly"->start.plusMonths(3);else->start.plusMonths(1)}
        return InventoryScheduleInfo(next,if(complete) "Инвентаризация в текущем периоде завершена." else "Следующее окно начнётся "+next.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))+".")
    }
}
