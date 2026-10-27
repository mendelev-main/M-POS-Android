package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** Pinned pos.html .pcard; bounded row height is owned by its home/category/folder grid. */
@Composable fun MposWorkspaceTile(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    categoryColor: String?=null, symbol: String?=null, compact: Boolean=false,
    content: @Composable ColumnScope.() -> Unit) {
    val category=categoryColor?.let {Color(android.graphics.Color.parseColor(it))}
    val dark=isSystemInDarkTheme()
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(if(compact) 14.dp else 16.dp),
        color=if(category!=null && !dark) category else MaterialTheme.colorScheme.surface,
        border=BorderStroke(if(category!=null && dark) 2.dp else 1.dp,if(category!=null && dark) category else Color.Transparent),
        modifier=modifier.fillMaxWidth().heightIn(min=if(compact) 120.dp else 128.dp),shadowElevation=1.dp) {
        Box {
            if(!symbol.isNullOrBlank()) Text(symbol,Modifier.align(Alignment.Center),style=MaterialTheme.typography.titleLarge.copy(fontSize=46.sp,fontWeight=FontWeight.ExtraBold),
                color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
            Column(Modifier.fillMaxSize().padding(if(compact) 10.dp else 14.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 6.dp else 10.dp),content=content)
        }
    }
}
