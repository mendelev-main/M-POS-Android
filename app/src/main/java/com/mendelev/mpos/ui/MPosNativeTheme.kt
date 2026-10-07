package com.mendelev.mpos.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.toColorInt
import kotlin.math.roundToInt

/** Native counterpart of the reviewed POS tokens; no remote font or runtime dependency. */
class MPosNativeTheme(private val context: Context, val dark: Boolean) {
    val uiContext = ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
    private fun color(light: String, night: String) = (if (dark) night else light).toColorInt()
    val bg = color("#F5F4F0", "#171A21")
    val surface = color("#FFFFFF", "#222631")
    val ink = color("#1B1F2A", "#F4F6FA")
    val muted = color("#767C8C", "#A9B0BF")
    val accent = color("#0E8F6F", "#31B98D")
    val accentInk = color("#FFFFFF", "#07140F")
    val soft = color("#E4F3EE", "#173B31")
    val border = color("#E7E4DD", "#353B49")
    val danger = color("#E0483E", "#FF6B61")
    val dangerSoft = color("#FBE7E5", "#432522")
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    fun shape(fill: Int, radius: Int = 12, outlined: Boolean = false, stroke: Int = border) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (outlined) setStroke(dp(1), stroke)
    }
    fun text(view: TextView, size: Float = 16f, weight: Int = 400, secondary: Boolean = false) {
        view.tag = "mpos-styled-text"; view.textSize = size; view.typeface = font(context, weight)
        view.setTextColor(if (secondary) muted else ink)
        view.setLineSpacing(dp(3).toFloat(), 1f)
    }
    fun button(view: Button, primary: Boolean = false, destructive: Boolean = false, selected: Boolean = false) {
        text(view, 15f, 600); view.isSelected = selected; view.isAllCaps = false; view.minHeight = dp(48); view.minimumHeight = dp(48)
        view.minWidth = 0; view.minimumWidth = 0; view.backgroundTintList = null
        val fill = if (primary) accent else if (destructive) dangerSoft else if (selected) soft else surface
        val normal = shape(fill, 12, !primary)
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(bg, 12, true))
            addState(intArrayOf(android.R.attr.state_focused), shape(fill, 12, true, accent))
            addState(intArrayOf(), RippleDrawable(ColorStateList.valueOf(0x201B1F2A), normal, shape(0xFFFFFFFF.toInt())))
        }
        view.background = states
        view.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(muted, if (primary) accentInk else if (destructive) danger else if (selected) accent else ink)))
        view.setPadding(dp(16), dp(12), dp(16), dp(12))
    }
    fun dialog(view: AlertDialog) {
        view.window?.setBackgroundDrawable(shape(surface, 22, true))
        val available = context.resources.displayMetrics.widthPixels - dp(32)
        if (available > 0) view.window?.setLayout(minOf(available, dp(640)), ViewGroup.LayoutParams.WRAP_CONTENT)
        fun visit(node: View) {
            if (node is TextView && node !is Button && node.tag != "mpos-styled-text") {
                node.typeface = font(context, if (node.textSize >= android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 20f, context.resources.displayMetrics)) 600 else if (node.typeface?.isBold == true) 700 else 400)
                node.setTextColor(ink)
            }
            if (node is ViewGroup) for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }
        view.window?.decorView?.let(::visit)
        button(view.getButton(AlertDialog.BUTTON_POSITIVE), primary = true)
        button(view.getButton(AlertDialog.BUTTON_NEGATIVE))
        button(view.getButton(AlertDialog.BUTTON_NEUTRAL))
        for (id in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
            val b = view.getButton(id)
            (b.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.marginStart = dp(8); b.layoutParams = it }
        }
    }
    companion object {
        private val fonts = mutableMapOf<Int, Typeface>()
        private fun font(context: Context, weight: Int): Typeface = fonts.getOrPut(weight) {
            runCatching {
                Typeface.Builder(context.assets, "pos/Web/fonts/Manrope-Variable.ttf")
                    .setFontVariationSettings("'wght' $weight").setWeight(weight).build()
            }.getOrNull() ?: Typeface.create(Typeface.DEFAULT, weight, false)
        }
    }
}
