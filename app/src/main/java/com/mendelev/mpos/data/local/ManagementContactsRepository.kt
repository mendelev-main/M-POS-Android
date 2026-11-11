package com.mendelev.mpos.data.local

import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.operations.InstallationGate
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class ManagementContact(val id: String,val phone: String="",val email: String="",val telegramId: String="")
data class ManagementContacts(val revision: Long=0,val owner: ManagementContact=ManagementContact("owner"),val administrators: List<ManagementContact> = emptyList())

object ManagementContactsCodec {
    const val KEY="management.contacts.v1"
    fun validate(c: ManagementContacts): ManagementContacts {
        require(c.revision>=0 && c.owner.id=="owner" && c.administrators.size<=100) {"Некорректные контакты"}
        val rows=listOf(c.owner)+c.administrators
        require(rows.map {it.id}.distinct().size==rows.size && rows.all {it.id.isNotBlank() && it.id.length<=100}) {"Повторяющийся контакт"}
        rows.forEach {
            require(it.phone.length<=64 && it.email.length<=254 && it.telegramId.length<=64) {"Слишком длинное значение"}
            require(it.email.isBlank() || Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(it.email)) {"Проверьте почту"}
            require(it.telegramId.isBlank() || Regex("-?[0-9]+").matches(it.telegramId)) {"Telegram ID должен содержать цифры"}
        }
        return c
    }
    private fun json(c: ManagementContact)=JSONObject().put("id",c.id).put("phone",c.phone).put("email",c.email).put("telegramId",c.telegramId)
    private fun contact(j: JSONObject)=ManagementContact(j.getString("id"),j.getString("phone"),j.getString("email"),j.getString("telegramId"))
    fun encode(c: ManagementContacts): String {validate(c);return JSONObject().put("version",1).put("owner",json(c.owner)).put("administrators",JSONArray(c.administrators.map(::json))).toString()}
    fun decode(raw: String,revision: Long): ManagementContacts {
        val j=JSONObject(raw);require(j.getInt("version")==1) {"Неизвестная версия контактов"}
        val a=j.getJSONArray("administrators")
        return validate(ManagementContacts(revision,contact(j.getJSONObject("owner")),List(a.length()) {contact(a.getJSONObject(it))}))
    }
}

class ManagementContactsRepository(private val db: CatalogDatabase,private val afterWrite: (() -> Unit)?=null) {
    suspend fun read(): ManagementContacts = db.workspace().document(ManagementContactsCodec.KEY)?.let {ManagementContactsCodec.decode(it.payload,it.revision)} ?: ManagementContacts()
    fun observe()=db.workspace().observeDocument(ManagementContactsCodec.KEY).map {it?.let {row->ManagementContactsCodec.decode(row.payload,row.revision)} ?: ManagementContacts()}
    suspend fun save(value: ManagementContacts,generation: Long) {
        val payload=ManagementContactsCodec.encode(value)
        InstallationGate.write {db.adminTransaction {
            check(Administration.session.state.value.generation==generation) {"Сессия администратора завершена"}
            check(read().revision==value.revision) {"Контакты изменились. Откройте окно заново"}
            db.workspace().put(WorkspaceDocument(ManagementContactsCodec.KEY,Math.addExact(value.revision,1),payload))
            afterWrite?.invoke()
        }}
    }
}
