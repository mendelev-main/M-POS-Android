package com.mendelev.mpos.data.local

import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.settings.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SettingsCodec {
    const val KEY="settings-catalog"
    fun encode(c: SettingsCatalog)=JSONObject().put("version",1).put("entries",JSONArray(c.entries.map {
        JSONObject().put("id",it.id).put("kind",it.kind.name).put("name",it.name).put("type",it.type).put("value",it.value)
    })).toString()
    fun decode(raw: String,revision: Long): SettingsCatalog {
        val j=JSONObject(raw);require(j.getInt("version")==1 && revision>=0)
        val a=j.getJSONArray("entries");var result=SettingsCatalog(revision)
        for(i in 0 until a.length()) {
            val v=a.getJSONObject(i);val row=SettingEntry(v.getString("id"),SettingKind.valueOf(v.getString("kind")),v.getString("name"),v.getString("type"),v.getString("value"))
            require(result.entries.none {it.id==row.id});require(SettingsRules.validate(row)==row)
            result=SettingsRules.save(result,row)
        }
        return result
    }
}
class SettingsRepository(private val db: CatalogDatabase,private val afterWrite: (() -> Unit)?=null) {
    suspend fun snapshot(): SettingsCatalog=db.workspace().document(SettingsCodec.KEY)?.let {SettingsCodec.decode(it.payload,it.revision)} ?: SettingsCatalog()
    fun observe(): Flow<SettingsCatalog> = db.workspace().observeDocument(SettingsCodec.KEY).map {row->row?.let {SettingsCodec.decode(it.payload,it.revision)} ?: SettingsCatalog()}.distinctUntilChanged()
    suspend fun save(row: SettingEntry,expected: Long,command: String)=change(row,null,expected,command)
    suspend fun delete(id: String,expected: Long,command: String)=change(null,id,expected,command)
    private suspend fun change(row: SettingEntry?,deleted: String?,expected: Long,command: String) {
        val valid=row?.let(SettingsRules::validate)
        val fingerprint=CatalogRules.fingerprint("settings.v1",SettingsCodec.encode(SettingsCatalog(entries=listOfNotNull(valid))),deleted.orEmpty(),expected.toString())
        InstallationGate.write {db.adminTransaction {
            db.catalog().result(command)?.let {if(it.fingerprint!=fingerprint) throw CatalogFailure("conflict","Команда уже использована");return@adminTransaction}
            val current=snapshot()
            if(current.revision!=expected) throw CatalogFailure("conflict","Настройки изменились. Откройте форму заново")
            val next=if(valid!=null) SettingsRules.save(current,valid) else current.copy(entries=current.entries.filterNot {it.id==deleted})
            db.workspace().put(WorkspaceDocument(SettingsCodec.KEY,Math.addExact(expected,1),SettingsCodec.encode(next)))
            afterWrite?.invoke()
            db.catalog().result(ResultEntity(command,fingerprint,valid?.id ?: deleted.orEmpty()))
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,db.catalog().config()!!.ownerId,if(valid==null) "settings.delete" else "settings.save",valid?.id ?: deleted.orEmpty(),System.currentTimeMillis()))
        }}
    }
}
