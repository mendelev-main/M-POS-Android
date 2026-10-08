package com.mendelev.mpos.workspace

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.graphics.toColorInt
import com.mendelev.mpos.settings.MPosSettingsActionRow
import com.mendelev.mpos.shift.MPosShiftScreenController
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Native normal-operation workspace. Business values/actions come from the approved owners. */
class MPosWorkspaceController(private val context: Context, private val host: FrameLayout, private val action: (JSONObject) -> Unit) {
    private val overlay = LinearLayout(context).apply { visibility = View.GONE }
    private var theme = MPosNativeTheme(context, false)
    private var token = ""
    private var contextKey = ""
    private var busy = false
    private var blocked = false
    private var navigation: JSONObject? = null
    private var catalogScroll: ScrollView? = null
    private var cartScroll: ScrollView? = null
    private var status: TextView? = null
    private val controls = mutableListOf<View>()
    private val disabled = mutableSetOf<View>()
    init { host.addView(overlay, FrameLayout.LayoutParams(1, 1)) }
    fun handle(payload: JSONObject) {
        when (payload.optString("action")) {
            "hide" -> if (payload.optString("token") == token) hide()
            "result" -> if (payload.optString("token") == token) { busy = false; blocked = payload.optBoolean("blocked"); status?.text = payload.optString("message"); status?.setTextColor(if (blocked) theme.danger else theme.muted); enable() }
            "show" -> show(payload)
        }
    }
    private fun column() = LinearLayout(theme.uiContext).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 16f, bold: Boolean = false, secondary: Boolean = false) = TextView(theme.uiContext).apply {
        text = value; theme.text(this, size, if (bold) 700 else 400, secondary)
    }
    private fun margin() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = theme.dp(10) }
    private fun button(value: JSONObject) = Button(theme.uiContext).apply {
        text = value.optString("label"); theme.button(this, value.optBoolean("primary")); controls += this
        if (value.optBoolean("disabled")) disabled += this
        setOnClickListener { emit(value.optString("key")) }
    }
    private fun show(payload: JSONObject) {
        val next = payload.optString("token"); val model = payload.optJSONObject("model") ?: return
        val bounds = MPosShiftScreenController.bounds(payload, host.width, host.height)
        if (next.isBlank()) return
        if (bounds == null) { hide(); action(JSONObject().put("action", "fallback").put("token", next)); return }
        val sameContext = contextKey == model.optString("context")
        val catalogY = if (sameContext) catalogScroll?.scrollY ?: 0 else 0
        val cartY = cartScroll?.scrollY ?: 0
        token = next; blocked = model.optBoolean("blocked"); contextKey = model.optString("context"); controls.clear(); disabled.clear(); overlay.removeAllViews()
        navigation=model.optJSONObject("navigation")?.let{JSONObject(it.toString())}
        theme = MPosNativeTheme(context, payload.optString("theme") == "dark")
        overlay.setBackgroundColor(theme.bg); overlay.setPadding(theme.dp(12), theme.dp(12), theme.dp(12), theme.dp(12))
        overlay.orientation = if (bounds.width >= theme.dp(720)) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        overlay.layoutParams = FrameLayout.LayoutParams(bounds.width, bounds.height).apply { leftMargin = bounds.left; topMargin = bounds.top }
        val catalog = column(); val cart = column().apply { background = theme.shape(theme.surface, 22, true); setPadding(theme.dp(16), theme.dp(16), theme.dp(16), theme.dp(16)) }
        catalog.addView(label(navigation?.optString("title")?:model.optString("title"), 24f, true), margin())
        if (model.optString("notice").isNotBlank()) catalog.addView(label(model.optString("notice"), 14f, secondary = true), margin())
        if (model.optBoolean("folder")&&navigation==null) catalog.addView(button(JSONObject().put("key", model.optString("closeKey")).put("label", "Закрыть папку")), margin())
        val toolbar = model.optJSONArray("toolbar") ?: JSONArray()
        val bar = HorizontalScrollView(theme.uiContext)
        val barItems = LinearLayout(theme.uiContext)
        val nativeButtons=navigation?.optJSONArray("buttons")?:JSONArray()
        for(i in 0 until nativeButtons.length()) {
            val value=nativeButtons.getJSONObject(i)
            val viewToken=token
            barItems.addView(Button(theme.uiContext).apply {
                text=value.getString("label");theme.button(this);controls+=this
                setOnClickListener{if(viewToken==token)emitNavigation(value.getString("operation"))}
            },LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{marginEnd=theme.dp(8)})
        }
        for (i in 0 until toolbar.length()) barItems.addView(button(toolbar.getJSONObject(i)), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = theme.dp(8) })
        bar.addView(barItems); if (toolbar.length() > 0||nativeButtons.length()>0) catalog.addView(bar, margin())
        status = label(if (blocked) "Перезапустите M POS для восстановления заказа" else "", 14f, secondary = true); catalog.addView(status, margin())
        val grid = MPosWorkspaceGrid(theme.uiContext, model.optInt("columns", 4).coerceIn(1, 12), theme.dp(10), theme.dp((170 * context.resources.configuration.fontScale.coerceAtLeast(1f)).toInt()))
        val tiles = model.optJSONArray("tiles") ?: JSONArray()
        for (i in 0 until tiles.length()) {
            val tile = tiles.getJSONObject(i)
            val viewToken=token
            val cell = column().apply {
                val fill = if (tile.optString("type") == "category") runCatching { tile.optString("color").toColorInt() }.getOrDefault(theme.soft) else theme.surface
                background = theme.shape(if (theme.dark) theme.surface else fill, 16, true)
                setPadding(theme.dp(12), theme.dp(12), theme.dp(12), theme.dp(12)); contentDescription = tile.optString("name")
                isClickable = true; isFocusable = true; controls += this
                if (tile.optBoolean("disabled")) { disabled += this; alpha = .42f }
                setOnClickListener {
                    if(viewToken==token) {
                        val route=tile.optJSONObject("route")
                        if(navigation!=null&&route!=null)emitNavigation(route.getString("operation"),route.getString("value"))
                        else emit(tile.optString("key"))
                    }
                }
            }
            if (tile.optString("symbol").isNotBlank()) cell.addView(label(tile.optString("symbol"), 22f))
            cell.addView(label(tile.optString("name"), 16f, true).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            if (tile.optString("price").isNotBlank()) cell.addView(label(tile.optString("price"), 17f, true))
            if (tile.optString("stock").isNotBlank()) cell.addView(label(tile.optString("stock"), 12f, secondary = true))
            grid.addTile(cell, tile)
        }
        val catalogContent: View = if (tiles.length() > 0) grid else label(model.optString("empty"), secondary = true)
        catalogScroll = ScrollView(theme.uiContext).apply { addView(catalogContent) }
        catalog.addView(catalogScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        cart.addView(label(model.optString("cartTitle"), 19f, true), margin())
        if (model.optString("metadata").isNotBlank()) cart.addView(label(model.optString("metadata"), 14f, secondary = true), margin())
        val headerActions = MPosSettingsActionRow(theme.uiContext, theme.dp(8))
        val headerButtons = model.optJSONArray("cartHeaderButtons") ?: JSONArray()
        for (i in 0 until headerButtons.length()) headerActions.addView(button(headerButtons.getJSONObject(i)))
        if (headerButtons.length() > 0) cart.addView(headerActions, margin())
        val lineList = column(); val lines = model.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until lines.length()) {
            val line = lines.getJSONObject(i)
            val row = column().apply { background = theme.shape(theme.bg, 12, true); setPadding(theme.dp(12), theme.dp(12), theme.dp(12), theme.dp(12)) }
            val titleRow = LinearLayout(theme.uiContext)
            val edit = Button(theme.uiContext).apply {
                text = line.optString("name"); theme.button(this); controls += this
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setOnClickListener { emit(line.optString("key")) }
            }
            titleRow.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val remove = button(JSONObject().put("label", "×").put("key", line.optString("removeKey")))
            remove.contentDescription = "Удалить " + line.optString("name")
            theme.button(remove, destructive = true)
            titleRow.addView(remove, LinearLayout.LayoutParams(theme.dp(48), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = theme.dp(8) })
            row.addView(titleRow, margin()); row.addView(label(line.optString("amount"), 17f, true), margin())
            row.addView(label(line.optString("details"), 13f, secondary = true), margin())
            installSwipe(edit, line.optString("removeKey")); lineList.addView(row, margin())
        }
        if (lines.length() == 0) lineList.addView(label(model.optString("cartEmpty"), secondary = true))
        cartScroll = ScrollView(theme.uiContext).apply { addView(lineList) }
        cart.addView(cartScroll, if (overlay.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, theme.dp((lines.length() * 170).coerceIn(80, 240))))
        val totals = model.optJSONArray("totals") ?: JSONArray()
        for (i in 0 until totals.length()) {
            val total = totals.getJSONObject(i); val last = i == totals.length() - 1
            cart.addView(label(total.optString("label") + "  " + total.optString("value"), if (last) 22f else 14f, last, !last), margin())
        }
        val buttons = model.optJSONArray("cartButtons") ?: JSONArray()
        val footerActions = MPosSettingsActionRow(theme.uiContext, theme.dp(8))
        for (i in 0 until buttons.length()) footerActions.addView(button(buttons.getJSONObject(i)))
        cart.addView(footerActions, margin())
        if (overlay.orientation == LinearLayout.HORIZONTAL) {
            overlay.addView(catalog, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.8f).apply { marginEnd = theme.dp(12) })
            overlay.addView(cart, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else {
            overlay.addView(catalog, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.3f).apply { bottomMargin = theme.dp(12) })
            val portraitCart = ScrollView(theme.uiContext).apply { addView(cart) }
            overlay.addView(portraitCart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); cartScroll = portraitCart
        }
        overlay.visibility = View.VISIBLE; enable()
        catalogScroll?.post { catalogScroll?.scrollTo(0, catalogY) }; cartScroll?.post { cartScroll?.scrollTo(0, cartY) }
    }
    @SuppressLint("ClickableViewAccessibility") // Ordinary taps use Button.performClick; swipe removal is also an accessible button.
    private fun installSwipe(view: View, removeKey: String) {
        var startX = 0f; var startY = 0f; var horizontal = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; horizontal = false; false }
                MotionEvent.ACTION_MOVE -> { val dx = event.x - startX; val dy = event.y - startY; horizontal = dx < -theme.dp(8) && abs(dx) > abs(dy); if (horizontal) view.parent.requestDisallowInterceptTouchEvent(true); horizontal }
                MotionEvent.ACTION_UP -> { val remove = horizontal && event.x - startX < -theme.dp(82); if (remove) emit(removeKey); view.parent.requestDisallowInterceptTouchEvent(false); val consumed = horizontal; if (consumed) view.isPressed = false; horizontal = false; consumed }
                MotionEvent.ACTION_CANCEL -> { horizontal = false; view.parent.requestDisallowInterceptTouchEvent(false); false }
                else -> false
            }
        }
    }
    private fun emit(key: String) { if (busy || blocked || token.isBlank()) return; busy = true; enable(); action(JSONObject().put("action", "click").put("token", token).put("key", key)) }
    private fun emitNavigation(operation:String,value:String?=null) {
        val model=navigation?:return
        if(busy||blocked||token.isBlank())return
        busy=true;enable()
        action(JSONObject().put("action","navigate").put("token",token).put("command",JSONObject().put("version",1)
            .put("route",operation).put("value",value?:JSONObject.NULL).put("expected",JSONObject(model.getJSONObject("expected").toString()))
            .put("folderModal",model.opt("folderModal")?:JSONObject.NULL)))
    }
    private fun enable() { controls.forEach { it.isEnabled = !busy && !blocked && it !in disabled } }
    fun hide() { overlay.visibility = View.GONE; token = ""; contextKey = ""; busy = false; blocked = false; navigation=null; controls.clear(); disabled.clear(); overlay.removeAllViews(); catalogScroll = null; cartScroll = null; status = null }
}

