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
import androidx.compose.ui.platform.testTag
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
    onRevalidate: ()->Unit,onCheckout: ()->Unit,onSoon: (String)->Unit,shiftOpen: Boolean) {
    var more by remember {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    Surface(modifier.testTag("workspaceCartPanel"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=18.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                val count=s.cart.lines.fold(BigDecimal.ZERO) {n,l->n+l.quantity.toBigDecimal()}
                val muted=MaterialTheme.colorScheme.onSurfaceVariant
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(fontSize=17.sp,fontWeight=FontWeight.ExtraBold)) {append("Текущий заказ")}
                    withStyle(androidx.compose.ui.text.SpanStyle(fontSize=13.sp,fontWeight=FontWeight.Normal,color=muted)) {append(" — ${CatalogRules.canonical(count)} поз.")}
                },Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                PosIconButton("customer","Выбрать клиента",{onSoon("Клиент заказа")},enabled=pending==0,modifier=Modifier.testTag("workspaceCustomer"))
                Box {
                    PosIconButton("more","Действия с чеком",{more=true},enabled=pending==0,modifier=Modifier.testTag("workspaceMore"))
                    DropdownMenu(more,{more=false},modifier=Modifier.width(240.dp)) {
                        DropdownMenuItem(text={Text("Очистить чек")},leadingIcon={PosIcon("trash",MaterialTheme.colorScheme.onSurface)},
                            onClick={more=false;onClear()},enabled=pending==0 && (s.cart.lines.isNotEmpty() || s.cart.details!=OrderDetails()),modifier=Modifier.testTag("workspaceClear"))
                        DropdownMenuItem(text={Text("Печатать счёт")},leadingIcon={PosIcon("bill",MaterialTheme.colorScheme.onSurface)},
                            onClick={more=false;onSoon("Печать счёта")},enabled=pending==0 && s.cart.lines.isNotEmpty(),modifier=Modifier.testTag("workspacePrintBill"))
                        DropdownMenuItem(text={Text("Печатать заказ")},leadingIcon={PosIcon("printer",MaterialTheme.colorScheme.onSurface)},
                            onClick={more=false;onSoon("Печать заказа")},enabled=pending==0 && s.cart.lines.isNotEmpty(),modifier=Modifier.testTag("workspacePrintKitchen"))
                    }
                }
            }
            Box(Modifier.padding(start=12.dp,end=12.dp,bottom=2.dp)) {
                PosAction(listOf(s.cart.details.label,OrderRules.typeLabel(s.cart.details.type)).filter {it.isNotBlank()}.joinToString(" · "),
                    onDetails,Modifier.fillMaxWidth().testTag("workspaceDetails"),enabled=pending==0,meta=true)
            }
            if(s.cart.details.comment.isNotBlank()) Text(s.cart.details.comment,Modifier.padding(horizontal=18.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f).padding(horizontal=14.dp).testTag("workspaceCart")) {
                items(s.cart.lines,key={it.id}) {line ->
                    Column(Modifier.fillMaxWidth().testTag("workspaceLine:${line.id}").clickable(enabled=pending==0) {selected=line.id}.padding(horizontal=6.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
                            Text(line.name,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall.copy(fontSize=14.sp,lineHeight=19.sp))
                            Text(posMoney(line.unitPrice.toBigDecimal()*line.quantity.toBigDecimal()),style=MaterialTheme.typography.titleSmall.copy(fontSize=14.5.sp,fontWeight=FontWeight.ExtraBold))
                        }
                        Text("${posMoney(line.unitPrice.toBigDecimal())} / ${unitLabel(line.unit)} · ×${line.quantity}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(line.options.isNotEmpty()) Text("↳ "+line.options.joinToString(" · ") {it.name},style=MaterialTheme.typography.bodySmall)
                        if(CartEngine.stale(line,s.products)) Text("Требуется проверка: каталог изменился",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
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
                    Text("При оплате",style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Итого",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium.copy(fontSize=14.5.sp,fontWeight=FontWeight.SemiBold),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(posMoney(CartEngine.total(s.cart)),style=MaterialTheme.typography.titleLarge.copy(fontSize=26.sp),modifier=Modifier.testTag("workspaceTotal"))
                }
                if(pending>0) Text("Сохраняем…",style=MaterialTheme.typography.bodySmall)
                if(s.cart.lines.any {CartEngine.stale(it,s.products)}) MposButton("Пересчитать заказ",onRevalidate,enabled=pending==0,modifier=Modifier.testTag("workspaceRevalidate"))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PosAction("Отложить",onPark,Modifier.weight(.34f).testTag("workspacePark"),enabled=pending==0 && s.cart.lines.isNotEmpty(),footer=true)
                    PosAction(if(s.completedReceiptId!=null) "Чек сохранён" else "Оплатить",onCheckout,Modifier.weight(.66f).testTag("workspaceCheckout"),
                        enabled=pending==0 && (s.completedReceiptId!=null || (s.cart.lines.isNotEmpty() && shiftOpen)),tone=PosTone.Primary,footer=true)
                }
            }
        }
    }
    val line=s.cart.lines.find {it.id==selected}
    if(line!=null) MposDialog(line.name,{selected=null},pending==0) {
        Text("Количество",style=MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            MposButton("−",{onStep(line.id,-1)},enabled=pending<100,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspaceMinus:${line.id}"))
            MposButton(line.quantity,{selected=null;onQuantity(line.id)},enabled=pending==0,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspaceEditQuantity:${line.id}"))
            MposButton("+",{onStep(line.id,1)},enabled=pending<100,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspacePlus:${line.id}"))
        }
        Text(posMoney(line.unitPrice.toBigDecimal()*line.quantity.toBigDecimal()),style=MaterialTheme.typography.titleLarge)
        MposButton("Скидка",{selected=null;onSoon("Скидка в карточке позиции. Сейчас скидки доступны при оплате")},enabled=pending==0,style=ActionStyle.Secondary)
        MposButton("Комментарий к позиции",{selected=null;onSoon("Комментарий к позиции")},enabled=pending==0,style=ActionStyle.Secondary)
        MposButton("Удалить позицию",{selected=null;onRemove(line.id)},enabled=pending==0,style=ActionStyle.Danger,modifier=Modifier.testTag("workspaceRemove:${line.id}"))
    }
}
