package com.mendelev.mpos.feature.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.sale.SaleQuote
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.CurrentCart
import com.mendelev.mpos.domain.workspace.OrderRules
import java.math.BigDecimal

internal fun cashQuickAmounts(total: Long): List<Long> = (listOf(total)+listOf(500L,1000L,2000L,5000L,10000L,20000L).mapNotNull {step->
    runCatching {Math.multiplyExact(total/step+if(total%step==0L) 0 else 1,step)}.getOrNull()
}).filter {it>=total && it>0}.distinct()
internal fun cashKey(raw: String,key: String): String {
    if(key=="backspace") return raw.dropLast(1)
    if(key==",") return if(raw.contains(',') || raw.contains('.')) raw else raw.ifEmpty {"0"}+","
    val decimalPart=raw.replace('.',',').substringAfter(',',"")
    if(key.length!=1 || key[0] !in '0'..'9' || raw.length>=15 || decimalPart.length>=2) return raw
    return (if(raw=="0") "" else raw)+key
}
private fun displayCash(raw: String)=runCatching {ShiftRules.money(ShiftRules.cash(raw.ifBlank {"0"}.trimEnd(',','.')))}.getOrDefault("0,00 BYN")

/** P01–P03 pinned .payment-page; financial commands stay in SaleViewModel/repositories. */
@Composable internal fun PaymentPage(cart: CurrentCart,quote: SaleQuote?,given: String,enabled: Boolean,canPay: Boolean,
    message: String?,restored: Boolean,onGiven: (String)->Unit,onBack: ()->Unit,onSplit: ()->Unit,onPay: (String)->Unit,
    onOptions: ()->Unit,notice: @Composable ()->Unit={},resolve: (@Composable ()->Unit)?=null) {
    var keypad by rememberSaveable(cart.sessionId) {mutableStateOf(false)}
    ProvideTextStyle(LocalTextStyle.current.copy(platformStyle=PlatformTextStyle(includeFontPadding=false),letterSpacing=0.sp)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("paymentPage")) {
        val compact=maxWidth<=800.dp
        val receiptMax=maxHeight*.30f
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                MposPosControl("← Назад",onBack,Modifier.height(44.dp).testTag("paymentBack"),enabled,height=44.dp,horizontalPadding=18.dp)
                Text("Оплата",fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
            }
            if(compact) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                PaymentReceipt(cart,quote,enabled,onOptions,Modifier.fillMaxWidth().heightIn(max=receiptMax))
                PaymentBox(quote,given,enabled,canPay,message,restored,keypad,{keypad=true;onGiven("")},onGiven,onSplit,onPay,notice,resolve,Modifier.weight(1f))
            } else Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                PaymentReceipt(cart,quote,enabled,onOptions,Modifier.weight(.58f).fillMaxHeight())
                PaymentBox(quote,given,enabled,canPay,message,restored,keypad,{keypad=true;onGiven("")},onGiven,onSplit,onPay,notice,resolve,Modifier.weight(1.42f).fillMaxHeight())
            }
        }
    }
    }
}
@Composable internal fun PaymentReceipt(cart: CurrentCart,quote: SaleQuote?,enabled: Boolean,onOptions: ()->Unit,modifier: Modifier) {
    PaymentPanel(modifier.testTag("paymentReceipt")) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Чек",Modifier.padding(bottom=12.dp),fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.ExtraBold)
            if(cart.details.label.isNotBlank()) Text(cart.details.label,Modifier.padding(bottom=6.dp),fontSize=18.sp,lineHeight=24.3.sp,fontWeight=FontWeight.ExtraBold)
            Text(OrderRules.typeLabel(cart.details.type),Modifier.padding(bottom=12.dp),fontSize=12.5.sp,lineHeight=18.125.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            cart.lines.forEach {line->
                val net=quote?.lines?.find {it.id==line.id}
                Row(Modifier.fillMaxWidth().clickable(enabled=enabled,onClickLabel="Параметры оплаты",onClick=onOptions).padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("${line.name} × ${line.quantity.replace('.',',')}",Modifier.weight(1f),fontSize=13.5.sp,lineHeight=18.5.sp)
                    Text(net?.net?.let {ShiftRules.money(it)} ?: "—",fontSize=13.5.sp,lineHeight=18.5.sp)
                }
                if((net?.discount ?: 0)>0) Row(Modifier.fillMaxWidth()) {Text("↳ Скидка",Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.primary);Text("−${ShiftRules.money(net!!.discount)}",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)}
            }
            if(cart.details.type=="delivery") {
                Row(Modifier.fillMaxWidth().clickable(enabled=enabled,onClickLabel="Параметры оплаты",onClick=onOptions).padding(vertical=5.dp).testTag("paymentDelivery")) {
                    Text("Доставка",Modifier.weight(1f),fontSize=13.5.sp,lineHeight=18.5.sp);Text(quote?.delivery?.fee?.let(ShiftRules::money) ?: "Выбрать тариф",fontSize=13.5.sp,lineHeight=18.5.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            val border=MaterialTheme.colorScheme.outline
            Canvas(Modifier.fillMaxWidth().height(1.5.dp)) {drawLine(border,Offset(0f,center.y),Offset(size.width,center.y),strokeWidth=size.height,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx())))}
            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Итого",Modifier.weight(1f),fontSize=17.sp,lineHeight=23.5.sp,fontWeight=FontWeight.ExtraBold)
                Text(quote?.total?.let(ShiftRules::money) ?: "—",fontSize=17.sp,lineHeight=23.5.sp,fontWeight=FontWeight.ExtraBold)
            }
        }
    }
}
@Composable private fun PaymentPanel(modifier: Modifier,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(19.dp),content=content)
    }
}
@Composable private fun PaymentBox(quote: SaleQuote?,given: String,enabled: Boolean,canPay: Boolean,message: String?,restored: Boolean,
    keypad: Boolean,openKeypad: ()->Unit,onGiven: (String)->Unit,onSplit: ()->Unit,onPay: (String)->Unit,
    notice: @Composable ()->Unit,resolve: (@Composable ()->Unit)?,modifier: Modifier) {
    val total=quote?.total ?: 0L
    PaymentPanel(modifier.testTag("paymentBox")) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("checkoutFields")) {
            // Round the combined CSS block once, rather than accumulating rounding at 1.25 density.
            val introHeight=with(LocalDensity.current) {70.7.sp.toDp()}+28.dp
            val totalHeight=with(LocalDensity.current) {53.2.sp.toDp()}+22.dp
            Column(Modifier.fillMaxWidth().height(introHeight)) {
            Row(Modifier.fillMaxWidth().height(totalHeight),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PaymentLabel("К оплате",13f)
                    Text(quote?.total?.let(ShiftRules::money) ?: "—",Modifier.padding(top=4.dp,bottom=18.dp).height(with(LocalDensity.current) {35.2.sp.toDp()}).testTag("paymentTotal"),fontSize=32.sp,lineHeight=35.2.sp,fontWeight=FontWeight.ExtraBold,overflow=TextOverflow.Visible,style=LocalTextStyle.current.copy(platformStyle=PlatformTextStyle(includeFontPadding=false),lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Center,LineHeightStyle.Trim.None)))
                }
                MposPosControl("Разделить",onSplit,Modifier.widthIn(min=120.dp).testTag("paymentSplit"),enabled && canPay,PosControl.Outline,height=49.dp,radius=16.dp,fontSize=14.5f,retainingAppearance=canPay)
            }
            PaymentLabel("Сумма")
            Spacer(Modifier.height(6.dp))
            }
            Surface(onClick=openKeypad,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=58.dp).testTag("cashGiven"),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                Box(Modifier.padding(horizontal=14.dp),contentAlignment=Alignment.CenterEnd) {Text(displayCash(given),fontSize=28.sp,letterSpacing=.2.sp,lineHeight=38.5.sp,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.onSurface)}
            }
            Spacer(Modifier.height(if(keypad) 26.dp else 24.dp))
            if(keypad) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val singleKey=(maxWidth-16.dp)/3
                Column {
                listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf(",","0","0span"),listOf("backspace","empty","empty")).forEachIndexed {row,keys->
                    if(row>0) Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        keys.filter {it!="0span"}.forEach {key->
                            if(key=="empty") Spacer(Modifier.weight(1f))
                            else MposPosControl(if(key=="backspace") "⌫" else key,{onGiven(cashKey(given,key))},(if(row==3 && key==",") Modifier.width(singleKey) else Modifier.weight(1f)).testTag("paymentKey:$key"),enabled,
                                if(key=="," || key=="backspace") PosControl.Outline else PosControl.Surface,fontSize=if(key=="," || key=="backspace") 14f else 19f,retainingAppearance=true)
                        }
                    }
                }
                }
            } else cashQuickAmounts(total).chunked(3).forEachIndexed {row,values->
                if(row>0) Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    values.forEach {amount->MposPosControl(ShiftRules.money(amount),{onGiven(BigDecimal.valueOf(amount,2).toPlainString())},Modifier.weight(1f).testTag("paymentQuick:$amount"),enabled,retainingAppearance=true)}
                    repeat(3-values.size) {Spacer(Modifier.weight(1f))}
                }
            }
            Spacer(Modifier.height(14.dp));PaymentLabel("Сдача");Spacer(Modifier.height(6.dp))
            val tender=runCatching {ShiftRules.cash(given.ifBlank {"0"}.trimEnd(',','.'))}.getOrDefault(0)
            Text(ShiftRules.money((tender-total).coerceAtLeast(0)),Modifier.testTag("paymentChange"),fontSize=21.sp,lineHeight=29.sp,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.primary)
            if(message!=null) {Spacer(Modifier.height(12.dp));MposNotice(message,true)}
            notice()
        }
        Spacer(Modifier.height(14.dp))
        if(resolve!=null) resolve() else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MposPosControl("Оплата картой",{onPay("card")},Modifier.weight(1f).testTag("salePrepareCard"),enabled && canPay,PosControl.Card,52.dp,16.dp,14.5f,"card",retainingAppearance=canPay)
            MposPosControl("Оплатить",{onPay("cash")},Modifier.weight(1f).testTag("salePrepare"),enabled && canPay,PosControl.Cash,52.dp,16.dp,14.5f,"cash",retainingAppearance=canPay)
        }
    }
}
@Composable private fun PaymentLabel(text: String,size: Float=12.5f) {Text(text,Modifier.height(with(LocalDensity.current) {(size+5).sp.toDp()}),fontSize=size.sp,lineHeight=(size+5).sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)}
