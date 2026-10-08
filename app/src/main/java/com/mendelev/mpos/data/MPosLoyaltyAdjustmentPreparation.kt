package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONObject
import org.json.JSONTokener

/** Fresh identity/config snapshot; the transaction ends before any network request starts. */
class MPosLoyaltyAdjustmentPreparation(private val database:MPosDatabase) {
    suspend fun prepare(raw:String):JSONObject=database.withTransaction {
        val input=JSONObject(raw);require(input.getInt("version")==1 && input.getString("operation")=="adjust")
        val root=MPosRootSessionRepository(database).read();check(root.isAdmin){"Требуются права администратора"}
        val employee=root.selectedEmployee
        val document=MPosWorkspaceStorage(database).read("network")
        val network=MPosAdminSettingsCommand.defaults("network")
        if(document.getBoolean("found")){
            val parser=JSONTokener(document.getString("payload"));val value=parser.nextValue();require(parser.nextClean()=='\u0000')
            require(value===JSONObject.NULL||value is JSONObject)
            if(value is JSONObject)value.keys().forEach{network.put(it,value.get(it))}
        }
        val body=JSONObject().put("programId",input.get("programId")).put("progressDelta",input.get("progressDelta"))
            .put("rewardDelta",input.get("rewardDelta")).put("reason",input.opt("reason"))
            .put("adminEmployeeId",employee?.opt("id") ?: "").put("adminEmployeeName",employee?.opt("name") ?: "")
        require(network.getString("backendUrl").startsWith("https://",ignoreCase=true) && network.getString("deviceKey").isNotBlank())
        val prepared=JSONObject().put("backendUrl",network.getString("backendUrl")).put("deviceKey",network.getString("deviceKey"))
            .put("customerId",input.getString("customerId")).put("body",body)
        prepared.put("verificationToken",MPosLoyaltyAdjustmentVerification(database).begin(prepared,input.getString("customerId"),input.get("programId")))
    }
}
