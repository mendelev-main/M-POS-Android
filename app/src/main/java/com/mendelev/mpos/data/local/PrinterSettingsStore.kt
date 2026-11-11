package com.mendelev.mpos.data.local

import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PrinterConfiguration(
    val id: String=UUID.randomUUID().toString(),val revision: Long=0,
    val name: String="",val ip: String="",val paperWidth: String="80",
    val printReceipts: Boolean=true,val printOrders: Boolean=false,
    val paymentComments: Boolean=true,val receiptTitle: String="M POS",val registerLabel: String="POS 1",
    val phrases: String="",val categories: String="",val drawer: Boolean=false,
    val autoReceipt: Boolean=true,val autoKitchen: Boolean=true,val copies: String="1",
    val printMode: String="graphic",val printWidth: String="auto",val dpi: String="203",
    val initCommand: String="1B,40",val cutCommand: String="1D,56,42,00",val drawerCommand: String="1B,70,00,19,FA",val enabled: Boolean=true
) {
    fun validated(): PrinterConfiguration {
        require(name.trim().length in 1..200) {"Введите название принтера"}
        val octets=ip.trim().split('.')
        require((!enabled && ip.isBlank()) || octets.size==4 && octets.all {it.isNotEmpty() && it.length<=3 && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) in 0..255}) {"Введите IP-адрес, например 192.168.1.100"}
        require(paperWidth in setOf("58","80")) {"Выберите ширину бумаги"}
        require(copies.toIntOrNull() in 1..10) {"Количество копий: от 1 до 10"}
        require(printMode in setOf("graphic","text") && printWidth in setOf("auto","384","576")) {"Проверьте режим печати"}
        require(dpi.toIntOrNull() in 100..600) {"Разрешение: от 100 до 600 DPI"}
        require(listOf(initCommand,cutCommand,drawerCommand).all {it.length<=200 && (it.isBlank() || it.matches(Regex("[0-9a-fA-F]{2}([ ,]+[0-9a-fA-F]{2})*")))}) {"Команды задаются HEX-байтами, например 1B,40"}
        require(listOf(receiptTitle,registerLabel,phrases,categories).all {it.length<=10000}) {"Слишком длинный текст конфигурации"}
        require(printReceipts || printOrders) {"Выберите хотя бы один тип печати"}
        if(drawer && printReceipts) com.mendelev.mpos.print.EscPosCommands.drawer(drawerCommand)
        return copy(name=name.trim(),ip=ip.trim(),drawer=drawer && printReceipts,autoReceipt=autoReceipt && printReceipts,autoKitchen=autoKitchen && printOrders)
    }
}

/** Device-local configuration. A successful synchronous commit precedes UI success. No printer I/O. */
class PrinterSettingsStore(file: File,private val authorized: ()->Boolean) {
    private val atomic=AtomicFile(file)
    companion object {private val lock=Any()}
    fun read(): List<PrinterConfiguration> = synchronized(lock) {
        val text=try {atomic.openRead().bufferedReader().use {it.readText()}} catch(e:java.io.FileNotFoundException) {if(atomic.baseFile.exists()) throw e else "[]"}
        val array=JSONArray(text)
        (0 until array.length()).map {i->
            val p=array.getJSONObject(i)
            PrinterConfiguration(p.getString("id"),p.getLong("revision"),p.getString("name"),p.getString("ip"),p.getString("paperWidth"),
                p.getBoolean("printReceipts"),p.getBoolean("printOrders"),p.getBoolean("paymentComments"),p.getString("receiptTitle"),p.getString("registerLabel"),
                p.getString("phrases"),p.getString("categories"),p.getBoolean("drawer"),p.getBoolean("autoReceipt"),p.getBoolean("autoKitchen"),p.getString("copies"),
                p.getString("printMode"),p.getString("printWidth"),p.getString("dpi"),p.getString("initCommand"),p.getString("cutCommand"),p.getString("drawerCommand"),p.optBoolean("enabled",true))
        }.also {rows->check(rows.map {it.id}.distinct().size==rows.size) {"Повреждён список принтеров"}}
    }
    fun save(input: PrinterConfiguration): List<PrinterConfiguration> = synchronized(lock) {
        check(authorized()) {"Войдите в администрирование"}
        val p=input.validated();val current=read();val previous=current.find {it.id==p.id}
        check((previous?.revision ?: 0)==p.revision) {"Настройки изменились. Откройте принтер заново"}
        val next=p.copy(revision=Math.addExact(p.revision,1))
        val updated=if(previous==null) current+next else current.map {if(it.id==p.id) next else it}
        val rows=if(next.drawer) updated.map {if(it.id!=next.id && it.drawer) it.copy(drawer=false,revision=Math.addExact(it.revision,1)) else it} else updated
        write(rows);rows
    }
    fun delete(input: PrinterConfiguration): List<PrinterConfiguration> = synchronized(lock) {
        check(authorized()) {"Войдите в администрирование"}
        val current=read();check(current.find {it.id==input.id}?.revision==input.revision) {"Настройки изменились. Откройте принтер заново"}
        val rows=current.filterNot {it.id==input.id};write(rows);rows
    }
    internal fun restore(rows: List<PrinterConfiguration>)=synchronized(lock) {write(rows.map {it.validated()})}
    private fun write(rows: List<PrinterConfiguration>) {
        val array=JSONArray()
        rows.forEach {p->array.put(JSONObject().apply {
            put("id",p.id);put("revision",p.revision);put("name",p.name);put("ip",p.ip);put("paperWidth",p.paperWidth)
            put("printReceipts",p.printReceipts);put("printOrders",p.printOrders);put("paymentComments",p.paymentComments)
            put("receiptTitle",p.receiptTitle);put("registerLabel",p.registerLabel);put("phrases",p.phrases);put("categories",p.categories)
            put("drawer",p.drawer);put("autoReceipt",p.autoReceipt);put("autoKitchen",p.autoKitchen);put("copies",p.copies)
            put("printMode",p.printMode);put("printWidth",p.printWidth);put("dpi",p.dpi);put("initCommand",p.initCommand);put("cutCommand",p.cutCommand);put("drawerCommand",p.drawerCommand);put("enabled",p.enabled)
        })}
        val stream=atomic.startWrite()
        try {stream.write(array.toString().toByteArray(Charsets.UTF_8));atomic.finishWrite(stream)}
        catch(e:Exception) {atomic.failWrite(stream);throw IllegalStateException("Не удалось сохранить конфигурацию принтера",e)}
    }
}
