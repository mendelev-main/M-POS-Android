package com.mendelev.mpos.print

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun interface PrintTransport {fun send(ip: String,bytes: ByteArray)}
class LanPrintTransport: PrintTransport {
    companion object {private val watchdog=Executors.newSingleThreadScheduledExecutor {Thread(it,"printer-timeout").apply {isDaemon=true}}}
    override fun send(ip: String,bytes: ByteArray) {
        Socket().use {socket->
            val timeout=watchdog.schedule({runCatching {socket.close()}},15,TimeUnit.SECONDS)
            try {socket.connect(InetSocketAddress(ip,9100),5000);socket.getOutputStream().apply {write(bytes);flush()}}
            finally {timeout.cancel(false)}
        }
    }
}

object EscPosCommands {
    fun hex(raw: String): ByteArray {
        if(raw.isBlank()) return byteArrayOf()
        val parts=raw.trim().split(Regex("[,\\s]+"))
        require(parts.size<=100 && parts.all {it.matches(Regex("[0-9A-Fa-f]{2}"))}) {"Некорректные HEX-байты"}
        return parts.map {it.toInt(16).toByte()}.toByteArray()
    }
    fun drawer(raw: String): ByteArray {
        val b=hex(raw);val u=b.map {it.toInt() and 255}
        require(u.size==5 && u[0]==27 && u[1]==112 && u[2] in listOf(0,1,48,49) && u[3]>0 && u[4]>=u[3]) {"Команда ящика: 1B,70,00,19,FA (выход 00 или 01)"}
        return b
    }
}

interface PrintClaims {fun claim(key: String): Boolean;fun finish(key: String,status: String)}
data class PrintResult(val sent: Boolean,val message: String,val duplicate: Boolean=false)
/** Once dispatched, a failed/ambiguous network job is never automatically sent again. */
class PrintDispatcher(private val claims: PrintClaims,private val transport: PrintTransport) {
    fun dispatch(key: String,ip: String,bytes: ()->ByteArray): PrintResult {
        // Rendering happens before the claim; no bytes have left the device on a render failure.
        val payload=try {bytes()} catch(e:Exception) {return PrintResult(false,e.message ?: "Не удалось подготовить печать")}
        try {if(!claims.claim(key)) return PrintResult(false,"Задание уже обрабатывалось; автоматический повтор отключён",true)}
        catch(_:Exception) {return PrintResult(false,"Не удалось сохранить задание; печать не отправлялась")}
        return try {
            transport.send(ip,payload)
            claims.finish(key,"sent")
            PrintResult(true,"Данные отправлены на принтер. Проверьте бумажный чек")
        } catch(_:Exception) {
            runCatching {claims.finish(key,"unknown")}
            PrintResult(false,"Не удалось подтвердить отправку. Проверьте принтер и сеть; автоматического повтора нет")
        }
    }
}
