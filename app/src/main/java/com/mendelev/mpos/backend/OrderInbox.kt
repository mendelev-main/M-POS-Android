package com.mendelev.mpos.backend

import android.util.AtomicFile
import com.mendelev.mpos.telegram.TelegramSecretCipher
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Reconstructible network inbox, encrypted and scoped to both backend and active dataset. */
class OrderInbox(file: File,private val cipher: TelegramSecretCipher) {
    companion object {private val lock=Any()}
    private val atomic=AtomicFile(file)
    fun read(): List<JSONObject> = synchronized(lock) {
        if(!atomic.baseFile.exists() && !File(atomic.baseFile.path+".bak").exists()) return@synchronized emptyList()
        val raw=cipher.decrypt(atomic.openRead().use {it.readBytes()});require(raw.size<=16_000_000)
        val j=JSONObject(raw.toString(Charsets.UTF_8));require(j.getInt("version")==1)
        val a=j.getJSONArray("orders");require(a.length()<=1000)
        List(a.length()) {a.getJSONObject(it)}
    }
    fun merge(incoming: List<JSONObject>,accepted: Set<String>): List<JSONObject> = synchronized(lock) {
        val previous=read()
        val merged=(incoming+previous).distinctBy {it.getString("id")}.filter {it.getString("id") !in accepted && it.getString("status")=="new"}
            .sortedByDescending {it.optString("created_at")}
        require(merged.size<=1000) {"Накопилось слишком много заказов сайта. Обработайте список событий"}
        if(merged.map {it.toString()}==previous.map {it.toString()}) return@synchronized previous
        val raw=JSONObject().put("version",1).put("orders",JSONArray(merged)).toString().toByteArray();require(raw.size<=16_000_000)
        val encrypted=cipher.encrypt(raw);val output=atomic.startWrite()
        try {output.write(encrypted);atomic.finishWrite(output)} catch(e:Exception) {atomic.failWrite(output);throw e}
        merged
    }
}
