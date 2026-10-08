package com.mendelev.mpos.workspace

import org.json.JSONObject

/** UI-thread native overlay state, updated by lifecycle transitions, never by inspecting DOM. */
class MPosBackStateOwner {
    private var snapshot:JSONObject?=null
    fun reset(){snapshot=null}
    fun update(input:JSONObject) {
        require(input.getInt("version")==1)
        require(listOf("pendingImport","modal","warehouse","receiving").all{input.get(it) is Boolean})
        val revision=input.getLong("revision");require(revision>0)
        if(revision<=(snapshot?.getLong("revision")?:0))return
        snapshot=JSONObject(input.toString()).put("native",true)
    }
    fun capture():JSONObject?=snapshot?.takeIf{it.optBoolean("enabled",true)}?.let{JSONObject(it.toString())}
    fun isCurrent(revision:Long):Boolean=snapshot?.optLong("revision")==revision
}
