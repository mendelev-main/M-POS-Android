package com.mendelev.mpos.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable fun MposAppBar(title: String = "M-POS", subtitle: String? = null, navigationEnabled: Boolean = true, actions: @Composable RowScope.() -> Unit) {
    val navigation = LocalMposTopNavigation.current
    if (navigation != null) {
        MposTopBar(navigation, navigationEnabled)
        return
    }
    Surface(color=if(isSystemInDarkTheme()) Color(0xFF10131A) else Color(0xFF22273A),contentColor=Color.White) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            if(subtitle!=null) Text(subtitle,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Color.White.copy(alpha=0.62f))
            else Spacer(Modifier.weight(1f))
            actions()
        }
    }
}
