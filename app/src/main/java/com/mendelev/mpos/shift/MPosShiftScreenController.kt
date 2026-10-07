package com.mendelev.mpos.shift

import com.mendelev.mpos.ui.MPosNativeTheme
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
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
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(24)) }
    private var theme = MPosNativeTheme(context, false)
    private val ink get() = theme.ink
    private val accent get() = theme.accent
    private fun shape(fill: Int, radius: Int = 12, border: Boolean = false) = theme.shape(fill, radius, border)
    private var generation = 0L
    private var sessionEpoch = 0L
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
        theme = MPosNativeTheme(context, payload.optString("theme") == "dark")
        sessionEpoch++
        scroll.setBackgroundColor(theme.bg)
        lastPayload = JSONObject(payload.toString())
        scroll.layoutParams = FrameLayout.LayoutParams(bounds.width, bounds.height).apply { leftMargin = bounds.left; topMargin = bounds.top }
        scroll.visibility = View.VISIBLE
        content.removeAllViews(); title("Кассовая смена"); text(content, "Загрузка смены…")
        refresh()
    }
    fun hide() { generation++; sessionEpoch++; pendingId = null; scroll.visibility = View.GONE }
    /** Root invalidation comes directly from Room; old controls/results must not survive it. */
    fun rootSessionChanged() {
        if (scroll.visibility != View.VISIBLE || lastPayload == null) return
        sessionEpoch++
        content.removeAllViews(); title("Кассовая смена"); text(content, "Загрузка смены…")
        refresh()
    }
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
            val historyCard = card()
            heading(historyCard, report.optString("employeeName").ifBlank { "Сотрудник не указан" }, 17f)
            text(historyCard, "${date(report.optLong("openedAt"))} — ${date(report.optLong("closedAt"))}", 13f, true)
            pair(historyCard, "Заказов", report.optInt("count").toString())
            pair(historyCard, "Наличные", money(report, "cash")); pair(historyCard, "Карта", money(report, "card"))
            pair(historyCard, "Расхождение", money(report, "difference"))
            button(historyCard, "Сменный отчёт") { emit("report", report.getString("id")) }
        }
        button(content, "Обновить") { refresh() }
    }
    private fun renderActive(model: JSONObject) {
        val r = model.getJSONObject("report"); val s = model.getJSONObject("summary"); val id = r.getString("id")
        val card = card()
        heading(card, "Кассовая смена №${model.getInt("number")}")
        text(card, "Смена открыта: ${date(r.optLong("openedAt"))} · ${r.optString("employeeName").ifBlank { "Сотрудник не указан" }}", 14f, true)
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        card.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12); bottomMargin = dp(16) })
        button(actions, "Внести наличные", true) { emit("deposit", id) }
        button(actions, "Изъять наличные", true) { emit("withdrawal", id) }
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
    private fun heading(parent: LinearLayout, label: String, size: Float = 19f) { text(parent, label, size).apply { theme.text(this, size, 700) } }
    private fun text(parent: LinearLayout, label: String, size: Float = 15f, secondary: Boolean = false): TextView = TextView(context).apply {
        text = label; theme.text(this, size, secondary = secondary); setPadding(dp(4), dp(8), dp(4), dp(8))
        parent.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun pair(parent: LinearLayout, label: String, value: String, bold: Boolean = false) {
        val narrow = scroll.layoutParams.width < dp(600) || context.resources.configuration.fontScale > 1.3f
        val row = LinearLayout(context).apply { orientation = if (narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        parent.addView(row)
        val left = text(row, label)
        left.layoutParams = if (narrow) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val right = text(row, value).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_END; theme.text(this, 16f, 600) }
        right.layoutParams = LinearLayout.LayoutParams(if (narrow) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (bold) {
            row.background = shape(theme.soft)
            left.setTextColor(accent); right.setTextColor(accent)
            theme.text(left, 15f, 700); theme.text(right, 18f, 700); left.setTextColor(accent); right.setTextColor(accent) }
    }
    private fun button(parent: LinearLayout, label: String, weighted: Boolean = false, onClick: () -> Unit) {
        val epoch = sessionEpoch
        parent.addView(Button(context).apply {
            text = label
            theme.button(this, primary = label == "Открыть смену", destructive = label == "Закрыть смену" || label == "Изъять наличные")
            setOnClickListener { if (sessionEpoch == epoch && scroll.isVisible) onClick() }
        }, if (weighted) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) } else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8); bottomMargin = dp(4) })
    }
    private fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; background = shape(theme.surface, 22, true); setPadding(dp(20), dp(16), dp(20), dp(20))
        content.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }
    private fun money(report: JSONObject, key: String) = moneyValue(report.optDouble(key), report)
    private fun moneyValue(value: Double, report: JSONObject) = String.format(Locale.US, "%.2f", value).replace('.', ',') + " " + report.optString("currency")
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
