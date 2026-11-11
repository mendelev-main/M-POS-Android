package com.mendelev.mpos.feature.workspace

import com.mendelev.mpos.core.ui.isMposDarkTheme
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

/** pos.html: screen-pos / pos-toolbar / cart-*; baseline mapping CSS px → dp/sp. */
internal data class WorkspaceGeometry(val columns: Int = 5, val homeRows: Int = 4)
internal val LocalWorkspaceGeometry = staticCompositionLocalOf { WorkspaceGeometry() }
internal enum class PosTone { Neutral, Info, Zone, Primary, Warning, Demand }

@Composable internal fun PosAction(label: String, onClick: () -> Unit, modifier: Modifier=Modifier,
    enabled: Boolean=true, tone: PosTone=PosTone.Neutral, footer: Boolean=false, meta: Boolean=false, retainingAppearance: Boolean=false) {
    val dark=isMposDarkTheme()
    val colors=when(tone) {
        PosTone.Info -> Triple(if(dark) Color(0xFF26334A) else Color(0xFFE8F0FF),if(dark) Color(0xFFBFD5FF) else Color(0xFF31558F),if(dark) Color(0xFF3C506F) else Color(0xFFC7D7F2))
        PosTone.Demand -> Triple(if(dark) Color(0xFF26334A) else Color(0xFFE8F0FF),if(dark) Color(0xFFBFD5FF) else Color(0xFF31558F),Color(0xFF34C759).copy(alpha=.55f))
        PosTone.Zone -> Triple(if(dark) Color(0xFF332B45) else Color(0xFFF0E9FF),if(dark) Color(0xFFDCCBFF) else Color(0xFF5E4691),if(dark) Color(0xFF51436C) else Color(0xFFD7C8F5))
        PosTone.Primary -> Triple(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.onPrimary,Color.Transparent)
        PosTone.Warning -> Triple(if(dark) Color(0xFFE9C989) else Color(0xFF8A5A17),MaterialTheme.colorScheme.surface,Color.Transparent)
        else -> if(meta) Triple(if(dark) Color(0xFF2A2F3A) else Color(0xFFFAFAF8),if(dark) Color(0xFFF4F6FA) else MaterialTheme.colorScheme.onSurface,if(dark) Color(0xFF454C5A) else MaterialTheme.colorScheme.outline)
            else Triple(MaterialTheme.colorScheme.background,MaterialTheme.colorScheme.onSurface,Color.Transparent)
    }
    val shape=RoundedCornerShape(if(meta || (footer && tone!=PosTone.Primary)) 12.dp else 16.dp)
    Box(modifier.heightIn(min=if(footer) 47.dp else if(meta) 40.dp else 44.dp).clip(shape)
        .background(colors.first.copy(alpha=if(enabled || retainingAppearance) 1f else .4f)).border(1.dp,colors.third,shape)
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick)
        .padding(horizontal=if(footer || meta) 12.dp else 15.dp,vertical=if(footer) 14.dp else if(meta) 10.dp else 11.dp),contentAlignment=if(meta) Alignment.CenterStart else Alignment.Center) {
        Text(label,color=colors.second.copy(alpha=if(enabled || retainingAppearance) 1f else .45f),
            style=MaterialTheme.typography.bodyMedium.copy(fontSize=if(meta) 13.sp else if(footer || tone==PosTone.Zone) 14.5.sp else 14.sp,
                lineHeight=if(footer) 19.sp else 20.sp,fontWeight=if(tone==PosTone.Zone) FontWeight.ExtraBold else FontWeight.Bold),
            maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
    }
}

