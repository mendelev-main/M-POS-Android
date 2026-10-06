package com.mendelev.mpos.payment

import com.mendelev.mpos.ui.MPosNativeTheme
import android.app.AlertDialog
import android.content.Context
import androidx.core.graphics.toColorInt
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** Manual confirmation of an external terminal payment; never initiates a sale itself. */
class MPosCardConfirmationDialog(private val context: Context, private val action: (JSONObject) -> Unit) {
    private var dialog: AlertDialog? = null
    private var token: String? = null
    fun handle(payload: JSONObject) {
        val next = payload.optString("token")
        if (payload.optString("action") == "hide") { if (next == token) dismiss(); return }
        if (payload.optString("action") != "show" || next.isBlank()) return
        val amount = payload.optDouble("amount", Double.NaN)
        if (!amount.isFinite() || amount < 0 || payload.optString("amountLabel").isBlank()) return
        dismiss(); token = next
        val dark = payload.optString("theme") == "dark"
        val theme = MPosNativeTheme(context, dark)
        val themed = theme.uiContext
        val fields = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun label(value: String, size: Float) {
            fields.addView(TextView(themed).apply { text = value; textSize = size; setTextColor((if (dark) "#F4F6FA" else "#1B1F2A").toColorInt()) })
        }
        label("Проведите оплату на терминале", 18f)
        label(payload.getString("amountLabel"), 28f)
        val current = AlertDialog.Builder(themed).setTitle("Оплата картой").setView(fields)
            .setNegativeButton("Отмена", null).setPositiveButton("Оплата прошла", null).create()
        dialog = current
        current.setOnCancelListener { finish(next, "cancel") }
        current.setOnShowListener {
            current.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { finish(next, "cancel") }
            current.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setOnClickListener { finish(next, "confirm") }
            }
        }
        current.show(); theme.dialog(current)
    }
    private fun finish(expected: String, kind: String) {
        if (token != expected) return
        dismiss()
        action(JSONObject().put("token", expected).put("action", kind))
    }
    fun dismiss() { token = null; dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null }
}
