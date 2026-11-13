package com.mendelev.mpos.telegram

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TelegramSecretCipher {fun encrypt(bytes: ByteArray): ByteArray;fun decrypt(bytes: ByteArray): ByteArray}
class TelegramKeystoreCipher(private val alias: String="mpos.telegram.settings.v1"): TelegramSecretCipher {
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return (store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun encrypt(bytes: ByteArray): ByteArray=Cipher.getInstance("AES/GCM/NoPadding").run {init(Cipher.ENCRYPT_MODE,key());byteArrayOf(iv.size.toByte())+iv+doFinal(bytes)}
    override fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size>28 && bytes[0].toInt()==12) {"Повреждён файл настроек Telegram"}
        return Cipher.getInstance("AES/GCM/NoPadding").run {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)));doFinal(bytes.copyOfRange(13,bytes.size))}
    }
}
object TelegramSettingsCodec {
    fun encode(s: TelegramSettings)=JSONObject().apply {
        put("version",1);put("revision",s.revision);put("enabled",s.enabled);put("botToken",s.botToken);put("chatId",s.chatId);put("threadId",s.threadId)
        put("deviceChatId",s.deviceChatId);put("ownerChatId",s.ownerChatId);put("onlineOrders",s.onlineOrders);put("shiftOpened",s.shiftOpened);put("shiftClosed",s.shiftClosed);put("monthlyWarehouse",s.monthlyWarehouse)
        put("backendUrl",s.backendUrl);put("deviceKey",s.deviceKey);put("ownerShiftStats",s.ownerShiftStats);put("ownerMonthlyStats",s.ownerMonthlyStats);put("onlineOrdersConfigured",s.onlineOrdersConfigured)
        put("ownerMonthReport",s.ownerMonthReport);put("ownerQuarterReport",s.ownerQuarterReport);put("ownerMonthSince",s.ownerMonthSince);put("ownerQuarterSince",s.ownerQuarterSince)
    }.toString()
    fun decode(raw: String): TelegramSettings {
        val p=JSONObject(raw);require(p.getInt("version")==1) {"Неизвестная версия настроек Telegram"}
        return TelegramSettings(p.getLong("revision"),p.getBoolean("enabled"),p.getString("botToken"),p.getString("chatId"),p.getString("threadId"),p.getString("deviceChatId"),p.getString("ownerChatId"),p.getBoolean("onlineOrders"),p.getBoolean("shiftOpened"),p.getBoolean("shiftClosed"),p.getBoolean("monthlyWarehouse"),p.getString("backendUrl"),p.getString("deviceKey"),p.optBoolean("ownerShiftStats",false),p.optBoolean("ownerMonthlyStats",false),p.optBoolean("onlineOrdersConfigured",false),p.optBoolean("ownerMonthReport",false),p.optBoolean("ownerQuarterReport",false),p.optLong("ownerMonthSince",0),p.optLong("ownerQuarterSince",0)).validated()
    }
}
class TelegramSettingsStore(file: File,private val cipher: TelegramSecretCipher,private val authorized: ()->Boolean) {
    companion object {private val lock=Any()}
    private val atomic=AtomicFile(file)
    fun read(): TelegramSettings=synchronized(lock) {
        if(!atomic.baseFile.exists() && !File(atomic.baseFile.path+".bak").exists()) return@synchronized TelegramSettings()
        TelegramSettingsCodec.decode(cipher.decrypt(atomic.openRead().use {it.readBytes()}).toString(Charsets.UTF_8))
    }
    fun save(input: TelegramSettings): TelegramSettings=synchronized(lock) {
        check(authorized()) {"Войдите в администрирование"}
        val previous=read();val next=input.validated();check(previous.revision==next.revision) {"Настройки Telegram изменились. Откройте окно заново"}
        val now=System.currentTimeMillis()
        val saved=next.copy(revision=Math.addExact(next.revision,1),
            ownerMonthSince=if(next.enabled && next.ownerMonthReport) {if(!previous.enabled || !previous.ownerMonthReport || previous.ownerMonthSince==0L) now else previous.ownerMonthSince} else 0,
            ownerQuarterSince=if(next.enabled && next.ownerQuarterReport) {if(!previous.enabled || !previous.ownerQuarterReport || previous.ownerQuarterSince==0L) now else previous.ownerQuarterSince} else 0)
        val bytes=cipher.encrypt(TelegramSettingsCodec.encode(saved).toByteArray())
        val stream=atomic.startWrite()
        try {stream.write(bytes);atomic.finishWrite(stream)} catch(e:Exception) {atomic.failWrite(stream);throw e}
        saved
    }
}
