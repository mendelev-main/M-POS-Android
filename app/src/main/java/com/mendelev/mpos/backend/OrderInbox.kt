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
    private fun snapshot(): InboxSnapshot {
        if(!atomic.baseFile.exists() && !File(atomic.baseFile.path+".bak").exists()) return InboxSnapshot()
        val raw=cipher.decrypt(atomic.openRead().use {it.readBytes()});require(raw.size<=16_000_000)
        return InboxSnapshot.decode(raw.toString(Charsets.UTF_8))
    }
    fun isRemoved(id: String): Boolean = synchronized(lock) {id in snapshot().removed}
    fun read(): List<JSONObject> = synchronized(lock) {snapshot().orders}
    fun merge(incoming: List<JSONObject>,accepted: Set<String>): List<JSONObject> = synchronized(lock) {
        val previous=snapshot();val next=previous.merge(incoming,accepted)
        if(next.encode()!=previous.encode()) write(next)
        next.orders
    }
    fun remove(id: String): List<JSONObject> = synchronized(lock) {
        val previous=snapshot();val next=previous.remove(id)
        if(next.encode()!=previous.encode()) write(next)
        next.orders
    }
    private fun write(state: InboxSnapshot) {
        require(state.removed.size<=100000)
        val raw=state.encode().toByteArray();require(raw.size<=16_000_000)
        val encrypted=cipher.encrypt(raw);val output=atomic.startWrite()
        try {output.write(encrypted);atomic.finishWrite(output)} catch(e:Exception) {atomic.failWrite(output);throw e}
    }
}
