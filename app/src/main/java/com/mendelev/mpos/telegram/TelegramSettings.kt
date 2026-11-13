package com.mendelev.mpos.telegram

import java.time.LocalDate
import java.time.YearMonth

data class TelegramSettings(
    val revision: Long=0,val enabled: Boolean=false,val botToken: String="",val chatId: String="",val threadId: String="",
    val deviceChatId: String="",val ownerChatId: String="",val onlineOrders: Boolean=false,
    val shiftOpened: Boolean=true,val shiftClosed: Boolean=true,val monthlyWarehouse: Boolean=false,
    val backendUrl: String="",val deviceKey: String="",val ownerShiftStats: Boolean=false,val ownerMonthlyStats: Boolean=false,val onlineOrdersConfigured: Boolean=false,
    val ownerMonthReport: Boolean=false,val ownerQuarterReport: Boolean=false,val ownerMonthSince: Long=0,val ownerQuarterSince: Long=0
) {
    override fun toString()="TelegramSettings(revision=$revision, enabled=$enabled, credentials=REDACTED)"
    fun validated(test: Boolean=false): TelegramSettings {
        val n=copy(monthlyWarehouse=false,ownerMonthlyStats=false,botToken=botToken.trim(),chatId=chatId.trim(),threadId=threadId.trim(),deviceChatId=deviceChatId.trim(),ownerChatId=ownerChatId.trim(),backendUrl=backendUrl.trim().trimEnd('/'),deviceKey=deviceKey.trim())
        require(n.ownerMonthSince>=0 && n.ownerQuarterSince>=0) {"Некорректная дата включения отчётов"}
        require(n.botToken.isEmpty() || n.botToken.matches(Regex("[0-9]{1,20}:[A-Za-z0-9_-]{20,150}"))) {"Проверьте токен бота"}
        require(!test && !n.enabled || n.botToken.isNotBlank()) {"Введите токен бота"}
        require(n.chatId.isEmpty() || n.chatId.matches(Regex("-?[1-9][0-9]{0,19}"))) {"Укажите корректный ID рабочей группы"}
        require(n.threadId.isEmpty() || n.threadId.matches(Regex("[1-9][0-9]{0,19}"))) {"Укажите положительный ID темы или оставьте его пустым"}
        require(n.deviceChatId.isEmpty() || n.deviceChatId.matches(Regex("[1-9][0-9]{0,19}"))) {"Укажите корректный ID рабочего устройства"}
        require(n.ownerChatId.isEmpty() || n.ownerChatId.matches(Regex("[1-9][0-9]{0,19}"))) {"Укажите корректный ID владельца"}
        require(!test && !(n.enabled && (n.shiftOpened || n.shiftClosed || n.monthlyWarehouse)) || n.chatId.isNotBlank()) {"Введите ID рабочей группы"}
        require(!(n.enabled && (n.ownerShiftStats || n.ownerMonthReport || n.ownerQuarterReport)) || n.ownerChatId.isNotBlank()) {"Введите ID владельца для отправки статистики"}
        require(!(n.enabled && n.onlineOrders) || n.deviceChatId.isNotBlank()) {"Введите ID рабочего устройства для онлайн-заказов"}
        if(n.backendUrl.isNotBlank()) {
            val uri=runCatching {java.net.URI(n.backendUrl)}.getOrNull()
            require(uri!=null && uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null) {"Укажите HTTPS-адрес backend без параметров и пароля"}
        }
        require(n.deviceKey.length<=200 && n.deviceKey.none {it.isISOControl()}) {"Проверьте ключ устройства"}
        return n
    }
}

object TelegramMessages {
    fun escape(value: String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    fun opened(employee: String,date: String,number: Int)="🟢 <b>Смена №$number открыта</b>\n👤 Сотрудник: ${escape(OwnerReportCaption.employeeName(employee))}\n🕐 Время: $date"
    fun closed(employee: String,date: String,number: Int)="🔴 <b>Смена №$number закрыта</b>\n👤 Сотрудник: ${escape(OwnerReportCaption.employeeName(employee))}\n🕐 Время: $date"
    fun previousMonth(today: LocalDate)=YearMonth.from(today).minusMonths(1)
}
