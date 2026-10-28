package com.mendelev.mpos.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pinned pos.html renderTopbar; route callbacks never execute financial commands. */
data class MposTopNavigation(
    val route: String,
    val shiftOpen: Boolean,
    val shiftLoading: Boolean = false,
    val shiftUnavailable: Boolean = false,
    val shiftPending: Boolean = false,
    val shiftName: String = "Локальный владелец",
    val inventoryReminder: Boolean=false,
    val navigate: (String) -> Unit,
)
val LocalMposTopNavigation = staticCompositionLocalOf<MposTopNavigation?> { null }
private val destinations = listOf(
    "purchaseOrders" to "Заказы", "receiving" to "Приёмка", "history" to "Чеки",
    "analytics" to "Аналитика", "bookings" to "Бронирования",
)

@Composable
fun MposTopBar(navigation: MposTopNavigation, enabled: Boolean = true) {
    val allowed = enabled && !navigation.shiftPending
    val navy = if (isSystemInDarkTheme()) Color(0xFF10131A) else Color(0xFF22273A)
    BoxWithConstraints(Modifier.fillMaxWidth().background(navy).testTag("mposTopBar").semantics { testTagsAsResourceId=true }) {
        val compact = maxWidth <= 700.dp
        Row(
            Modifier.horizontalScroll(rememberScrollState()).widthIn(min=maxWidth)
                .padding(horizontal=if(compact) 10.dp else 16.dp, vertical=if(compact) 9.dp else 12.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(if(compact) 6.dp else 10.dp),
        ) {
            TopItem("M POS", "workspace", navigation, allowed,
                selected=navigation.route in setOf("workspace", "checkout"), bold=true)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                destinations.forEach { (route, label) ->
                    TopItem(label, route, navigation, allowed, compact=compact)
                }
                if(navigation.inventoryReminder) TopItem("Инвентаризация","inventory",navigation,allowed,selected=navigation.route in setOf("inventory","inventoryWork"),compact=compact)
            }
            Spacer(Modifier.weight(1f))
            TopItem("События", "events", navigation, allowed, compact=compact, icon="bell", selected=false)
            val shiftLabel = when {
                navigation.shiftLoading -> "Загрузка смены"
                navigation.shiftUnavailable -> "Смена недоступна"
                navigation.shiftOpen -> shortEmployeeName(navigation.shiftName)
                else -> "Открыть смену"
            }
            TopItem(shiftLabel, "shift", navigation, allowed, compact=compact, shift=true, selected=false)
            Box(Modifier.size(44.dp).clip(CircleShape)
                .background(if(navigation.route=="settings") Color.White.copy(alpha=.14f) else Color.Transparent)
                .clickable(enabled=allowed, role=Role.Button) { navigation.navigate("settings") }
                .semantics { contentDescription="Настройки"; selected=navigation.route=="settings" }
                .testTag("topNav-settings"), contentAlignment=Alignment.Center) {
                TopIcon("settings", if(navigation.route=="settings") Color.White else Color.White.copy(alpha=.62f))
            }
        }
    }
}

@Composable
private fun TopItem(label: String, route: String, nav: MposTopNavigation, enabled: Boolean,
    selected: Boolean = nav.route==route, compact: Boolean=false, bold: Boolean=false,
    icon: String?=null, shift: Boolean=false) {
    val ink=if(selected || shift) Color.White else Color.White.copy(alpha=.62f)
    // 44dp interaction row, 36dp capsule at normal text scale; source header is 68dp tall.
    Box(Modifier.heightIn(min=44.dp).clip(CircleShape)
        .clickable(enabled=enabled, role=Role.Tab) {nav.navigate(route)}
        .semantics { this.selected=selected }
        .testTag("topNav-$route"), contentAlignment=Alignment.Center) {
        Row(Modifier.clip(CircleShape)
            .background(Color.White.copy(alpha=if(selected) .14f else if(shift) .08f else 0f))
            .padding(horizontal=if(compact) (if(shift) 10.dp else 11.dp) else 14.dp,
                vertical=if(compact && shift) 7.dp else 8.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(if(shift) 8.dp else 7.dp)) {
            if(shift) Canvas(Modifier.size(8.dp)) {
                val dot=if(nav.shiftLoading || nav.shiftUnavailable) Color(0xFFB9BEC8)
                    else if(nav.shiftOpen) Color(0xFF3ED28E) else Color(0xFFFF7A70)
                drawCircle(dot.copy(alpha=.25f), radius=size.minDimension/2+3.dp.toPx())
                drawCircle(dot)
            }
            if(icon!=null) TopIcon(icon, ink)
            Text(label, color=ink, style=MaterialTheme.typography.bodyMedium.copy(
                fontSize=if(shift) (if(compact) 11.sp else 13.5.sp) else if(compact && !bold) 12.sp else 14.sp,
                lineHeight=20.sp, fontWeight=if(bold) FontWeight.Bold else FontWeight.SemiBold),
                maxLines=1, softWrap=false, overflow=TextOverflow.Visible)
        }
    }
}

/** Same 24-unit SVG geometry as the source, rendered natively at 18dp. */
@Composable private fun TopIcon(kind: String, ink: Color) {
    val path=remember(kind) {
        PathParser().parsePathString(if(kind=="bell")
            "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"
        else
            "M9.7 3.4h4.6l.6 2.1c.5.2 1 .5 1.4.8l2.1-.6 2.3 4-1.5 1.5v1.6l1.5 1.5-2.3 4-2.1-.6c-.4.3-.9.6-1.4.8l-.6 2.1H9.7l-.6-2.1c-.5-.2-1-.5-1.4-.8l-2.1.6-2.3-4 1.5-1.5v-1.6L3.3 9.7l2.3-4 2.1.6c.4-.3.9-.6 1.4-.8l.6-2.1Z"
        ).toPath()
    }
    Canvas(Modifier.size(18.dp)) {
        val scale=size.width/24f
        with(drawContext.canvas) {
            save(); scale(scale,scale)
            val stroke=Stroke(width=if(kind=="bell") 1.9f else 1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            drawPath(path,ink,style=stroke)
            if(kind=="settings") drawCircle(ink,2.6f,Offset(12f,12f),style=stroke)
            restore()
        }
    }
}

private fun shortEmployeeName(name: String): String {
    val parts=name.trim().split(Regex("\\s+")).filter {it.isNotEmpty()}
    return parts.firstOrNull()?.let { surname ->
        surname + parts.drop(1).take(2).joinToString("", prefix=if(parts.size>1) " " else "") {it.first().uppercaseChar().toString()+"."}
    } ?: "Сотрудник"
}

@Composable fun PlannedModuleScreen(route: String) {
    val label=if(route=="events") "События" else destinations.firstOrNull {it.first==route}?.second ?: "M POS"
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar {}
        Column(Modifier.padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(label,style=MaterialTheme.typography.titleLarge)
            MposNotice("Раздел будет подключён на соответствующем этапе плана. Сейчас доступна навигация; данные и операции этого модуля ещё не реализованы.")
        }
    }
}
