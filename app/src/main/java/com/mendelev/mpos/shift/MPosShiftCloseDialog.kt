package com.mendelev.mpos.shift

import android.app.AlertDialog
import android.content.Context
import org.json.JSONObject

/** Reads expected cash through the storage FIFO; no financial value is trusted from WebView. */
class MPosShiftCloseDialog(private val context: Context, private val request: (JSONObject) -> Unit, private val action: (JSONObject) -> Unit) {
    private var token = ""
    private var shiftId = ""
    private var currency = ""
    private var generation = 0L
    private var pending: String? = null
    private var loading: AlertDialog? = null
    private val input = MPosCashMovementDialog(context, action)
    fun handle(payload: JSONObject) {
        when (payload.optString("action")) {
            "closeFormShow" -> {
                if (payload.optString("token").isBlank() || payload.optString("shiftId").isBlank()) return
                dismiss(); token = payload.getString("token"); shiftId = payload.getString("shiftId"); currency = payload.optString("currency")
                val view = AlertDialog.Builder(context).setTitle("Закрыть смену").setMessage("Чтение сохранённых данных смены…")
                    .setNegativeButton("Отмена", null).setNeutralButton("Повторить", null).setPositiveButton("Прежняя форма", null).create()
                loading = view
                view.setOnCancelListener { cancel("cancel") }
                view.setOnShowListener {
                    view.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancel("cancel") }
                    view.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { refresh() }
                    view.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { cancel("fallback") }
                    view.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = pending == null
                    view.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = pending == null
                }
                view.show(); refresh()
            }
            "closeFormHide" -> if (payload.optString("token") == token) dismiss()
            "closeFormResult" -> if (payload.optString("token") == token) input.handle(JSONObject(payload.toString()).put("action", "cashFormResult"))
        }
    }
    private fun refresh() {
        pending = "native-shift-close-${++generation}"
        loading?.let {
            it.setMessage("Чтение сохранённых данных смены…")
            it.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
            it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
        request(JSONObject().put("action", "shiftCloseFormRead").put("requestId", pending)
            .put("payload", JSONObject().put("shiftId", shiftId).toString()))
    }
    fun result(value: JSONObject) {
        if (pending == null || value.optString("requestId") != pending || loading == null) return
        pending = null
        val expected = value.optDouble("expectedCash")
        if (!value.optBoolean("ok") || value.optString("shiftId") != shiftId || !expected.isFinite() || expected < 0) {
            loading?.let {
                it.setMessage("Не удалось получить актуальные данные открытой смены. Повторите чтение или откройте прежнюю форму.")
                it.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
            }
            return
        }
        loading?.setOnCancelListener(null); loading?.dismiss(); loading = null
        input.handle(JSONObject().put("action", "cashFormShow").put("token", token).put("type", "close").put("shiftId", shiftId).put("expectedCash", expected).put("currency", currency))
    }
    private fun cancel(name: String) { val previous = token; dismiss(); action(JSONObject().put("action", name).put("token", previous)) }
    fun dismiss() { pending = null; generation++; loading?.setOnCancelListener(null); loading?.dismiss(); loading = null; input.dismiss(); token = ""; shiftId = "" }
}
