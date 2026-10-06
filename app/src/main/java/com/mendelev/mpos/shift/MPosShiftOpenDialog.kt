package com.mendelev.mpos.shift

import com.mendelev.mpos.ui.MPosNativeTheme
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ScrollView
import androidx.core.widget.doAfterTextChanged
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.floor

/** Native selection/password input; no verifier or persisted authentication data is introduced. */
class MPosShiftOpenDialog(private val context: Context, private val request: (JSONObject) -> Unit, private val action: (JSONObject) -> Unit) {
    private var theme = MPosNativeTheme(context, false)
    private var token = ""
    private var currency = ""
    private var generation = 0L
    private var pending: String? = null
    private var loading: AlertDialog? = null
    private var dialog: AlertDialog? = null
    private var busy = false
    private var blocked = false
    private var password: EditText? = null
    private var picker: Spinner? = null
    private var error: TextView? = null
    private var employeeCount = 0
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun handle(payload: JSONObject) {
        when (payload.optString("action")) {
            "openFormShow" -> {
                if (busy || payload.optString("token").isBlank()) return
                dismiss(); theme = MPosNativeTheme(context, payload.optString("theme") == "dark"); token = payload.getString("token"); currency = payload.optString("currency")
                val view = AlertDialog.Builder(theme.uiContext).setTitle("Открыть смену").setMessage("Чтение сохранённых данных…")
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
                view.show(); theme.dialog(view); refresh()
            }
            "openFormHide" -> if (payload.optString("token") == token) dismiss()
            "openFormResult" -> {
                if (payload.optString("token") != token || !busy) return
                if (payload.optBoolean("ok")) { dismiss(); return }
                busy = false; blocked = payload.optBoolean("blocked"); password?.text?.clear()
                error?.text = if (blocked) "Статус сохранения не подтверждён. Перезапустите приложение." else "Смена не открыта. Проверьте сотрудника, пароль администратора и актуальность данных."
                controls()
            }
        }
    }
    private fun refresh() {
        pending = "native-shift-open-${++generation}"
        loading?.let { it.setMessage("Чтение сохранённых данных…"); it.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false; it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false }
        request(JSONObject().put("action", "shiftOpenFormRead").put("requestId", pending))
    }
    fun result(value: JSONObject) {
        if (pending == null || value.optString("requestId") != pending || loading == null) return
        pending = null
        val opening = value.optDouble("openingCash")
        val staff = value.optJSONArray("employees")
        if (!value.optBoolean("ok") || !opening.isFinite() || opening < 0 || staff == null) {
            loading?.let { it.setMessage("Не удалось получить актуальные данные. Повторите чтение или откройте прежнюю форму."); it.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true; it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true }
            return
        }
        loading?.setOnCancelListener(null); loading?.dismiss(); loading = null
        showInput(staff, opening)
    }
    private fun showInput(staff: JSONArray, opening: Double) {
        employeeCount = staff.length()
        val rows = (0 until staff.length()).map { staff.getJSONObject(it) }
        val fields = LinearLayout(theme.uiContext).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(24)) }
        fun label(value: String): TextView = TextView(theme.uiContext).apply {
            text = value; theme.text(this); setPadding(0, dp(8), 0, dp(8)); fields.addView(this)
        }
        label("Сотрудник")
        val select = Spinner(theme.uiContext, Spinner.MODE_DROPDOWN).apply {
            contentDescription = "Выбор сотрудника"; minimumHeight = dp(52)
            background = theme.shape(theme.bg, 12, true); setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        picker = select
        val names = listOf("Выберите сотрудника") + rows.map { it.getString("name") + if (it.optString("role") == "admin") " · Администратор" else "" }
        select.adapter = object : ArrayAdapter<String>(theme.uiContext, android.R.layout.simple_spinner_item, names) {
            private fun row(position: Int, selected: Boolean): View = LinearLayout(theme.uiContext).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = dp(48); setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(TextView(theme.uiContext).apply {
                    text = names[position]; theme.text(this, 16f, if (position == 0) 400 else 600, position == 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (selected) addView(TextView(theme.uiContext).apply { text = "⌄"; theme.text(this, 22f, secondary = true) })
                else background = theme.shape(if (position == select.selectedItemPosition) theme.soft else theme.surface, 0)
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, true)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = row(position, false)
        }
        fields.addView(select, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val caption = label("Пароль администратора")
        val secret = EditText(theme.uiContext).apply { theme.text(this); background = theme.shape(theme.bg, 12, true); minHeight = dp(52); setPadding(dp(16), dp(12), dp(16), dp(12)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; isSaveEnabled = false; contentDescription = "Пароль администратора" }
        theme.text(secret); password = secret; fields.addView(secret)
        caption.visibility = View.GONE; secret.visibility = View.GONE
        select.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val admin = position > 0 && rows[position - 1].optString("role") == "admin"
                caption.visibility = if (admin) View.VISIBLE else View.GONE; secret.visibility = caption.visibility; secret.text.clear()
            }
        }
        if (rows.isEmpty()) label("Сотрудников нет. Добавьте сотрудника в разделе сотрудников.")
        label("Наличные при открытии смены").apply { theme.text(this, 14f, secondary = true) }
        label(String.format(Locale.US, "%.2f", floor(opening * 100 + 0.5) / 100).replace('.', ',') + " " + currency).apply { theme.text(this, 28f, 700); setTextColor(theme.accent) }
        label("Проверьте наличные в кассе. Сумма перенесена с прошлой смены.").apply { theme.text(this, 14f, secondary = true) }
        error = label("").apply {
            setTextColor(theme.danger); visibility = View.GONE
            doAfterTextChanged { visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE }
        }
        val view = AlertDialog.Builder(theme.uiContext).setTitle("Открыть смену").setView(ScrollView(theme.uiContext).apply { addView(fields) }).setNegativeButton("Отмена", null).setPositiveButton("Открыть", null).create()
        dialog = view; view.setOnCancelListener { cancel("cancel") }
        view.setOnShowListener {
            view.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { cancel("cancel") }
            view.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (busy || blocked) return@setOnClickListener
                val position = select.selectedItemPosition
                if (position <= 0) { error?.text = "Выберите сотрудника"; return@setOnClickListener }
                val selected = rows[position - 1]; val admin = selected.optString("role") == "admin"
                if (admin && secret.text.isBlank()) { secret.error = "Введите пароль администратора"; return@setOnClickListener }
                val entered = if (admin) secret.text.toString() else ""
                secret.text.clear(); busy = true; controls(); error?.text = "Сохранение…"
                action(JSONObject().put("action", "submit").put("token", token).put("employeeId", selected.getString("id")).put("password", entered))
            }
            controls()
        }
        view.show(); theme.dialog(view); error?.setTextColor(theme.danger)
    }
    private fun controls() {
        error?.setTextColor(if (busy) theme.muted else theme.danger)
        picker?.isEnabled = !busy && !blocked; password?.isEnabled = !busy && !blocked
        dialog?.let { it.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !busy && !blocked && employeeCount > 0; it.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !busy; it.setCancelable(!busy); it.setCanceledOnTouchOutside(!busy) }
    }
    private fun cancel(name: String) { if (busy) return; val old = token; dismiss(); action(JSONObject().put("action", name).put("token", old)) }
    fun dismiss() { pending = null; generation++; password?.text?.clear(); loading?.setOnCancelListener(null); loading?.dismiss(); loading = null; dialog?.setOnCancelListener(null); dialog?.dismiss(); dialog = null; token = ""; busy = false; blocked = false; password = null; picker = null; error = null }
}