/** Source coordinates/spans are presentation metadata; no catalogue layout is written here. */
internal class MPosWorkspaceGrid @JvmOverloads constructor(context: Context, private val columns: Int = 4, private val gap: Int = 10, private val rowHeight: Int = 170) : ViewGroup(context) {
    private data class Cell(val column: Int, val row: Int, val width: Int, val height: Int)
    private val cells = mutableListOf<Cell>()
    fun addTile(view: View, tile: JSONObject) {
        val column = tile.optInt("column").coerceIn(0, columns - 1); val row = tile.optInt("row").coerceIn(0, 10000)
        cells += Cell(column, row, tile.optInt("columnSpan", 1).coerceIn(1, columns - column), tile.optInt("rowSpan", 1).coerceIn(1, 100))
        addView(view)
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec); val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(1)
        for (i in cells.indices) { val c = cells[i]; getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellWidth * c.width + gap * (c.width - 1), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowHeight * c.height + gap * (c.height - 1), MeasureSpec.EXACTLY)) }
        val rows = cells.maxOfOrNull { it.row + it.height } ?: 0
        setMeasuredDimension(width, rows * rowHeight + (rows - 1).coerceAtLeast(0) * gap)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(1)
        for (i in cells.indices) { val c = cells[i]; val x = c.column * (cellWidth + gap); val y = c.row * (rowHeight + gap); getChildAt(i).layout(x, y, x + getChildAt(i).measuredWidth, y + getChildAt(i).measuredHeight) }
    }
}
