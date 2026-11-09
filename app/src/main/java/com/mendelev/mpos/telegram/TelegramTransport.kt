package com.mendelev.mpos.telegram

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class TelegramAttachment(val name: String,val mediaType: String,val bytes: ByteArray) {
    override fun toString()="TelegramAttachment(name=$name, bytes=REDACTED)"
}
data class TelegramMessage(val text: String,val attachment: TelegramAttachment?=null,val album: List<TelegramAttachment> = emptyList())
fun interface TelegramTransport {fun send(settings: TelegramSettings,message: TelegramMessage)}
class TelegramFailure(val safeMessage: String,val definite: Boolean=false,val retryAfterSeconds: Int?=null): Exception(safeMessage) {
    companion object {
        fun response(status: Int,raw: String): TelegramFailure {
            val p=runCatching {JSONObject(raw)}.getOrNull()
            val code=p?.optInt("error_code",status) ?: status
            val description=p?.optString("description").orEmpty().lowercase()
            val safe=when {
                code in listOf(401,404)->"Telegram отклонил токен бота. Проверьте актуальный токен"
                code==403->"Боту запрещено писать в чат. Добавьте его в группу или нажмите Start в личном чате"
                code==400 && "thread" in description->"Тема Telegram не найдена. Проверьте ID темы"
                code==400 && "chat" in description->"Чат Telegram не найден. Проверьте ID группы и участие бота"
                code==429->"Telegram ограничил отправку. Повторите позже${p?.optJSONObject("parameters")?.optInt("retry_after",0)?.takeIf {it>0}?.let {": через $it с"}.orEmpty()}"
                else->"Telegram не подтвердил отправку. Проверьте чат перед повтором"
            }
            return TelegramFailure(safe,code in 400..499,if(code==429) (p?.optJSONObject("parameters")?.optInt("retry_after",60) ?: 60).coerceIn(1,86400) else null)
        }
        fun safe(error: Throwable)=when(error) {
            is TelegramFailure->error.safeMessage
            is java.net.UnknownHostException->"Не найден адрес Telegram. Проверьте интернет и DNS"
            is javax.net.ssl.SSLException->"Не удалось проверить HTTPS-сертификат Telegram"
            is java.io.InterruptedIOException->"Telegram не ответил вовремя. Проверьте чат перед повтором"
            else->"Не удалось подтвердить отправку в Telegram. Проверьте интернет и чат перед повтором"
        }
    }
}
class TelegramHttpTransport(
    private val client: OkHttpClient=OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).writeTimeout(12,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(),
    private val baseUrl: HttpUrl="https://api.telegram.org/".toHttpUrl()
): TelegramTransport {
    override fun send(settings: TelegramSettings,message: TelegramMessage) {
        val s=settings.validated(test=true)
        require(message.text.isNotBlank() && message.text.length<=if(message.attachment==null && message.album.isEmpty())4096 else 1024) {"Слишком длинное сообщение Telegram"}
        val attachment=message.attachment
        require(message.album.isEmpty() || attachment==null && message.album.size in 2..10 && message.album.all {it.mediaType=="image/png" && it.bytes.size in 1..10_000_000}) {"Альбом должен содержать от 2 до 10 изображений"}
        val method=if(message.album.isNotEmpty()) "sendMediaGroup" else when(attachment?.mediaType) {null->"sendMessage";"image/png"->"sendPhoto";"application/pdf"->"sendDocument";else->error("Неподдерживаемый формат Telegram")}
        val body=if(message.album.isNotEmpty()) MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            addFormDataPart("chat_id",s.chatId)
            if(s.threadId.isNotEmpty()) addFormDataPart("message_thread_id",s.threadId)
            val media=JSONArray()
            message.album.forEachIndexed {index,image->
                val part="page_$index"
                media.put(JSONObject().put("type","photo").put("media","attach://$part").apply {if(index==0) {put("caption",message.text);put("parse_mode","HTML")}})
                addFormDataPart(part,image.name,image.bytes.toRequestBody("image/png".toMediaType()))
            }
            addFormDataPart("media",media.toString())
        }.build() else if(attachment==null) FormBody.Builder().apply {
            add("chat_id",s.chatId);add("text",message.text);add("parse_mode","HTML");if(s.threadId.isNotEmpty()) add("message_thread_id",s.threadId)
        }.build() else MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            require(attachment.bytes.size in 1..(if(attachment.mediaType=="image/png")10_000_000 else 50_000_000)) {"Файл слишком большой для Telegram"}
            addFormDataPart("chat_id",s.chatId);addFormDataPart("caption",message.text);addFormDataPart("parse_mode","HTML")
            if(s.threadId.isNotEmpty()) addFormDataPart("message_thread_id",s.threadId)
            addFormDataPart(if(method=="sendPhoto")"photo" else "document",attachment.name,attachment.bytes.toRequestBody(attachment.mediaType.toMediaType()))
        }.build()
        val url=baseUrl.newBuilder().addPathSegment("bot${s.botToken}").addPathSegment(method).build()
        try {client.newCall(Request.Builder().url(url).post(body).build()).execute().use {r->
            val source=r.body?.source();val raw=source?.let {it.request(65537);it.readUtf8(minOf(it.buffer.size,65536))}.orEmpty()
            if(!r.isSuccessful || !runCatching {JSONObject(raw).optBoolean("ok")}.getOrDefault(false)) throw TelegramFailure.response(r.code,raw)
        }} catch(e:TelegramFailure) {throw e} catch(e:Exception) {throw TelegramFailure(TelegramFailure.safe(e))}
    }
}

interface TelegramDeliveryJournal {fun state(key: String): String?;fun put(key: String,state: String)}
data class TelegramDeliveryResult(val sent: Boolean,val message: String,val skipped: Boolean=false)
/** A durable sending marker precedes I/O; uncertain outcomes require an explicit user retry. */
class TelegramDelivery(private val journal: TelegramDeliveryJournal,private val transport: TelegramTransport,private val now: ()->Long={System.currentTimeMillis()}) {
    @Synchronized fun send(key: String,s: TelegramSettings,explicitRetry: Boolean=false,message: ()->TelegramMessage): TelegramDeliveryResult {
        try {
            val previous=journal.state(key)
            val retryDue=previous?.startsWith("retry:")==true && (previous.substringAfter(":").toLongOrNull() ?: Long.MAX_VALUE)<=now()
            if(previous=="sent" || !explicitRetry && previous!=null && !retryDue) return TelegramDeliveryResult(previous=="sent","Задание уже обрабатывалось",true)
            val payload=message()
            journal.put(key,"sending")
            try {transport.send(s,payload);journal.put(key,"sent");return TelegramDeliveryResult(true,"Отправлено в Telegram")}
            catch(e:Exception) {runCatching {journal.put(key,if(e is TelegramFailure && e.retryAfterSeconds!=null) "retry:${now()+e.retryAfterSeconds*1000L}" else if(e is TelegramFailure && e.definite) "rejected" else "unknown")};return TelegramDeliveryResult(false,TelegramFailure.safe(e))}
        } catch(_:Exception) {return TelegramDeliveryResult(false,"Не удалось подготовить или сохранить задание Telegram; отправка не запускалась")}
    }
}
