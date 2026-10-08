package com.mendelev.mpos.employee

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONObject

/** Password never leaves this native input/command boundary. */
class MPosEmployeeAuthorizationDialog(
    private val context: Context,
    private val commit: (JSONObject, String) -> Unit,
    private val reply: (JSONObject) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var deadline: Runnable? = null
    private var blocked = false
    private var dialog: AlertDialog? = null
    private var requestId = ""
    private var pending: String? = null
    private var generation = 0L
    private var password: EditText? = null
    private var error: TextView? = null
    private var command: JSONObject? = null

    fun handle(payload: JSONObject) {
        if (payload.optString("action") != "employeeAuthorize") return
        val id = payload.optString("requestId")
        if (id.isBlank()) return
        if (dialog != null) {
            reply(JSONObject().put("requestId", id).put("ok", false).put("message", "Дождитесь завершения текущего подтверждения"))
            return
        }
        val input = payload.optJSONObject("command")
        if (input == null || input.optString("operation") !in setOf("save", "delete")) {
            reply(JSONObject().put("requestId", id).put("ok", false).put("message", "Некорректная команда сотрудника"))
            return
        }
        requestId = id
        command = JSONObject(input.toString())
        val theme = MPosNativeTheme(context, payload.optString("theme") == "dark")
        val content = LinearLayout(theme.uiContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(theme.dp(24), theme.dp(16), theme.dp(24), theme.dp(16))
        }
        content.addView(TextView(theme.uiContext).apply {
            text = if (input.getString("operation") == "delete") "Для удаления сотрудника требуется пароль администратора."
                else "Для изменения прав сотрудника требуется пароль администратора."
            theme.text(this, 16f)
        })
        val field = EditText(theme.uiContext).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Пароль администратора"
            contentDescription = hint
            isSaveEnabled = false
            minimumHeight = theme.dp(52)
            theme.text(this)
            background = theme.shape(theme.bg, 12, true)
            setPadding(theme.dp(12), theme.dp(8), theme.dp(12), theme.dp(8))
        }
        password = field
        content.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = theme.dp(16) })
        error = TextView(theme.uiContext).apply { theme.text(this, 14f); setTextColor(theme.danger) }.also { content.addView(it) }
        val view = AlertDialog.Builder(theme.uiContext).setTitle("Подтверждение действия")
            .setView(ScrollView(theme.uiContext).apply { addView(content) })
            .setNegativeButton("Отмена", null).setPositiveButton("Подтвердить", null).create()
        dialog = view
        view.setOnCancelListener { cancel() }
        view.setOnShowListener {
            view.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancel() }
            view.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        }
        view.show()
        theme.dialog(view)
        field.requestFocus()
    }

    private fun submit() {
        if (pending != null || blocked) return
        val input = command ?: return
        val credential = password?.text?.toString().orEmpty()
        password?.text?.clear()
        val id = "native-employee-commit-${++generation}"
        pending = id
        dialog?.setCancelable(false)
        password?.isEnabled = false
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        error?.text = "Сохранение…"
        reply(JSONObject().put("requestId", requestId).put("action", "committing"))
        deadline = Runnable {
            if (pending != id) return@Runnable
            pending = null
            blocked = true
            error?.text = "Статус сохранения не подтверждён. Перезапустите приложение."
            dialog?.setCancelable(true)
            dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply { isEnabled = true; text = "Закрыть" }
            reply(JSONObject().put("requestId", requestId).put("ok", false).put("uncertain", true)
                .put("message", "Статус сохранения не подтверждён. Перезапустите приложение."))
        }.also { handler.postDelayed(it, 30_000) }
        commit(JSONObject(input.toString()).put("requestId", id), credential)
    }

    fun result(value: JSONObject) {
        if (pending == null || pending != value.optString("requestId")) return
        pending = null
        deadline?.let(handler::removeCallbacks); deadline = null
        if (value.optBoolean("credentialRejected")) {
            password?.isEnabled = true
            dialog?.setCancelable(true)
            dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = true
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
            error?.text = "Неверный пароль администратора"
            reply(JSONObject().put("requestId", requestId).put("action", "retry"))
            return
        }
        // Forward fixed results only, never submitted credential or command documents.
        val result = JSONObject().put("requestId", requestId).put("ok", value.optBoolean("ok"))
            .put("message", value.optString("message"))
        close()
        reply(result)
    }

    private fun cancel() {
        if (pending != null) return
        val id = requestId
        close()
        reply(JSONObject().put("requestId", id).put("cancelled", true))
    }

    fun close() {
        generation++
        deadline?.let(handler::removeCallbacks); deadline = null
        blocked = false
        password?.text?.clear()
        password = null
        error = null
        command = null
        pending = null
        requestId = ""
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        dialog = null
    }
}
