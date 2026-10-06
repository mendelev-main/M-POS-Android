package com.mendelev.mpos.shift

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.mendelev.mpos.data.MPosJsonNumbers
import org.json.JSONObject

/** Native input surface; the existing acknowledged command path remains the commit owner. */
class MPosCashMovementDialog(private val context: Context, private val action: (JSONObject) -> Unit) {
    private var dialog: AlertDialog? = null
    private var token = ""
    private var busy = false
    private var blocked = false
    private var error: TextView? = null
    private var inputs: List<EditText> = emptyList()
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun handle(payload: JSONObject) {
        when (payload.optString("action")) {
            "cashFormShow" -> show(payload)
            "cashFormHide" -> if (payload.optString("token") == token) dismiss()
            "cashFormResult" -> {
                if (payload.optString("token") != token || dialog == null || !busy) return
                if (payload.optBoolean("ok")) { dismiss(); return }
                busy = false; blocked = payload.optBoolean("blocked")
                error?.text = if (blocked) "Статус сохранения не подтверждён. Перезапустите приложение перед новой операцией." else "Операция не сохранена. Проверьте сумму и текущую смену."
                updateControls()
            }
        }
    }
    private fun show(payload: JSONObject) {
        val type = payload.optString("type")
        val nextToken = payload.optString("token")
        if (type !in setOf("deposit", "withdrawal") || nextToken.isBlank() || payload.optString("shiftId").isBlank()) return
        if (busy) return
        dismiss(); token = nextToken
        val fields = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8)) }
        fun label(text: String) { fields.addView(TextView(context).apply { this.text = text; textSize = 16f }) }
        label("Сумма")
        val amount = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "0,00"; contentDescription = "Сумма наличных"
        }
        fields.addView(amount, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        label("Комментарий")
        val note = EditText(context).apply { hint = "Необязательно"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; contentDescription = "Комментарий движения наличных" }
        fields.addView(note)
        inputs = listOf(amount, note)
        error = TextView(context).apply { setTextColor(android.graphics.Color.rgb(176, 32, 32)); textSize = 15f }
        fields.addView(error)
        val current = AlertDialog.Builder(context).setTitle(if (type == "deposit") "Внести наличные" else "Изъять наличные")
            .setView(fields).setNegativeButton("Отмена", null)
            .setPositiveButton(if (type == "deposit") "Внести" else "Изъять", null).create()
        dialog = current
        current.setOnCancelListener { cancel() }
        current.setOnShowListener {
            current.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancel() }
            current.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (busy || blocked) return@setOnClickListener
                val value = parseAmount(amount.text.toString())
                if (value == null) { amount.error = "Введите корректную сумму"; return@setOnClickListener }
                busy = true; error?.text = "Сохранение…"; updateControls()
                action(JSONObject().put("action", "submit").put("token", token).put("shiftId", payload.getString("shiftId"))
                    .put("type", type).put("amount", value).put("note", note.text.toString().trim { it.isWhitespace() || it == '\uFEFF' }))
            }
        }
        current.show()
    }
    private fun updateControls() {
        inputs.forEach { it.isEnabled = !busy && !blocked }
        dialog?.let {
            it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !busy && !blocked
            it.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !busy
            it.setCancelable(!busy); it.setCanceledOnTouchOutside(!busy)
        }
    }
    private fun cancel() {
        if (busy) return
        val old = token
        dismiss()
        action(JSONObject().put("action", "cancel").put("token", old))
    }
    fun dismiss() { dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null; token = ""; busy = false; blocked = false; error = null; inputs = emptyList() }
    companion object {
        fun parseAmount(raw: String): Double? = MPosJsonNumbers.number(raw.replaceFirst(',', '.')).takeIf { it.isFinite() && it > 0 }
    }
}
