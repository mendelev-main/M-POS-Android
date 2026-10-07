package com.mendelev.mpos.settings

import android.content.Context
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Chart widths and labels are supplied by the reviewed report; no financial aggregation. */
internal class MPosSettingsBars(
    theme: MPosNativeTheme,
    private val rows: JSONArray,
    height: Int,
    position: Pair<Int, Int>,
    remember: (Int, Int) -> Unit,
) : ListView(theme.uiContext) {
    constructor(context: Context) : this(MPosNativeTheme(context, false), JSONArray(), 320, 0 to 0, { _, _ -> })
    private data class Row(val name: TextView, val value: TextView, val bar: ProgressBar)
    private var downX = 0f
    private var downY = 0f
    init {
        divider = null; cacheColorHint = android.graphics.Color.TRANSPARENT
        layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            minOf(height.toLong(), theme.dp(80).toLong() * maxOf(1, rows.length())).toInt().coerceAtLeast(theme.dp(80)))
        adapter = object : BaseAdapter() {
            override fun getCount() = rows.length()
            override fun getItem(position: Int): Any = rows.optJSONObject(position) ?: JSONObject()
            override fun getItemId(position: Int) = position.toLong()
            override fun isEnabled(position: Int) = false
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView as? LinearLayout ?: LinearLayout(theme.uiContext).apply {
                    orientation = LinearLayout.VERTICAL; minimumHeight = theme.dp(64)
                    setPadding(0, theme.dp(8), 0, theme.dp(12))
                    val name = TextView(theme.uiContext).apply { theme.text(this, 14f) }
                    val value = TextView(theme.uiContext).apply { theme.text(this, 14f, 600); gravity = Gravity.END }
                    addView(LinearLayout(theme.uiContext).apply {
                        addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = theme.dp(12) })
                    })
                    val bar = ProgressBar(theme.uiContext, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 10000
                        progressDrawable = LayerDrawable(arrayOf(theme.shape(theme.soft, 3), ClipDrawable(theme.shape(theme.accent, 3), Gravity.START, ClipDrawable.HORIZONTAL))).apply {
                            setId(0, android.R.id.background); setId(1, android.R.id.progress)
                        }
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(6)).apply { topMargin = theme.dp(8) })
                    tag = Row(name, value, bar)
                }
                val item = rows.optJSONObject(position) ?: JSONObject()
                val cells = row.tag as Row
                cells.name.text = item.optString("name"); cells.value.text = item.optString("value")
                cells.value.visibility = if (cells.value.text.isBlank()) View.GONE else View.VISIBLE
                val width = item.optDouble("width", 0.0)
                cells.bar.progress = if (width.isFinite()) (width.coerceIn(0.0, 100.0) * 100).toInt() else 0
                row.contentDescription = listOf(cells.name.text, cells.value.text).filter { it.isNotBlank() }.joinToString(" · ")
                return row
            }
        }
        setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, state: Int) = Unit
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) { if (visible > 0) remember(first, getChildAt(0)?.top ?: 0) }
        })
        post { setSelectionFromTop(position.first.coerceIn(0, (rows.length() - 1).coerceAtLeast(0)), position.second) }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; parent?.requestDisallowInterceptTouchEvent(canScrollVertically(1) || canScrollVertically(-1)) }
            MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(abs(event.y - downY) > abs(event.x - downX) && canScrollVertically(if (event.y < downY) 1 else -1))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(event)
    }
}
