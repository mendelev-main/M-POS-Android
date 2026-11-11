package com.mendelev.mpos.feature.sale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.ShiftRules
import java.math.BigDecimal

/** Web payment.js renderSplitPayment: receipt stays visible, only workspace changes. */
@Composable internal fun SplitPaymentPage(ui: SaleUi,model: SaleViewModel) {
    val progress=ui.split!!;val state=progress.editable()
    val enabled=!ui.pending && !ui.splitUncertain && ui.unresolved==null
    var amountId by remember {mutableStateOf<Long?>(null)}
    var raw by remember {mutableStateOf("")}
    var cashId by remember {mutableStateOf<Long?>(null)}
    var given by remember {mutableStateOf("")}
    val money=ShiftRules::money
    BoxWithConstraints(Modifier.fillMaxSize().padding(22.dp).testTag("splitPage")) {
        val compact=maxWidth<=800.dp
        val receiptHeight=maxHeight*.30f
        val receipt: @Composable (Modifier)->Unit={m->PaymentReceipt(ui.work!!.cart,model.estimate(),false,{},m)}
        val panel: @Composable (Modifier)->Unit={m->
            Surface(m,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        MposPosControl("Назад",model::leaveSplit,enabled=enabled,height=44.dp,retainingAppearance=true)
                        Text("Разделить оплату",Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.ExtraBold)
                        MposPosControl("−",{model.changeSplit(SplitEdit.Count(state.parts.size-1))},Modifier.size(48.dp).testTag("splitDecrease"),enabled && state.parts.size>maxOf(2,progress.paid.size),height=48.dp,retainingAppearance=true)
                        Text("${state.parts.size}",fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
                        MposPosControl("+",{model.changeSplit(SplitEdit.Count(state.parts.size+1))},Modifier.size(48.dp).testTag("splitIncrease"),enabled && state.parts.size<10,height=48.dp,retainingAppearance=true)
                    }
                    ui.message?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
                    LazyColumn(Modifier.weight(1f).testTag("splitParts"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(state.parts,key={_,p->p.id}) {index,part->
                            val p=part.payment;val editable=enabled && !part.paid
                            Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                    Row {Text("Платёж ${index+1}",Modifier.weight(1f),fontWeight=FontWeight.Bold);if(part.paid) Text("Оплачено",color=MaterialTheme.colorScheme.primary)}
                                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
                                        listOf("cash" to "Наличные","card" to "Карта").forEach {(method,label)->
                                            MposPosControl(label,{amountId=null;model.changeSplit(SplitEdit.Method(part.id,method))},Modifier.weight(1f).testTag("splitMethod:${part.id}:$method"),editable,if(p.method==method) PosControl.Cash else PosControl.Outline,44.dp,fontSize=12.5f,retainingAppearance=true)
                                        }
                                        MposPosControl(if(amountId==part.id) raw.ifBlank {"0"} else BigDecimal.valueOf(p.amount,2).toPlainString().replace('.',','),{amountId=part.id;raw=""},Modifier.weight(.8f).testTag("splitAmount:${part.id}"),editable,height=44.dp,retainingAppearance=true)
                                    }
                                    if(amountId==part.id) SplitAmountKeys(raw,editable,{raw=it;model.splitValue("amount",part.id,it.ifBlank {"0"}.trimEnd(',', '.'))})
                                    MposPosControl(if(part.paid) "Оплачено" else "Оплатить ${money(p.amount)}",{
                                        amountId=null
                                        if(p.method=="cash") {cashId=part.id;given=BigDecimal.valueOf(p.given ?: p.amount,2).toPlainString()} else model.reviewPart(part.id)
                                    },Modifier.fillMaxWidth().testTag("splitPay:${part.id}"),editable && p.amount>0,if(p.method=="cash") PosControl.Cash else PosControl.Card,44.dp,fontSize=13f,retainingAppearance=true)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row {Text("Оплачено",Modifier.weight(1f));Text(money(progress.paidAmount),fontWeight=FontWeight.Bold)}
                    Row {Text("Осталось",Modifier.weight(1f));Text(money(state.total-progress.paidAmount),Modifier.testTag("splitRemaining"),fontWeight=FontWeight.Bold)}
                    if(ui.splitUncertain) MposButton("Прочитать сохранённую оплату",model::refresh,busy=ui.pending)
                    else if(ui.unresolved!=null) MposButton("Проверить результат оплаты",model::commit,busy=ui.pending)
                    else if(progress.complete) MposButton("Завершить оплату",model::completeSplit,Modifier.fillMaxWidth().testTag("splitFinish"),busy=ui.pending)
                }
            }
        }
        if(compact) Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {receipt(Modifier.fillMaxWidth().heightIn(max=receiptHeight));panel(Modifier.weight(1f))}
        else Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {receipt(Modifier.weight(.58f).fillMaxHeight());panel(Modifier.weight(1.42f).fillMaxHeight())}
    }
    cashId?.let {id->state.parts.firstOrNull {it.id==id && !it.paid}?.let {part->
        val tender=runCatching {ShiftRules.cash(given)}.getOrNull()
        MposDialog("Оплата наличными",{if(!ui.pending) cashId=null},!ui.pending) {
            Text("Платёж ${state.parts.indexOf(part)+1}",Modifier.align(Alignment.CenterHorizontally))
            Text(money(part.payment.amount),Modifier.align(Alignment.CenterHorizontally),fontSize=30.sp,fontWeight=FontWeight.ExtraBold)
            MposField(given,{given=it},"Внесено наличными",Modifier.testTag("splitCashGiven"),!ui.pending,numeric=true)
            (listOf(part.payment.amount)+listOf(500L,1000L,2000L,5000L,10000L,20000L,50000L).filter {it>=part.payment.amount}).distinct().chunked(3).forEach {values->
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {values.forEach {n->MposPosControl(money(n),{given=BigDecimal.valueOf(n,2).toPlainString()},Modifier.weight(1f),!ui.pending,height=44.dp,retainingAppearance=true)}}
            }
            Text("Сдача",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(((tender ?: 0)-part.payment.amount).coerceAtLeast(0)),Modifier.testTag("splitCashChange"),fontSize=24.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
            ui.message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton("Отмена",{cashId=null},Modifier.weight(1f),!ui.pending,ActionStyle.Secondary)
                MposButton("Оплатить ${money(part.payment.amount)}",{model.payCashPart(id,given)},Modifier.weight(1f).testTag("splitCashConfirm"),enabled=tender!=null && tender>=part.payment.amount,busy=ui.pending)
            }
        }
    }}
}

@Composable private fun SplitAmountKeys(raw: String,enabled: Boolean,onValue: (String)->Unit) {
    listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf(",","0","backspace")).forEach {keys->
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {keys.forEach {key->MposPosControl(if(key=="backspace") "⌫" else key,{onValue(cashKey(raw,key))},Modifier.weight(1f),enabled,height=40.dp,retainingAppearance=true)}}
    }
}
