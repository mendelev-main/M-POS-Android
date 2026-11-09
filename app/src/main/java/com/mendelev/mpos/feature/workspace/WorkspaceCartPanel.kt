package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import java.math.BigDecimal
import java.math.RoundingMode

internal fun posMoney(value: BigDecimal)=value.setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',',')+" BYN"

@Composable internal fun WorkspaceCartPanel(s: WorkspaceSnapshot,pending: Int,modifier: Modifier,onQuantity: (String)->Unit,
    onStep: (String,Int)->Unit,onRemove: (String)->Unit,onClear: ()->Unit,onDetails: ()->Unit,onPark: ()->Unit,
    onRevalidate: ()->Unit,onCheckout: ()->Unit,onSoon: (String)->Unit,shiftOpen: Boolean,onTransfer: ()->Unit={},onOptions: (CartAction.SetOptions,String,()->Unit)->Unit={_,_,_->},error: String?=null,discounts: List<com.mendelev.mpos.domain.settings.SettingEntry> = emptyList(),onlineHeader: @Composable ()->Unit={}) {
    var selectedRevision by rememberSaveable {mutableStateOf(0L)}
    var selectedOwner by rememberSaveable {mutableStateOf("")}
    var previousQuantities by remember(s.cart.sessionId) {mutableStateOf(s.cart.lines.associate {it.id to it.quantity.toBigDecimal()})}
    var highlightTokens by remember(s.cart.sessionId) {mutableStateOf(emptyMap<String,Long>())}
    LaunchedEffect(s.cartRevision,s.cart.sessionId) {
        val next=s.cart.lines.associate {it.id to it.quantity.toBigDecimal()}
        val changed=next.filter {(id,quantity)->quantity>(previousQuantities[id] ?: BigDecimal.ZERO)}.keys
        highlightTokens=highlightTokens.filterKeys {it in next}+changed.associateWith {s.cartRevision}
        previousQuantities=next
    }
    var more by remember {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(s.cart.sessionId) {if(selectedOwner!=s.cart.sessionId) selected=null}
    Surface(modifier.testTag("workspaceCartPanel"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
        Column {
            onlineHeader()
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=18.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                val count=s.cart.lines.fold(BigDecimal.ZERO) {n,l->n+l.quantity.toBigDecimal()}
                val muted=MaterialTheme.colorScheme.onSurfaceVariant
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(fontSize=17.sp,fontWeight=FontWeight.ExtraBold)) {append("Текущий заказ")}
                    withStyle(androidx.compose.ui.text.SpanStyle(fontSize=13.sp,fontWeight=FontWeight.Medium,color=muted)) {append(" — ${CatalogRules.canonical(count)} поз.")}
                },Modifier.weight(1f),style=MaterialTheme.typography.titleMedium.copy(lineHeight=23.8.sp))
                PosIconButton("customer","Выбрать клиента",{onSoon("Клиент заказа")},enabled=pending==0,modifier=Modifier.testTag("workspaceCustomer"),retainingAppearance=pending>0)
                Box {
                    PosIconButton("more","Действия с чеком",{more=true},enabled=pending==0,modifier=Modifier.testTag("workspaceMore"),retainingAppearance=pending>0)
                    CartActionsMenu(more,{more=false},pending==0 && s.cart.lines.isNotEmpty(),
                        pending==0 && (s.cart.lines.isNotEmpty() || s.cart.details!=OrderDetails()),
                        {more=false;onTransfer()},{more=false;onClear()},
                        {more=false;onSoon("Печать счёта")},{more=false;onSoon("Печать заказа")})
                }
            }
            Box(Modifier.padding(start=12.dp,end=12.dp,bottom=2.dp)) {
                PosAction(listOf(s.cart.details.label,OrderRules.typeLabel(s.cart.details.type),s.cart.details.customerName.ifBlank {s.cart.details.phone}).filter {it.isNotBlank()}.joinToString(" · "),
                    onDetails,Modifier.fillMaxWidth().testTag("workspaceDetails"),enabled=pending==0,meta=true,retainingAppearance=pending>0)
            }
            if(s.cart.details.comment.isNotBlank()) Text(s.cart.details.comment,Modifier.padding(horizontal=18.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f).padding(horizontal=14.dp).testTag("workspaceCart")) {
                items(s.cart.lines,key={it.id}) {line ->
                    val flash=remember {androidx.compose.animation.core.Animatable(0f)}
                    val token=highlightTokens[line.id]
                    LaunchedEffect(token) {if(token!=null) {flash.snapTo(1f);flash.animateTo(0f,androidx.compose.animation.core.tween(620,easing=androidx.compose.animation.core.CubicBezierEasing(.2f,.8f,.2f,1f)))}}
                    val tint=if(isMposDarkTheme()) androidx.compose.ui.graphics.Color(0xFF244B32) else androidx.compose.ui.graphics.Color(0xFFCFF3D8)
                    val rowColor=androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface,tint,flash.value)

                    CartRowSwipe(pending==0,{onRemove(line.id)}) {
                    Column(Modifier.fillMaxWidth().background(rowColor,RoundedCornerShape(8.dp)).testTag("workspaceLine:${line.id}").semantics {customActions=listOf(CustomAccessibilityAction("Удалить позицию") {if(pending==0) {onRemove(line.id);true} else false})}.clickable(enabled=pending==0) {selectedRevision=s.cartRevision;selectedOwner=s.cart.sessionId;selected=line.id}.padding(horizontal=6.dp,vertical=9.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top,modifier=Modifier.padding(bottom=3.dp)) {
                            Text(line.name,Modifier.weight(1f).alignByBaseline(),style=MaterialTheme.typography.titleSmall.copy(fontSize=14.sp,lineHeight=18.9.sp))
                            Text(runCatching {com.mendelev.mpos.domain.shift.ShiftRules.money(com.mendelev.mpos.domain.sale.SaleMoney.quote(listOf(com.mendelev.mpos.domain.sale.SaleLineInput(line.id,line.unitPrice,line.quantity,s.sourceDiscounts[line.id]))).total)}.getOrDefault("—"),modifier=Modifier.alignByBaseline(),style=MaterialTheme.typography.titleSmall.copy(fontSize=14.5.sp,fontWeight=FontWeight.ExtraBold))
                        }
                        Text("${posMoney(line.unitPrice.toBigDecimal())} / ${if(line.sellingBasis=="recipe-batch") "порция рецепта" else unitLabel(line.unit)} · ×${line.quantity}${s.sourceDiscounts[line.id]?.let {" · "+it.name}.orEmpty()}",modifier=Modifier.padding(top=2.dp),style=MaterialTheme.typography.bodySmall.copy(lineHeight=17.4.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(line.options.isNotEmpty()) Text("↳ "+line.options.joinToString(" · ") {it.name},Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodySmall.copy(lineHeight=17.4.sp))
                        if(line.comment.isNotBlank()) Text("Комментарий: ${line.comment}",Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outline)
                }
                if(s.cart.lines.isEmpty()) item {
                    Box(Modifier.fillParentMaxHeight().fillMaxWidth().padding(horizontal=20.dp,vertical=40.dp),contentAlignment=Alignment.Center) {
                        Text("Заказ пуст.\nНажмите на товар слева, чтобы добавить его.",style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp),color=MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline)
            Column(Modifier.padding(horizontal=12.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(s.cart.details.type=="delivery") Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Доставка",style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp))
                    Text(s.sourceDelivery?.fee?.let(com.mendelev.mpos.domain.shift.ShiftRules::money) ?: "При оплате",style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Итого",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.5.sp,fontWeight=FontWeight.SemiBold),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(runCatching {com.mendelev.mpos.domain.shift.ShiftRules.money(orderSettingsTotal(s.cart,s.sourceDiscounts,s.sourceDelivery))}.getOrDefault("—"),style=MaterialTheme.typography.titleLarge.copy(fontSize=26.sp),modifier=Modifier.testTag("workspaceTotal"))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val parkWidth=maxWidth*.34f
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PosAction("Отложить",onPark,Modifier.width(parkWidth).testTag("workspacePark"),enabled=pending==0 && s.cart.lines.isNotEmpty(),footer=true,retainingAppearance=pending>0 && s.cart.lines.isNotEmpty())
                    PosAction(if(shiftOpen) "Оплатить" else "Открыть смену",onCheckout,Modifier.weight(1f).testTag("workspaceCheckout"),
                        enabled=pending==0 && (!shiftOpen || s.cart.lines.isNotEmpty()),tone=PosTone.Primary,footer=true,retainingAppearance=pending>0 && (s.cart.lines.isNotEmpty() && shiftOpen))
                }
                }
            }
        }
    }
    val line=s.cart.lines.find {it.id==selected}
    if(line!=null) CartItemWindow(line,s.sourceDiscounts[line.id],discounts,pending>0,error,{selected=null}) {quantity,comment,id,discount->
        onOptions(CartAction.SetOptions(line.id,quantity,comment,id,discount,selectedRevision),selectedOwner) {selected=null}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CartRowSwipe(enabled: Boolean,onDelete: ()->Unit,content: @Composable ()->Unit) {
    val latestDelete by rememberUpdatedState(onDelete)
    val state=rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if(state.currentValue==SwipeToDismissBoxValue.EndToStart) {
            if(enabled) latestDelete()
            state.reset()
        }
    }
    SwipeToDismissBox(state,enableDismissFromStartToEnd=false,enableDismissFromEndToStart=enabled,backgroundContent={
        if(state.dismissDirection==SwipeToDismissBoxValue.EndToStart) Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.errorContainer) {
            Box(Modifier.padding(16.dp),contentAlignment=Alignment.CenterEnd) {Text("Удалить",color=MaterialTheme.colorScheme.error)}
        }
    }) {Surface(color=MaterialTheme.colorScheme.surface) {content()}}
}

@Composable private fun CartActionsMenu(open: Boolean,onDismiss: ()->Unit,enabled: Boolean,clearEnabled: Boolean,
    onTransfer: ()->Unit,onClear: ()->Unit,onBill: ()->Unit,onKitchen: ()->Unit) {
    if(!open) return
    val offset=with(androidx.compose.ui.platform.LocalDensity.current) {52.dp.roundToPx()}
    androidx.compose.ui.window.Popup(alignment=Alignment.TopEnd,offset=androidx.compose.ui.unit.IntOffset(0,offset),onDismissRequest=onDismiss,
        properties=androidx.compose.ui.window.PopupProperties(focusable=true)) {
        Surface(Modifier.width(240.dp).testTag("cartActionsMenu"),shape=RoundedCornerShape(16.dp),
            color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),shadowElevation=12.dp) {
            Column(Modifier.padding(6.dp)) {
                @Composable fun item(label: String,icon: String,tag: String,active: Boolean,action: ()->Unit) {
                    val ink=MaterialTheme.colorScheme.onSurface.copy(alpha=if(active) 1f else .4f)
                    Row(Modifier.fillMaxWidth().heightIn(min=46.dp).testTag(tag)
                        .then(Modifier.clip(RoundedCornerShape(10.dp))).clickable(enabled=active,role=androidx.compose.ui.semantics.Role.Button,onClick=action)
                        .padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        PosIcon(icon,ink)
                        Text(label,color=ink,fontSize=14.5.sp,lineHeight=20.3.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
                    }
                }
                item("Объединить чеки","merge","workspaceTransfer",enabled,onTransfer)
                item("Очистить чек","trash","workspaceClear",clearEnabled,onClear)
                item("Печатать счёт","bill","workspacePrintBill",enabled,onBill)
                item("Печатать заказ","printer","workspacePrintKitchen",enabled,onKitchen)
            }
        }
    }
}
