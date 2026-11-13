package com.mendelev.mpos.backend

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** M-POS HTTP protocol; a different host need not be the original deployment. */
data class BackendSettings(
    val revision: Long=0,val url: String="",val deviceName: String="M POS Android",val deviceKey: String="",
    val healthPath: String="/health",val statePath: String="/api/operational/state",val eventsPath: String="/api/orders/events",
    val menuPath: String="/api/menu/sync",val mediaPath: String="/api/media/upload",
    val tariffsPath: String="/api/device/delivery-tariffs",val availabilityPath: String="/api/availability/snapshot",
    val timeoutSeconds: Int=15,val ordersPath: String="/api/orders",val receiveOrders: Boolean=false,val datasetId: String="",val operationalPath: String="/api/operational/snapshot",val heartbeatPath: String="/api/operational/heartbeat",
    val loyaltyPath: String="/api/loyalty",val customersPath: String="/api/customers",val loyaltyTermsPath: String="/api/device/loyalty/terms",val telegramSettingsPath: String="/api/device/telegram-settings"
) {
    fun validatedOrDraft(): BackendSettings {
        if(url.isNotBlank() && deviceKey.isNotBlank()) return validated()
        val checked=copy(url=url.ifBlank {"https://unconfigured.invalid"},deviceKey=deviceKey.ifBlank {"unconfigured"}).validated()
        return checked.copy(url=url.trim(),deviceKey=deviceKey.trim(),receiveOrders=false)
    }
    fun validated(): BackendSettings {
        val address=url.trim().trimEnd('/')
        val parsed=address.toHttpUrlOrNull()
        require(parsed!=null && parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query==null && parsed.fragment==null) {"Укажите HTTPS адрес backend без параметров и пароля"}
        require(deviceName.trim().isNotEmpty() && deviceName.length<=100 && deviceName.none {it.isISOControl()}) {"Укажите название устройства до 100 символов"}
        require(deviceKey.trim().length in 8..512 && deviceKey.trim().all {it.code in 33..126}) {"Укажите ключ устройства: от 8 до 512 символов без пробелов"}
        require(timeoutSeconds in 5..60) {"Время ожидания: от 5 до 60 секунд"}
        listOf(healthPath,statePath,eventsPath,menuPath,mediaPath,tariffsPath,availabilityPath,ordersPath,operationalPath,heartbeatPath,loyaltyPath,customersPath,loyaltyTermsPath,telegramSettingsPath).forEach {path->
            require(path.startsWith('/') && !path.startsWith("//") && path.length<=200 && path.none {it.isWhitespace() || it in "?#\\"} && path.split('/').none {it==".." || it=="."} && '%' !in path) {"Маршрут должен начинаться с / и не содержать адрес, параметры или переходы между каталогами"}
        }
        require(datasetId.length<=100 && datasetId.all {it.isLetterOrDigit() || it in "-_"})
        require(revision>=0)
        return copy(url=parsed.toString().trimEnd('/'),deviceName=deviceName.trim(),deviceKey=deviceKey.trim())
    }
    override fun toString()="BackendSettings(revision=$revision, configured=${url.isNotBlank()}, credentials=redacted)"
    fun sameTarget(other: BackendSettings)=url==other.url && deviceKey==other.deviceKey && ordersPath==other.ordersPath && loyaltyPath==other.loyaltyPath && customersPath==other.customersPath && loyaltyTermsPath==other.loyaltyTermsPath
    fun target(): String=java.security.MessageDigest.getInstance("SHA-256").digest((url+"\n"+deviceKey+"\n"+ordersPath).toByteArray()).joinToString("") {"%02x".format(it)}
    fun loyaltyTarget(): String=java.security.MessageDigest.getInstance("SHA-256").digest((url+"\n"+deviceKey+"\n"+loyaltyPath+"\n"+customersPath+"\n"+loyaltyTermsPath).toByteArray()).joinToString("") {"%02x".format(it)}
}

object BackendSettingsCodec {
    fun encode(s: BackendSettings)=JSONObject().apply {
        put("version",1);put("revision",s.revision);put("url",s.url);put("deviceName",s.deviceName);put("deviceKey",s.deviceKey)
        put("healthPath",s.healthPath);put("statePath",s.statePath);put("eventsPath",s.eventsPath);put("menuPath",s.menuPath)
        put("mediaPath",s.mediaPath);put("tariffsPath",s.tariffsPath);put("availabilityPath",s.availabilityPath);put("timeoutSeconds",s.timeoutSeconds);put("ordersPath",s.ordersPath);put("receiveOrders",s.receiveOrders);put("datasetId",s.datasetId);put("operationalPath",s.operationalPath);put("heartbeatPath",s.heartbeatPath)
        put("loyaltyPath",s.loyaltyPath);put("customersPath",s.customersPath);put("loyaltyTermsPath",s.loyaltyTermsPath);put("telegramSettingsPath",s.telegramSettingsPath)
    }.toString()
    fun decode(raw: String): BackendSettings {
        val j=JSONObject(raw);require(j.getInt("version")==1)
        return BackendSettings(j.getLong("revision"),j.getString("url"),j.getString("deviceName"),j.getString("deviceKey"),j.getString("healthPath"),j.getString("statePath"),j.getString("eventsPath"),j.getString("menuPath"),j.getString("mediaPath"),j.getString("tariffsPath"),j.getString("availabilityPath"),j.getInt("timeoutSeconds"),j.optString("ordersPath","/api/orders"),j.optBoolean("receiveOrders"),j.optString("datasetId"),j.optString("operationalPath","/api/operational/snapshot"),j.optString("heartbeatPath","/api/operational/heartbeat"),j.optString("loyaltyPath","/api/loyalty"),j.optString("customersPath","/api/customers"),j.optString("loyaltyTermsPath","/api/device/loyalty/terms"),j.optString("telegramSettingsPath","/api/device/telegram-settings")).validatedOrDraft()
    }
}
