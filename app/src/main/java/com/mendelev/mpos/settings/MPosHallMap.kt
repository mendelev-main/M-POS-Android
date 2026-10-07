package com.mendelev.mpos.settings

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatButton
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.mendelev.mpos.ui.MPosNativeTheme
import com.mendelev.mpos.R
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.max

/** Relative table presentation and drag preview. Durable commands remain behind mounted actions. */
internal class MPosHallMap(
    private val theme: MPosNativeTheme,
    private val tables: JSONArray,
    private val editing: Boolean,
    empty: String,
    height: Int,
    private val click: (String) -> Unit,
    private val move: (String, Double, Double) -> Unit,
) : FrameLayout(theme.uiContext) {
    constructor(context: Context) : this(MPosNativeTheme(context, false), JSONArray(), false, "", 360, {}, { _, _, _ -> })
    init {
        background = theme.shape(theme.bg, 18, true)
        layoutParams = android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height)
        clipChildren = true
        if (tables.length() == 0) addView(TextView(theme.uiContext).apply { text = empty; theme.text(this); setPadding(theme.dp(20), theme.dp(20), theme.dp(20), theme.dp(20)) })
        for (index in 0 until tables.length()) {
            val item = tables.getJSONObject(index); val key = item.optString("key")
            val button = object : AppCompatButton(ContextThemeWrapper(theme.uiContext, if (theme.dark) androidx.appcompat.R.style.Theme_AppCompat else androidx.appcompat.R.style.Theme_AppCompat_Light)) {
                private var downX = 0f
                private var downY = 0f
                private var dragging = false
                private var px = item.optDouble("x", 0.0)
                private var py = item.optDouble("y", 0.0)
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    if (!this@MPosHallMap.isEnabled) return false
                    if (!editing || !isEnabled) return super.onTouchEvent(event)
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; dragging = false; parent?.requestDisallowInterceptTouchEvent(true) }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downX; val dy = event.rawY - downY
                            if (abs(dx) + abs(dy) > theme.dp(5)) dragging = true
                            if (dragging && this@MPosHallMap.width > 0 && this@MPosHallMap.height > 0) {
                                px = (item.optDouble("x", 0.0) + dx / this@MPosHallMap.width * 100).coerceIn(0.0, 94.0)
                                py = (item.optDouble("y", 0.0) + dy / this@MPosHallMap.height * 100).coerceIn(0.0, 88.0)
                                translationX = ((px - item.optDouble("x", 0.0)) / 100 * this@MPosHallMap.width).toFloat()
                                translationY = ((py - item.optDouble("y", 0.0)) / 100 * this@MPosHallMap.height).toFloat()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            translationX = 0f; translationY = 0f
                            if (dragging) move(key, px, py) else if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                        }
                    }
                    return true
                }
                override fun performClick(): Boolean = super.performClick()
            }.apply {
                theme.button(this, selected = item.optBoolean("selected"))
                val booked = item.optBoolean("booked")
                background = theme.shape(if (booked) theme.warningSoft else theme.infoSoft, 14, true, if (booked) theme.warningBorder else theme.infoBorder)
                setTextColor(if (booked) theme.warning else theme.info)
                if (item.optBoolean("selected")) { elevation = theme.dp(5).toFloat(); scaleX = 1.015f; scaleY = 1.015f }
                text = context.getString(R.string.mpos_hall_table_caption, item.optString("name"), item.optString("status"))
                theme.text(this, 12f, 600); setPadding(theme.dp(4), theme.dp(4), theme.dp(4), theme.dp(4)); setLineSpacing(0f, 1f); isSingleLine = false; maxLines = 4; setTextColor(if (booked) theme.warning else theme.info); isAllCaps = false
                rotation = item.optDouble("rotation", 0.0).toFloat()
                contentDescription = text
                setOnClickListener { if (this@MPosHallMap.isEnabled) click(key) }
            }
            addView(button, LayoutParams(1, 1))
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        for (index in 0 until tables.length()) {
            val item = tables.getJSONObject(index); val rectangle = item.optString("shape") == "rectangle"
            val w = max((width * if (rectangle) 0.25 else 0.15).toInt(), theme.dp(if (rectangle) 120 else 76))
            val h = max((height * if (rectangle) 0.12 else 0.15).toInt(), theme.dp(if (rectangle) 56 else 58))
            (getChildAt(index).layoutParams as LayoutParams).apply {
                this.width = w; this.height = h
                leftMargin = (width * item.optDouble("x", 0.0) / 100).toInt()
                topMargin = (height * item.optDouble("y", 0.0) / 100).toInt()
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
