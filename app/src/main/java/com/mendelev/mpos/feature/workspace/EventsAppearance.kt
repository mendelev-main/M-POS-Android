package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*

/** EV06: approved Cards mockup tokens, scoped to Events (never recolors the POS). */
@Composable internal fun eventColor(light: Long,dark: Long)=Color(if(isMposDarkTheme()) dark else light)
@Composable internal fun EventsTheme(content: @Composable ()->Unit) {
    val c=MaterialTheme.colorScheme
    MaterialTheme(colorScheme=c.copy(
        background=eventColor(0xFFF2F4F6,0xFF1B1F27),surface=eventColor(0xFFFFFFFF,0xFF222631),
        onSurface=eventColor(0xFF202633,0xFFEEF1F5),onBackground=eventColor(0xFF202633,0xFFEEF1F5),
        onSurfaceVariant=eventColor(0xFF626D7C,0xFFAAB2BF),outline=eventColor(0xFFDCE1E8,0xFF373F4D),
        primary=eventColor(0xFF16845E,0xFF8FE0BE),onPrimary=eventColor(0xFFFFFFFF,0xFF10281E),
        primaryContainer=eventColor(0xFFE3F3EB,0xFF263E35),onPrimaryContainer=eventColor(0xFF166A4E,0xFF8FE0BE),
        error=eventColor(0xFFAF4149,0xFFF09A9E),errorContainer=eventColor(0xFFFBE7E9,0xFF472D33)
    ),content=content)
}

/** Soft selection and control geometry from the approved mock, rather than filled POS keys. */
@Composable internal fun EventControl(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    active: Boolean=false,primary: Boolean=false,icon: String?=null) {
    val c=MaterialTheme.colorScheme
    val ink=when {primary->c.onPrimary;active->c.onPrimaryContainer;else->c.onSurface}
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=44.dp),shape=RoundedCornerShape(13.dp),
            color=when {primary->c.primary;active->c.primaryContainer;else->c.background},
            contentColor=ink.copy(alpha=if(enabled) 1f else .45f),
            border=if(primary) null else BorderStroke(1.dp,if(active) c.onPrimaryContainer else c.outline)) {
            Row(Modifier.padding(horizontal=if(label.isEmpty()) 0.dp else 10.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                icon?.let {MposPosIcon(it,Modifier.size(16.dp));if(label.isNotEmpty()) Spacer(Modifier.width(7.dp))}
                if(label.isNotEmpty()) Text(label,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
            }
        }
    }
}

@Composable internal fun EventMinutes(value: String,onChange: (String)->Unit,enabled: Boolean) {
    val c=MaterialTheme.colorScheme
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Минут на приготовление",fontSize=12.sp,color=c.onSurfaceVariant)
        Surface(shape=RoundedCornerShape(11.dp),color=c.surface,border=BorderStroke(1.dp,c.outline)) {
            BasicTextField(value,onChange,enabled=enabled,singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                textStyle=TextStyle(fontFamily=MaterialTheme.typography.bodyMedium.fontFamily,fontSize=16.sp,color=c.onSurface),
                modifier=Modifier.fillMaxWidth().heightIn(min=44.dp).padding(10.dp),decorationBox={inner->
                    Box(contentAlignment=Alignment.CenterStart) {if(value.isEmpty()) Text("Например, 25",fontSize=16.sp,color=c.onSurfaceVariant);inner()}
                })
        }
    }
}
