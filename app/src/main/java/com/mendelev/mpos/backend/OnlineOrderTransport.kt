package com.mendelev.mpos.backend

import com.mendelev.mpos.data.local.OnlineOrderCodec
import org.json.JSONObject

/** Web protocol: these commands also drive server-side customer notifications. */
internal class OnlineOrderTransport(private val http: BackendHttp=BackendHttp()) {
    fun accept(settings: BackendSettings,id: String,estimate: String): JSONObject {
        require(OnlineOrderCodec.estimate(estimate)) {"Выберите время готовности"}
        return http.json(settings,path(settings,id,"accept"),"POST",JSONObject().put("readyEstimate",estimate))
    }
    fun ready(settings: BackendSettings,id: String) {
        val result=http.json(settings,path(settings,id,"ready"),"POST",JSONObject())
        check(result.opt("ok")==true) {"Backend не подтвердил готовность"}
        result.optJSONObject("order")?.let {order->
            check(order.optString("id")==id && order.optString("status")=="ready") {"Backend вернул другой заказ или статус"}
        }
    }
    /** Reviewed backend status route only updates SQL; unlike accept/ready it sends no notification. */
    fun cancelSilently(settings: BackendSettings,id: String) {
        val result=http.json(settings,path(settings,id,"status"),"PATCH",JSONObject().put("status","cancelled"))
        val order=result.optJSONObject("order")
        check(result.opt("ok")==true && order?.optString("id")==id && order.optString("status")=="cancelled") {"Backend не подтвердил удаление заказа"}
    }
    private fun path(settings: BackendSettings,id: String,action: String): String {
        require(Regex("[A-Za-z0-9_-]{1,100}").matches(id)) {"Некорректный номер заказа"}
        return settings.ordersPath+"/$id/$action"
    }
}
