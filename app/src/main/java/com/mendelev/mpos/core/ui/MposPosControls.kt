package com.mendelev.mpos.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import com.mendelev.mpos.core.ui.isMposDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Explicit pinned Web POS variants; do not change generic editor/dialog controls. */
enum class PosControl { Surface, Outline, Cash, Card, Delete, Close, Secondary, Receiving, Warning }
@Composable fun MposPosControl(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    kind: PosControl=PosControl.Surface,height: Dp=46.dp,radius: Dp=12.dp,fontSize: Float=14f,icon: String?=null,horizontalPadding: Dp=14.dp,retainingAppearance: Boolean=false) {
    val c=MaterialTheme.colorScheme
    val background=when(kind) {
        PosControl.Warning->if(isMposDarkTheme()) Color(0xFF3A3022) else Color(0xFFFFF4E9)
        PosControl.Receiving->if(isMposDarkTheme()) Color(0xFF695015) else Color(0xFFFFE08A)
        PosControl.Cash->c.primary
        PosControl.Card->if(isMposDarkTheme()) Color(0xFF10131A) else Color(0xFF22273A)
        PosControl.Delete->c.errorContainer
        PosControl.Close,PosControl.Outline,PosControl.Secondary->c.background
        else->c.surface
    }
    val ink=when(kind) {PosControl.Warning->if(isMposDarkTheme()) Color(0xFFE9C989) else Color(0xFF8A5A17);PosControl.Receiving->if(isMposDarkTheme()) Color(0xFFFFE9AA) else Color(0xFF6B5200);PosControl.Cash->c.onPrimary;PosControl.Card->Color.White;PosControl.Delete->c.error;PosControl.Close->c.onSurfaceVariant;else->c.onSurface}
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=height),shape=RoundedCornerShape(radius),
        color=background,contentColor=ink.copy(alpha=if(enabled || retainingAppearance) 1f else .45f),
        border=if(kind in listOf(PosControl.Cash,PosControl.Card,PosControl.Secondary)) null else BorderStroke(1.dp,if(kind==PosControl.Warning) {if(isMposDarkTheme()) Color(0xFF5A4A30) else Color(0xFFF5DCB8)} else if(kind==PosControl.Receiving) {if(isMposDarkTheme()) Color(0xFFAA8434) else Color(0xFFE4BE45)} else if(kind==PosControl.Delete) c.error else c.outline)) {
        Row(Modifier.padding(horizontal=if(label.isEmpty()) 0.dp else horizontalPadding),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            if(icon!=null) {MposPosIcon(icon,Modifier.size(if(kind==PosControl.Close) 22.dp else if(kind==PosControl.Delete) 18.dp else 20.dp));if(label.isNotEmpty()) Spacer(Modifier.width(8.dp))}
            if(label.isNotEmpty()) Text(label,fontSize=fontSize.sp,fontWeight=if(kind==PosControl.Cash || kind==PosControl.Card || kind==PosControl.Secondary || label=="Разделить") FontWeight.Bold else FontWeight.ExtraBold)
        }
    }
    }
}

/** Paths are copied from pinned pos.html / parked-orders.js, in a 24×24 viewport. */
@Composable fun MposPosIcon(name: String,modifier: Modifier=Modifier,tint: Color?=null) {
    val ink=tint ?: when(name) {"close","delete"->MaterialTheme.colorScheme.error;"add"->MaterialTheme.colorScheme.primary;else->LocalContentColor.current}
    val path=when(name) {
        "history"->"M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12ZM12 7V12L15 14"
        "loyalty"->"M20 8V20H4V8M2 8H22M12 8V20M12 8H7A3 3 0 1 1 10 5L12 8ZM12 8H17A3 3 0 1 0 14 5L12 8Z"
        "network"->"M8 2H16V8H8ZM2 16H10V22H2ZM14 16H22V22H14ZM12 8V12M6 16V12H18V16"
        "company"->"M4 22V4L12 2V22M12 8H20V22M2 22H22M7 7H9M7 11H9M7 15H9M16 12H17M16 16H17"
        "inventory"->"M5 4H19V22H5ZM9 2H15V6H9ZM8 12L10 14L15 9M8 18H16"
        "warehouse"->"M3 9L12 3L21 9V21H3ZM7 21V12H17V21M7 16H17M7 19H17"
        "analytics"->"M3 3V21H22M7 17V12M12 17V8M17 17V5"
        "next"->"M9 6L15 12L9 18"
        "add"->"M12 5V19M5 12H19"
        "edit"->"M16 3L21 8L8 21H3V16L16 3ZM13 6L18 11"
        "close"->"M6 6L18 18M18 6L6 18"
        "bill"->"M6 3H18V21L15 19L12 21L9 19L6 21ZM9 7H15M9 11H15M9 15H12"
        "printer"->"M7 8V3H17V8M7 17H4V8H20V17H17M7 14H17V21H7ZM17 11H17.01"
        "admin"->"M12 3L20 6V12C20 17 15 20 12 22C9 20 4 17 4 12V6L12 3ZM8 12L11 15L16 9"
        "sun"->"M16 12A4 4 0 1 1 8 12A4 4 0 1 1 16 12M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.65 17.65l1.42 1.42M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42"
        "moon"->"M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5 8.5 8.5 0 1 0 20.5 14.2Z"
        "refresh"->"M3 12A9 9 0 0 1 18.36 5.64L21 8M21 3V8H16M21 12A9 9 0 0 1 5.64 18.36L3 16M8 16H3V21"
        "delete"->"M4 7H20M9 7V4H15V7M6 7L7 20H17L18 7M10 11V16M14 11V16"
        "takeaway"->"M6 7H18L20 21H4L6 7ZM9 8V6A3 3 0 0 1 15 6V8"
        "delivery"->"M3 6H14V18H3ZM14 10H18L21 14V18H14M9 18A2 2 0 1 1 5 18A2 2 0 1 1 9 18M20 18A2 2 0 1 1 16 18A2 2 0 1 1 20 18"
        "here"->"M5 3V9A3 3 0 0 0 11 9V3M8 3V21M19 21V3C15 5 14 10 18 12H19"
        "card"->"M6 5H18Q21 5 21 8V16Q21 19 18 19H6Q3 19 3 16V8Q3 5 6 5ZM3 10H21M7 15H10"
        else->"M4 5H20Q22 5 22 7V17Q22 19 20 19H4Q2 19 2 17V7Q2 5 4 5ZM15 12A3 3 0 1 1 9 12A3 3 0 1 1 15 12M6 9H6.01M18 15H18.01"
    }
    Canvas(modifier) {scale(size.width/24f,size.height/24f,pivot=androidx.compose.ui.geometry.Offset.Zero) {
        drawPath(PathParser().parsePathString(path).toPath(),ink,style=Stroke(if(name in setOf("close","sun","moon")) 1.8f else 1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
    }}
}
