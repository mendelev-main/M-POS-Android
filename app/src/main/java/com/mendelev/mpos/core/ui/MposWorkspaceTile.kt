package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import com.mendelev.mpos.core.ui.isMposDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** Pinned pos.html .pcard; bounded row height is owned by its home/category/folder grid. */
@Composable fun MposWorkspaceTile(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    categoryColor: String?=null, symbol: String?=null, compact: Boolean=false, editing: Boolean=false,
    content: @Composable ColumnScope.() -> Unit) {
    val category=categoryColor?.let {Color(android.graphics.Color.parseColor(it))}
    val dark=isMposDarkTheme()
    val outlinedCategory=category!=null && dark
    val borderWidth=if(outlinedCategory) 2.dp else if(compact || editing) 1.5.dp else 1.dp
    val borderColor=if(outlinedCategory) category!! else if(compact || editing) MaterialTheme.colorScheme.outline else Color.Transparent
    val dashed=editing && !outlinedCategory
    val symbolSize=if(compact || editing) 32.sp else 46.sp
    val contentPadding=(if(editing) 8.dp else if(compact) 10.dp else 14.dp)+borderWidth
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(if(compact) 14.dp else 16.dp),
        color=if(category!=null && !dark) category else MaterialTheme.colorScheme.surface,
        border=if(dashed) null else BorderStroke(borderWidth,borderColor),
        modifier=modifier.alpha(if(enabled) 1f else .42f).fillMaxWidth().heightIn(min=if(compact) 120.dp else 128.dp),shadowElevation=1.dp) {
        Box(Modifier.drawWithContent {
            drawContent()
            if(dashed) {
                val width=borderWidth.toPx()
                drawRoundRect(borderColor,topLeft=Offset(width/2,width/2),size=Size((size.width-width).coerceAtLeast(0f),(size.height-width).coerceAtLeast(0f)),
                    cornerRadius=CornerRadius(16.dp.toPx()),style=Stroke(width=width,pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx()))))
            }
        }) {
            if(!symbol.isNullOrBlank()) Text(symbol,Modifier.align(Alignment.Center).alpha(if(compact || editing) .12f else .6f),style=MaterialTheme.typography.titleLarge.copy(fontSize=symbolSize,lineHeight=symbolSize,letterSpacing=1.sp,fontWeight=FontWeight.ExtraBold),
                color=MaterialTheme.colorScheme.onSurface)
            Column(Modifier.fillMaxSize().padding(contentPadding),verticalArrangement=Arrangement.spacedBy(if(editing) 4.dp else if(compact) 6.dp else 10.dp),content=content)
        }
    }
}