@Composable internal fun PosIconButton(icon: String,label: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true, retainingAppearance: Boolean=false, selected: Boolean=false) {
    val shape=RoundedCornerShape(12.dp)
    Box(modifier.size(44.dp).clip(shape).background(if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
        .border(1.dp,if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,shape)
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).semantics {contentDescription=label;this.selected=selected},contentAlignment=Alignment.Center) {
        PosIcon(icon,(if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface).copy(alpha=if(enabled || retainingAppearance) 1f else .4f))
    }
}
@Composable internal fun PosIcon(icon: String,ink: Color) {
    val data=when(icon) {
        "customer" -> "M4 21v-2a8 8 0 0 1 16 0v2M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0"
        "search" -> "M16 10a6 6 0 1 1-12 0a6 6 0 1 1 12 0M15 15l6 6"
        "merge" -> "M4 5h4l6 7h6M4 19h4l6-7M17 9l3 3-3 3"
        "trash" -> "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5"
        "printer" -> "M7 8V3h10v5M7 17H4V8h16v9h-3M7 14h10v7H7zM17 11h.01"
        "bill" -> "M6 3h12v18l-3-2-3 2-3-2-3 2V3ZM9 7h6M9 11h6M9 15h3"
        else -> ""
    }
    val path=remember(data) {PathParser().parsePathString(data).toPath()}
    Canvas(Modifier.size(22.dp)) {
        with(drawContext.canvas) {
            save();scale(size.width/24f,size.height/24f)
            if(icon=="more") listOf(5f,12f,19f).forEach {drawCircle(ink,2f,androidx.compose.ui.geometry.Offset(it,12f))}
            else drawPath(path,ink,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
            restore()
        }
    }
}

@Composable internal fun WorkspaceToolbar(title: String,back: Boolean,editing: Boolean,pending: Boolean,parked: Int,
    onBack: ()->Unit,onEdit: ()->Unit,onParked: ()->Unit,onSoon: (String)->Unit,onLayoutAction: (String)->Unit={},overloaded: Boolean=false) {
    var menu by remember {mutableStateOf(false)}
    LaunchedEffect(editing) {if(!editing) menu=false}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min=maxWidth).testTag("workspaceToolbar"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        val size=Modifier.height(48.dp)
        if(back) PosAction("Назад",onBack,size.testTag("workspaceBack"),!pending,PosTone.Info,retainingAppearance=pending)
        PosAction(title,{},size.testTag("workspaceZone"),tone=PosTone.Zone)
        Spacer(Modifier.weight(1f))
        PosAction(if(overloaded) "Повышенная загрузка" else "Обычная загрузка",{onSoon("Управление загрузкой сайта")},size.testTag("workspaceDemand"),!pending && !editing,PosTone.Demand,retainingAppearance=pending && !editing)
        PosAction(if(editing) "Готово" else "Раскладка",onEdit,size.testTag("workspaceEdit"),!pending,PosTone.Info,retainingAppearance=pending)
        if(editing) Box {
            PosAction("⋯",{menu=true},size.testTag("workspaceLayoutMenu"),!pending,PosTone.Info)
            DropdownMenu(menu,{menu=false}) {
                listOf("add" to "Добавить на рабочую зону","folder" to "Создать папку","categories" to "Открыть категорию","reset" to "Сбросить раскладку").forEach {(action,label)->
                    DropdownMenuItem(text={Text(label)},onClick={menu=false;onLayoutAction(action)},enabled=!pending,modifier=Modifier.testTag("layoutMenu:$action"))
                }
            }
        }
        Surface(onClick=onParked,enabled=!pending && !editing,modifier=size.testTag("workspaceParked"),shape=RoundedCornerShape(16.dp),color=if(isMposDarkTheme()) Color(0xFF26334A) else Color(0xFFE8F0FF)) {
            Row(Modifier.padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Отложенные",style=MaterialTheme.typography.labelLarge)
                if(parked>0) Surface(shape=RoundedCornerShape(100.dp),color=Color(0xFF34C759),contentColor=Color.White) {
                    Box(Modifier.defaultMinSize(minWidth=24.dp,minHeight=24.dp).padding(horizontal=5.dp),contentAlignment=Alignment.Center) {Text(parked.toString(),style=MaterialTheme.typography.labelMedium)}
                }
            }
        }
    }
    }
}
@Composable internal fun WorkspaceTileFooter(index: com.mendelev.mpos.domain.workspace.WorkspaceAvailability?,product: com.mendelev.mpos.domain.workspace.WorkspaceProduct) {
    var stock by remember(product.id) {mutableStateOf("…")}
    var availability by remember(product.id) {mutableStateOf<com.mendelev.mpos.domain.workspace.ProductAvailability?>(null)}
    LaunchedEffect(index,product.id) {
        val projection=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            (index?.availableStockLabel(product.id) ?: "—") to index?.status(product.id)
        }
        stock=projection.first;availability=projection.second
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom) {
        Text(if(product.price.toBigDecimal().signum()==0) "Ввести цену" else posMoney(product.price.toBigDecimal()),Modifier.weight(1f).alignByBaseline(),
            style=MaterialTheme.typography.titleSmall.copy(fontSize=13.sp,fontWeight=FontWeight.ExtraBold),maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(stock,style=MaterialTheme.typography.bodySmall.copy(fontSize=11.5.sp,fontWeight=FontWeight.SemiBold),maxLines=1,
            color=if(stock.replace(',','.').toBigDecimalOrNull()?.let {it<=java.math.BigDecimal(3)}==true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.alignByBaseline().semantics {contentDescription="Количество: $stock"; stateDescription=when(availability) {
                com.mendelev.mpos.domain.workspace.ProductAvailability.SHORTAGE -> "Недостаточно для добавления"
                com.mendelev.mpos.domain.workspace.ProductAvailability.STALE -> "Проверьте текущий заказ"
                com.mendelev.mpos.domain.workspace.ProductAvailability.INVALID -> "Требуется проверка состава"
                com.mendelev.mpos.domain.workspace.ProductAvailability.AVAILABLE -> "Доступен"
                null -> "Проверяем доступность"
            }})
    }
}


// Floor at physical pixels so row rounding cannot create a one-pixel scroll range.
internal fun workspaceRowHeightPx(height: Float,rows: Int,padding: Float,gap: Float): Float =
    kotlin.math.floor(((height-2*padding-gap*(rows-1))/rows).coerceAtLeast(1f))
