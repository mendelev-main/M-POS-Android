package com.mendelev.mpos.domain.settings

import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

enum class SettingKind { DISCOUNT, DELIVERY, RECEIPT_LABEL }
data class SettingEntry(val id: String,val kind: SettingKind,val name: String,val type: String="",val value: String="")
data class SettingsCatalog(val revision: Long=0,val entries: List<SettingEntry> = emptyList()) {
    fun rows(kind: SettingKind)=entries.filter {it.kind==kind}
}
object SettingsRules {
    private fun invalid(message: String): Nothing=throw CatalogFailure("settings",message)
    fun key(name: String)=Normalizer.normalize(name.trim(),Normalizer.Form.NFC).lowercase(Locale.forLanguageTag("ru")).replace(Regex("[\\p{Z}\\s]+")," ")
    fun validate(row: SettingEntry): SettingEntry {
        if(row.id.isBlank() || row.id.length>100) invalid("Некорректный идентификатор")
        val name=row.name.trim()
        if(name.isEmpty() || name.length>(if(row.kind==SettingKind.RECEIPT_LABEL) 80 else 100) || name.any {it.isISOControl()}) invalid("Проверьте название: до ${if(row.kind==SettingKind.RECEIPT_LABEL) 80 else 100} символов")
        if(row.kind==SettingKind.RECEIPT_LABEL) {
            if(row.type.isNotEmpty() || row.value.isNotEmpty()) invalid("Некорректный шаблон")
            return row.copy(name=name)
        }
        val value=row.value.trim().replace(',','.').toBigDecimalOrNull() ?: invalid("Укажите размер")
        if(value.stripTrailingZeros().scale()>2 || value<BigDecimal.ZERO || value>BigDecimal("9999999.99")) invalid("Размер должен быть от 0 до 9999999,99 с точностью до копейки")
        if(row.kind==SettingKind.DISCOUNT) {
            if(row.type !in listOf("percent","fixed") || value.signum()<=0) invalid("Укажите положительную скидку")
            if(row.type=="percent" && value>BigDecimal(100)) invalid("Процент не может быть больше 100%")
        } else if(row.type.isNotEmpty()) invalid("Некорректный тариф")
        return row.copy(name=name,value=value.stripTrailingZeros().toPlainString())
    }
    fun save(catalog: SettingsCatalog,row: SettingEntry): SettingsCatalog {
        val valid=validate(row)
        if(valid.kind==SettingKind.RECEIPT_LABEL && catalog.entries.any {it.id!=valid.id && it.kind==valid.kind && key(it.name)==key(valid.name)}) invalid("Такой шаблон уже есть")
        val old=catalog.entries.find {it.id==row.id}
        if(old!=null && old.kind!=row.kind) invalid("Тип записи изменился")
        return catalog.copy(entries=if(old==null) catalog.entries+valid else catalog.entries.map {if(it.id==valid.id) valid else it})
    }
}
