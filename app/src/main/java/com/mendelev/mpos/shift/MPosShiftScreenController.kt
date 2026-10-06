package com.mendelev.mpos.shift

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Native summary surface; approved legacy forms retain auth and post-commit side effects. */
class MPosShiftScreenController(
    private val context: Context,
    private val host: FrameLayout,
    private val request: (JSONObject) -> Unit,
    private val action: (JSONObject) -> Unit,
) {
    private val scroll = ScrollView(context).apply { visibility = View.GONE; setBackgroundColor(Color.rgb(246, 247, 249)) }
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(20)) }
    private var dark = false
    private fun color(light: String, night: String) = Color.parseColor(if (dark) night else light)
    private val ink get() = color("#1B1F2A", "#F4F6FA")
    private val accent get() = color("#0E8F6F", "#31B98D")
    private fun shape(fill: Int, radius: Int = 12, border: Boolean = false) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), color("#E7E4DD", "#353B49"))
    }
    private var generation = 0L
    private var pendingId: String? = null
    private var lastPayload: JSONObject? = null
    init {
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        host.addView(scroll, FrameLayout.LayoutParams(1, 1))
        scroll.contentDescription = "Кассовая смена"
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    fun handle(payload: JSONObject) {
        if (payload.optString("action") == "hide") { hide(); return }
        if (payload.optString("action") != "show") return
        val bounds = bounds(payload, host.width, host.height) ?: run { hide(); return }
        dark = payload.optString("theme") == "dark"
        scroll.setBackgroundColor(color("#F5F4F0", "#171A21"))
        lastPayload = JSONObject(payload.toString())
        scroll.layoutParams = FrameLayout.LayoutParams(bounds.width, bounds.height).apply { leftMargin = bounds.left; topMargin = bounds.top }
        scroll.visibility = View.VISIBLE
        content.removeAllViews(); title("Кассовая смена"); text(content, "Загрузка смены…")
        refresh()
    }
    fun hide() { generation++; pendingId = null; scroll.visibility = View.GONE }
    private fun refresh() {
        val payload = lastPayload ?: return
        pendingId = "native-shift-screen-${++generation}"
        request(JSONObject().put("action", "shiftScreenRead").put("requestId", pendingId)
            .put("payload", JSONObject().put("currency", payload.optString("currency")).put("establishmentName", payload.optString("establishmentName")).toString()))
    }
    fun result(value: JSONObject) {
        if (value.optString("requestId") != pendingId || scroll.visibility != View.VISIBLE) return
        pendingId = null
        content.removeAllViews(); title("Кассовая смена")
        if (!value.optBoolean("ok")) {
            text(content, "Не удалось загрузить данные смены")
            button(content, "Повторить") { refresh() }
            button(content, "Открыть прежний экран") { emit("fallback") }
            return
        }
        val active = value.optJSONObject("active")
        if (active == null) {
            val card = card()
            heading(card, "Смена закрыта")
            text(card, "Откройте смену, чтобы начать принимать заказы.")
            button(card, "Открыть смену") { emit("open") }
        } else renderActive(active)
        heading(content, "История смен")
        val history = value.optJSONArray("history") ?: JSONArray()
        if (history.length() == 0) text(content, "Ещё нет закрытых смен")
        for (i in 0 until history.length()) {
            val report = history.getJSONObject(i).getJSONObject("report")
            button(content, "${date(report.optLong("openedAt"))} — ${date(report.optLong("closedAt"))}\n${report.optString("employeeName").ifBlank { "Сотрудник не указан" }} · Заказов: ${report.optInt("count")}\nНаличные ${money(report, "cash")} · Карта ${money(report, "card")}\nРасхожд.: ${money(report, "difference")}") { emit("report", report.getString("id")) }
        }
        button(content, "Обновить") { refresh() }
    }
    private fun renderActive(model: JSONObject) {
        val r = model.getJSONObject("report"); val s = model.getJSONObject("summary"); val id = r.getString("id")
        val card = card()
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        card.addView(actions)
        button(actions, "Внести наличные", true) { emit("deposit", id) }
        button(actions, "Изъять наличные", true) { emit("withdrawal", id) }
        heading(card, "Кассовая смена №${model.getInt("number")}")
        text(card, "Смена открыта: ${date(r.optLong("openedAt"))} · ${r.optString("employeeName").ifBlank { "Сотрудник не указан" }}")
        heading(card, "Наличные в кассе")
        pair(card, "Наличные на начало смены", money(r, "openingCash"))
        pair(card, "Оплата наличными", moneyValue(r.getDouble("cash") + s.getDouble("cashRefunds"), r))
        pair(card, "Возвраты наличными", moneyValue(s.getDouble("cashRefunds"), r))
        pair(card, "Сумма внесений", money(r, "deposits")); pair(card, "Сумма изъятий", money(r, "withdrawals"))
        pair(card, "Ожидаемая сумма наличных", money(r, "expectedCash"), true)
        heading(card, "Итоги продаж")
        pair(card, "Продажи", moneyValue(s.getDouble("grossSales"), r)); pair(card, "Возвраты", moneyValue(s.getDouble("refunds"), r)); pair(card, "Скидки", moneyValue(s.getDouble("discountsTotal"), r))
        heading(card, "Выручка")
        pair(card, "Выручка", moneyValue(s.getDouble("netRevenue"), r), true)
        pair(card, "Наличные", money(r, "cash")); pair(card, "Карта", money(r, "card"))
        val moves = r.getJSONArray("cashMovements")
        if (moves.length() > 0) heading(card, "Движение средств")
        for (i in moves.length() - 1 downTo 0) {
            val m = moves.getJSONObject(i)
            val deposit = m.optString("type") == "deposit"
            val label = when {
                deposit -> "Внесение наличных"
                m.optString("subtype") == "delivery" || m.optString("note") == "🚗 Доставка" -> "Доставка"
                m.optString("subtype") == "refund" || m.optString("note") == "↩️ Возврат чека" -> "Возврат чека"
                else -> "Изъятие наличных"
            }
            pair(card, label, (if (deposit) "+" else "−") + moneyValue(m.optDouble("amount"), r))
            text(card, date(m.optLong("timestamp")) + m.optString("note").takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty())
        }
        button(card, "Закрыть смену") { emit("close", id) }
    }
    private fun emit(name: String, id: String = "") { hide(); action(JSONObject().put("action", name).put("shiftId", id)) }
    private fun title(label: String) { heading(content, label, 24f) }
    private fun heading(parent: LinearLayout, label: String, size: Float = 19f) { text(parent, label, size).setTypeface(null, Typeface.BOLD) }
    private fun text(parent: LinearLayout, label: String, size: Float = 15f): TextView = TextView(context).apply {
        text = label; textSize = size; setTextColor(ink); setPadding(dp(4), dp(8), dp(4), dp(8))
        parent.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun pair(parent: LinearLayout, label: String, value: String, bold: Boolean = false) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        parent.addView(row)
        val left = text(row, label)
        left.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val right = text(row, value)
        right.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (bold) {
            row.background = shape(color("#E4F3EE", "#173B31"))
            left.setTextColor(accent); right.setTextColor(accent)
            left.setTypeface(null, Typeface.BOLD); right.setTypeface(null, Typeface.BOLD) }
    }
    private fun button(parent: LinearLayout, label: String, weighted: Boolean = false, onClick: () -> Unit) {
        parent.addView(Button(context).apply {
            text = label; isAllCaps = false; minHeight = dp(48)
            val primary = label == "Открыть смену"
            val danger = label == "Закрыть смену" || label == "Изъять наличные"
            val tint = if (danger) color("#E0483E", "#FF6B61") else accent
            backgroundTintList = null
            background = shape(if (primary) accent else if (danger) color("#FBE7E5", "#432522") else color("#E4F3EE", "#173B31"))
            setTextColor(if (primary) color("#FFFFFF", "#07140F") else tint)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { onClick() }
        }, if (weighted) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8); bottomMargin = dp(4) })
    }
    private fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; background = shape(color("#FFFFFF", "#222631"), 22, true); setPadding(dp(12), dp(8), dp(12), dp(12))
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }
    private fun money(report: JSONObject, key: String) = moneyValue(report.optDouble(key), report)
    private fun moneyValue(value: Double, report: JSONObject) = String.format(Locale.US, "%.2f %s", value, report.optString("currency"))
    private fun date(value: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")).format(Date(value))

    data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)
    companion object {
        fun bounds(payload: JSONObject, hostWidth: Int, hostHeight: Int): Bounds? {
            if (hostWidth <= 0 || hostHeight <= 0) return null
            val viewportWidth = payload.optDouble("viewportWidth"); val viewportHeight = payload.optDouble("viewportHeight")
            val rect = payload.optJSONObject("rect") ?: return null
            val values = listOf(viewportWidth, viewportHeight, rect.optDouble("left"), rect.optDouble("top"), rect.optDouble("width"), rect.optDouble("height"))
            if (values.any { !it.isFinite() } || viewportWidth <= 0 || viewportHeight <= 0 || values[4] <= 0 || values[5] <= 0) return null
            val left = (values[2] / viewportWidth * hostWidth).coerceIn(0.0, hostWidth.toDouble()).roundToInt()
            val top = (values[3] / viewportHeight * hostHeight).coerceIn(0.0, hostHeight.toDouble()).roundToInt()
            val right = ((values[2] + values[4]) / viewportWidth * hostWidth).coerceIn(0.0, hostWidth.toDouble()).roundToInt()
            val bottom = ((values[3] + values[5]) / viewportHeight * hostHeight).coerceIn(0.0, hostHeight.toDouble()).roundToInt()
            return if (right > left && bottom > top) Bounds(left, top, right - left, bottom - top) else null
        }
    }
}
