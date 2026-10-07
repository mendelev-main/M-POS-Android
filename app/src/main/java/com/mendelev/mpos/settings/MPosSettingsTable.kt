package com.mendelev.mpos.settings

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.mendelev.mpos.ui.MPosNativeTheme
import org.json.JSONArray
import kotlin.math.abs

/** Native report presentation: fixed headers and recycled visible rows, no business arithmetic. */
internal class MPosSettingsTable(
    theme: MPosNativeTheme,
    headers: JSONArray,
    rows: JSONArray,
    height: Int,
    position: Pair<Int, Int>,
    remember: (Int, Int) -> Unit,
) : HorizontalScrollView(theme.uiContext) {
    constructor(context: Context) : this(MPosNativeTheme(context, false), JSONArray(), JSONArray(), 400, 0 to 0, { _, _ -> })
    init {
        val count = headers.length().coerceAtLeast(1)
        val probe = TextView(theme.uiContext).apply { theme.text(this, 14f) }
        val widths = (0 until count).map { column ->
            var width = probe.paint.measureText(headers.optString(column))
            for (index in 0 until minOf(rows.length(), 50)) width = maxOf(width, probe.paint.measureText(rows.optJSONArray(index)?.optString(column).orEmpty()))
            (width.toInt() + theme.dp(24)).coerceIn(theme.dp(132), theme.dp(260))
        }
        fun cell(label: String, heading: Boolean) = TextView(theme.uiContext).apply {
            text = label; theme.text(this, 14f, if (heading) 700 else 400)
            setPadding(theme.dp(12), theme.dp(12), theme.dp(12), theme.dp(12))
            minHeight = theme.dp(48)
        }
        val column = LinearLayout(theme.uiContext).apply { orientation = LinearLayout.VERTICAL }
        val heading = LinearLayout(theme.uiContext).apply { setBackgroundColor(theme.soft) }
        repeat(count) { index -> heading.addView(cell(headers.optString(index), true), LinearLayout.LayoutParams(widths[index], ViewGroup.LayoutParams.WRAP_CONTENT)) }
        column.addView(heading)
        val list = object : ListView(theme.uiContext) {
            private var downX = 0f
            private var downY = 0f
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; parent?.requestDisallowInterceptTouchEvent(canScrollVertically(1) || canScrollVertically(-1)) }
                    MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(abs(event.y - downY) > abs(event.x - downX) && canScrollVertically(if (event.y < downY) 1 else -1))
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
                }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            divider = theme.shape(theme.border, 0); dividerHeight = theme.dp(1)
            cacheColorHint = android.graphics.Color.TRANSPARENT
            adapter = object : BaseAdapter() {
                override fun getCount() = rows.length()
                override fun getItem(position: Int): Any = rows.optJSONArray(position) ?: JSONArray()
                override fun getItemId(position: Int) = position.toLong()
                override fun isEnabled(position: Int) = false
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = convertView as? LinearLayout ?: LinearLayout(theme.uiContext).apply {
                        repeat(count) { index -> addView(cell("", false), LinearLayout.LayoutParams(widths[index], ViewGroup.LayoutParams.WRAP_CONTENT)) }
                    }
                    val values = rows.optJSONArray(position)
                    repeat(count) { index ->
                        (row.getChildAt(index) as TextView).apply {
                            text = values?.optString(index).orEmpty()
                            contentDescription = headers.optString(index) + ": " + text
                        }
                    }
                    return row
                }
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, state: Int) = Unit
                override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) { if (visible > 0) remember(first, getChildAt(0)?.top ?: 0) }
            })
            post { setSelectionFromTop(position.first.coerceIn(0, (rows.length() - 1).coerceAtLeast(0)), position.second) }
        }
        column.addView(list, LinearLayout.LayoutParams(widths.sum(), minOf(height.toLong(), theme.dp(48).toLong() * maxOf(1, rows.length())).toInt().coerceAtLeast(theme.dp(48))))
        addView(column)
    }
}
