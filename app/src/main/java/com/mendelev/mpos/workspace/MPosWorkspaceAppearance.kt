package com.mendelev.mpos.workspace

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.mendelev.mpos.ui.MPosNativeTheme

/** Reviewed iPad workspace CSS only. No changes to other native screens. */
internal object MPosWorkspaceAppearance {
    private fun color(theme: MPosNativeTheme, light: String, dark: String) = (if (theme.dark) dark else light).toColorInt()
    fun title(view: TextView, theme: MPosNativeTheme) {
        theme.text(view,14.5f,800); view.setTextColor(color(theme,"#5E4691","#DCCBFF"))
        view.background=theme.shape(color(theme,"#F0E9FF","#332B45"),16,true,color(theme,"#D7C8F5","#51436C"))
        view.setPadding(theme.dp(16),theme.dp(11),theme.dp(16),theme.dp(11));view.gravity=Gravity.CENTER_VERTICAL
    }
    fun button(view: Button, theme: MPosNativeTheme, role: String) {
        view.stateListAnimator=null;view.includeFontPadding=false
        view.minWidth=0;view.minimumWidth=0;view.minHeight=theme.dp(44);view.minimumHeight=theme.dp(44)
        when(role) {
            "toolbar","demand","demandOverload" -> {
                theme.text(view,14f,700)
                val overload=role=="demandOverload"
                val fill=if(overload)color(theme,"#FFF0EF","#3C2A2F") else theme.infoSoft
                val stroke=if(overload)"#FF453A".toColorInt() else if(role=="demand")color(theme,"#8BDC9F","#238347") else theme.infoBorder
                view.background=theme.shape(fill,16,true,stroke)
                view.setTextColor(if(overload)color(theme,"#B42318","#FF9B95") else theme.info)
                view.setPadding(theme.dp(15),theme.dp(11),theme.dp(15),theme.dp(11))
            }
            "orderMeta" -> {
                theme.text(view,13f,700);view.gravity=Gravity.START or Gravity.CENTER_VERTICAL
                view.background=theme.shape(color(theme,"#FAFAF8","#2A2F3A"),12,true,color(theme,"#E7E4DD","#454C5A"))
                view.setPadding(theme.dp(12),theme.dp(10),theme.dp(12),theme.dp(10))
            }
            "customer" -> {
                view.background=theme.shape(theme.surface,12,true);view.setTextColor(theme.ink)
                val icon=CustomerIcon(theme.dp(24),theme.ink)
                view.setCompoundDrawablesWithIntrinsicBounds(icon,null,null,null);view.text=""
                view.setPadding(theme.dp(10),theme.dp(10),theme.dp(10),theme.dp(10))
            }
            else -> {
                theme.text(view,14.5f,700)
                val prominent=role=="primary"||role=="cash"
                val fill=if(prominent)theme.accent else if(role=="card")theme.navy else if(role=="secondary")theme.bg else theme.surface
                view.background=theme.shape(fill,16,role=="outline"||role=="default")
                view.setTextColor(if(prominent)theme.accentInk else if(role=="card")Color.WHITE else theme.ink)
                view.setPadding(theme.dp(12),theme.dp(14),theme.dp(12),theme.dp(14))
            }
        }
    }
    private class CustomerIcon(private val size: Int, color: Int): Drawable() {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color;style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
        override fun draw(canvas: Canvas) {
            val save=canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
            canvas.drawCircle(12f,8f,4f,paint)
            val path=Path().apply{moveTo(4f,21f);lineTo(4f,19f);arcTo(RectF(4f,11f,20f,27f),180f,180f);lineTo(20f,21f)}
            canvas.drawPath(path,paint);canvas.restoreToCount(save)
        }
        override fun getIntrinsicWidth()=size
        override fun getIntrinsicHeight()=size
        override fun setAlpha(alpha:Int){paint.alpha=alpha}
        override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter}
        @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
}
