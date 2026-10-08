package com.mendelev.mpos.data

import androidx.room.withTransaction
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONObject
import org.json.JSONTokener

/** Live shift/role authorization and settings acknowledgement share one local transaction. */
class MPosAdminSettingsCommand(private val database: MPosDatabase) {
    suspend fun commit(raw: String): JSONObject = database.withTransaction {
        val input=JSONObject(raw);require(input.getInt("version")==1)
        val key=input.getString("key");require(key in setOf("network","telegram"))
        check(MPosRootSessionRepository(database).read().isAdmin){"Сетевые конфигурации доступны только администратору"}
        val storage=MPosWorkspaceStorage(database);val document=storage.read(key)
        val current=defaults(key)
        if(document.getBoolean("found")){
            val parser=JSONTokener(document.getString("payload"));val value=parser.nextValue();require(parser.nextClean()=='\u0000')
            require(value===JSONObject.NULL||value is JSONObject)
            if(value is JSONObject)value.keys().forEach{current.put(it,value.get(it))}
        }
        val expected = input.getJSONObject("expected")
        // Reviewed startup creates an in-memory device key even when the original document is absent.
        // Adopt that key on its first durable user save, while still comparing every other field.
        if(key=="network" && !MPosJsonNumbers.truthy(current.opt("deviceKey")) && MPosJsonNumbers.truthy(expected.opt("deviceKey"))) {
            current.put("deviceKey",expected.getString("deviceKey"))
        }
        check(MPosSupplyParity.same(current,expected)){"Настройки изменились. Откройте форму заново"}
        val fields=input.getJSONObject("fields")
        val next=if(key=="network"){
            require(fields.keys().asSequence().toSet()==setOf("backendUrl","deviceName"))
            val url=MPosOrderContextEngine.trim(fields.getString("backendUrl")).trimEnd('/')
            check(url.startsWith("https://",ignoreCase=true)){"Адрес backend должен начинаться с https://"}
            current.put("backendUrl",url).put("deviceName",MPosOrderContextEngine.trim(fields.getString("deviceName")).ifEmpty{"M POS iPad"})
                .apply{if(!MPosJsonNumbers.truthy(opt("deviceKey")))put("deviceKey",java.util.UUID.randomUUID().toString())}
        }else{
            val strings=listOf("botToken","chatId","threadId","deviceChatId","ownerChatId")
            val flags=listOf("enabled","notifyOnlineOrders","notifyShiftOpened","notifyShiftClosed","notifyMonthlyWarehouse")
            require(fields.keys().asSequence().toSet()==(strings+flags).toSet())
            JSONObject().apply {
                for(field in strings)put(field,MPosOrderContextEngine.trim(fields.getString(field)))
                for(field in flags){require(fields.get(field) is Boolean);put(field,fields.getBoolean(field))}
                put("lastMonthlyWarehouseSent",if(MPosJsonNumbers.truthy(current.opt("lastMonthlyWarehouseSent")))current.get("lastMonthlyWarehouseSent") else "")
                check(!getBoolean("enabled")||!getBoolean("notifyOnlineOrders")||getString("deviceChatId").matches(Regex("\\d{1,20}"))){"Укажите корректный ID рабочего устройства"}
                check(getString("ownerChatId").isEmpty()||getString("ownerChatId").matches(Regex("\\d{1,20}"))){"Укажите корректный ID владельца"}
            }
        }
        storage.write(key,next.toString())
        JSONObject().put("ok",true).put("authoritative",true).put("settings",next).put("key",key)
    }
    companion object {
        fun defaults(key:String):JSONObject=if(key=="network")JSONObject().put("backendUrl","https://project-dubrovno.up.railway.app")
            .put("deviceKey","").put("deviceName","M POS iPad").put("lastSyncAt",JSONObject.NULL).put("lastSyncStatus","")
        else JSONObject().put("enabled",false).put("botToken","").put("chatId","").put("threadId","").put("deviceChatId","").put("ownerChatId","")
            .put("notifyOnlineOrders",false).put("notifyShiftOpened",true).put("notifyShiftClosed",true).put("notifyMonthlyWarehouse",false).put("lastMonthlyWarehouseSent","")
    }
}
