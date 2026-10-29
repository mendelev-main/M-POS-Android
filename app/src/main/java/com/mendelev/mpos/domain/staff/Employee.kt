package com.mendelev.mpos.domain.staff

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules

/** Stable identity; deleted IDs stay reserved. Historical documents keep their own name snapshot. */
data class Employee(val id: String,val name: String,val phone: String,val role: String,val revision: Long,val deleted: Boolean=false) {
    val selectable: Boolean get()=!deleted && role=="employee"
}
sealed interface EmployeeAction {
    data class Save(val id: String,val revision: Long?,val name: String,val phone: String,val role: String): EmployeeAction
    data class Delete(val id: String,val revision: Long): EmployeeAction
}
data class EmployeeCommand(val commandId: String,val actorId: String,val action: EmployeeAction) {
    fun validated(): EmployeeCommand {
        EmployeeRules.id(commandId);EmployeeRules.id(actorId)
        val a=when(val v=action) {
            is EmployeeAction.Save->{
                EmployeeRules.id(v.id)
                if(v.revision!=null && v.revision<1) throw CatalogFailure("employee","Некорректная версия сотрудника")
                v.copy(name=EmployeeRules.name(v.name),phone=EmployeeRules.phone(v.phone),role=EmployeeRules.role(v.role))
            }
            is EmployeeAction.Delete->{EmployeeRules.id(v.id);if(v.revision<1) throw CatalogFailure("employee","Некорректная версия сотрудника");v}
        }
        return copy(action=a)
    }
    fun fingerprint(): String=when(val a=validated().action) {
        is EmployeeAction.Save->CatalogRules.fingerprint("employee.save.v1",actorId,a.id,a.revision,a.name,a.phone,a.role)
        is EmployeeAction.Delete->CatalogRules.fingerprint("employee.delete.v1",actorId,a.id,a.revision)
    }
}
object EmployeeRules {
    fun id(value: String) {
        if(value.isBlank() || value!=value.trim() || value.length>100 || value.any {it.isISOControl()}) throw CatalogFailure("employee","Некорректный идентификатор сотрудника или команды")
    }
    fun name(value: String): String {
        val name=value.trim()
        if(name.isEmpty() || name.codePointCount(0,name.length)>200 || name.any {it.isISOControl()}) throw CatalogFailure("name","Введите ФИО сотрудника от 1 до 200 символов")
        return name
    }
    fun phone(value: String): String {
        val phone=value.trim()
        if(phone.length>64 || phone.any {it.isISOControl()}) throw CatalogFailure("phone","Телефон — до 64 символов без управляющих знаков")
        return phone
    }
    fun role(value: String): String {
        if(value !in setOf("employee","admin")) throw CatalogFailure("role","Выберите сотрудника или администратора")
        return value
    }
    fun validate(record: Employee) {
        id(record.id)
        if(record.revision<1 || name(record.name)!=record.name || phone(record.phone)!=record.phone) throw CatalogFailure("employee","Некорректная запись сотрудника")
        role(record.role)
    }
    /** Source employeeDisplayName, including hyphenated given names. This is presentation only. */
    fun displayName(name: String): String {
        val parts=name.trim().split(Regex("\\s+")).filter {it.isNotEmpty()}
        return when(parts.size) {
            0->"Сотрудник"
            1->parts[0]
            else->parts[0]+" "+parts.drop(1).take(2).joinToString(" ") {part->
                part.split('-').filter {it.isNotEmpty()}.joinToString("-") {piece->String(Character.toChars(piece.codePointAt(0))).uppercase(java.util.Locale.forLanguageTag("ru"))+"."}
            }
        }
    }
}
