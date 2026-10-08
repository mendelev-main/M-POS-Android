package com.mendelev.mpos.workspace

import org.json.JSONArray
import org.json.JSONObject

/** Reviewed topbar destinations. Auxiliary shift/events controls retain their domain owners. */
object MPosWorkspaceHeaderModel {
    val destinations:Map<String,String> =linkedMapOf("pos" to "M POS","purchaseOrders" to "Заказы","receiving" to "Приёмка",
        "receipts" to "Чеки","analytics" to "Аналитика","bookings" to "Бронирования","settings" to "Настройки")
    fun calculate(snapshot:JSONObject):JSONObject {
        val buttons=JSONArray()
        destinations.forEach{(tab,label)->buttons.put(JSONObject().put("tab",tab).put("label",label)
            .put("group",when(tab){"pos"->"brand";"settings"->"settings";else->"tabs"})
            .put("selected",snapshot.getString("tab")==tab))}
        return JSONObject().put("buttons",buttons).put("expected",JSONObject(snapshot.toString()))
    }
}
