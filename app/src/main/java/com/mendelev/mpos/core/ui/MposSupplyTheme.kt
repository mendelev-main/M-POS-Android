package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved supplies mockup palette, scoped so other POS screens keep their current theme. */
@Composable fun MposSupplyTheme(content: @Composable ()->Unit) {
    val dark=isMposDarkTheme();val c=MaterialTheme.colorScheme
    MaterialTheme(colorScheme=c.copy(
        background=if(dark) Color(0xFF191C24) else Color(0xFFF3F4F3),
        surface=if(dark) Color(0xFF242833) else Color.White,
        surfaceContainer=if(dark) Color(0xFF242833) else Color.White,
        onSurface=if(dark) Color(0xFFF2F3F7) else Color(0xFF252B38),
        onSurfaceVariant=if(dark) Color(0xFFA8AEBB) else Color(0xFF667080),
        outline=if(dark) Color(0xFF3B414F) else Color(0xFFDCE0E5),
        primary=if(dark) Color(0xFF39C59B) else Color(0xFF087C5A),
        onPrimary=if(dark) Color(0xFF10281F) else Color.White,
        error=if(dark) Color(0xFFFFAAA4) else Color(0xFFB43D3B),
        errorContainer=if(dark) Color(0xFF492D30) else Color(0xFFFCE8E6)
    ),content=content)
}

@Composable fun MposSupplyAction(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    kind: PosControl=PosControl.Surface,height: Dp=44.dp,radius: Dp=16.dp,icon: String?=null) {
    if(kind!=PosControl.Receiving) {MposPosControl(label,onClick,modifier,enabled,kind,height,radius,icon=icon);return}
    val dark=isMposDarkTheme();val ink=if(dark) Color(0xFFE9C989) else Color(0xFF80600C)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=height),shape=RoundedCornerShape(radius),
        color=if(dark) Color(0xFF433722) else Color(0xFFFFF1C1),contentColor=ink,
        border=BorderStroke(1.dp,ink.copy(alpha=.38f))) {
        Row(Modifier.padding(horizontal=15.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Text(label,fontSize=14.sp,fontWeight=FontWeight.Bold)
        }
    }
    }
}
