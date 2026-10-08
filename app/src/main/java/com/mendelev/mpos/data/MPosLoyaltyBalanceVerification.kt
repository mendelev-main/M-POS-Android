package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONObject
import org.json.JSONTokener

/** Only a successful native profile read for the exact program can release its uncertainty gate. */
class MPosLoyaltyBalanceVerification(private val database:MPosDatabase) {
    suspend fun context(customerId:String,requireAdmin:Boolean=true):JSONObject=database.withTransaction {
        val root=MPosRootSessionRepository(database).read()
        check(!requireAdmin||root.isAdmin){"Требуются права администратора"}
        val document=MPosWorkspaceStorage(database).read("network")
        val config=MPosAdminSettingsCommand.defaults("network")
        if(document.getBoolean("found")){
            val parser=JSONTokener(document.getString("payload"));val value=parser.nextValue();require(parser.nextClean()=='\u0000')
            require(value===JSONObject.NULL||value is JSONObject)
            if(value is JSONObject)value.keys().forEach{config.put(it,value.get(it))}
        }
        config.put("customerId",customerId)
    }
    suspend fun complete(config:JSONObject,customerId:String,programId:Any,token:String?,data:JSONObject):JSONObject {
        val programs=data.getJSONArray("programs")
        val program=(0 until programs.length()).map{programs.getJSONObject(it)}.firstOrNull{MPosSupplyParity.same(it.opt("id"),programId)}
            ?: error("Не удалось подтвердить баланс программы")
        if(token!=null)MPosLoyaltyAdjustmentVerification(database).finish(config,customerId,programId,token)
        return JSONObject(program.toString())
    }
}
