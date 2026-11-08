package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.settings.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.*

internal fun orderSettingsTotal(cart: CurrentCart,discounts: Map<String,SaleDiscount>,delivery: DeliveryCharge?): Long {
    val effective=discounts.filterKeys {id->cart.lines.none {it.id==id && it.discountEdited}}+cart.lines.mapNotNull {line->line.discount?.let {line.id to it}}.toMap()
    val goods=if(cart.lines.isEmpty()) 0L else SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,effective[it.id])}).total
    return Math.addExact(goods,if(cart.details.type=="delivery") delivery?.fee ?: 0L else 0L)
}

/** One field/order mirrors openOrderSettings; selection commits immediately, text commits on Save. */
@Composable internal fun OrderSettingsWindow(details: OrderDetails,delivery: DeliveryCharge?,rates: List<SettingEntry>,ratesRevision: Long,
    total: Long?,busy: Boolean,error: String?,owner: String,revision: Long,onClose: ()->Unit,
    onCommit: (OrderDetails,()->Unit)->Unit) {
    var label by rememberSaveable(owner,revision) {mutableStateOf(details.label)}
    var name by rememberSaveable(owner,revision) {mutableStateOf(details.customerName)}
    var phone by rememberSaveable(owner,revision) {mutableStateOf(details.phone)}
    var address by rememberSaveable(owner,revision) {mutableStateOf(details.address)}
    Dialog({if(!busy) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        MposDialogStatusBar()
        val maxHeight=(LocalConfiguration.current.screenHeightDp*.92f).dp
        Surface(Modifier.padding(20.dp).widthIn(max=460.dp).fillMaxWidth().heightIn(max=maxHeight).testTag("orderSettingsWindow"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp)) {
                // The pinned Web modal has no close icon; shared dialog dismiss/back remains available.
                Text("Параметры заказа",Modifier.padding(bottom=16.dp),fontSize=18.sp,fontWeight=FontWeight.ExtraBold)
                OrderSettingsLabel("Тип заказа")
                Row(Modifier.fillMaxWidth().padding(bottom=14.dp).testTag("orderType"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf("here" to "На месте","takeaway" to "С собой","delivery" to "Доставка").forEach {(id,text)->
                        OrderSettingsChoice(text,details.type==id,!busy,Modifier.weight(1f).testTag("orderType:$id")) {
                            if(id!=details.type) onCommit(details.copy(type=id,deliveryTariff=null,deliveryEdited=true)) {}
                        }
                    }
                }
                OrderSettingsField(label,{label=it},"Название / подпись чека","Например: Иван · стол 4","orderLabel",!busy)
                OrderSettingsField(name,{name=it},"Имя клиента","Необязательно","orderCustomerName",!busy)
                OrderSettingsField(phone,{phone=it},"Телефон","Необязательно","orderPhone",!busy,true)
                if(details.type=="delivery") {
                    OrderSettingsField(address,{address=it},"Адрес доставки","Введите адрес доставки","orderAddress",!busy)
                    OrderSettingsLabel("Стоимость доставки")
                    if(rates.isEmpty()) Text("Стоимость доставки пока не настроена. Добавьте варианты во вкладке Настройки.",fontSize=13.sp,lineHeight=19.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        rates.forEach {rate->
                            val fee=rate.value.toBigDecimal().movePointRight(2).longValueExact()
                            OrderSettingsChoice("${rate.name} · ${ShiftRules.money(fee)}",delivery?.id==rate.id,!busy,Modifier.weight(1f).testTag("orderTariff:${rate.id}")) {
                                onCommit(details.copy(deliveryTariff=DeliveryCharge(rate.id,rate.name,fee,ratesRevision.coerceAtLeast(1)),deliveryEdited=true)) {}
                            }
                        }
                    }
                    if(delivery!=null && rates.none {it.id==delivery.id}) Text("Тариф заказа: ${delivery.name} · ${ShiftRules.money(delivery.fee)}",Modifier.padding(top=8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.padding(top=26.dp,bottom=14.dp)) {
                        OrderSettingsLabel("Итого заказа")
                        Text((total?.let(ShiftRules::money) ?: "—"),fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.testTag("orderTotal"))
                    }
                }
                error?.let {MposNotice(it,true)}
                Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposPosControl("Отмена",onClose,Modifier.weight(1f),enabled=!busy,kind=PosControl.Secondary,height=48.dp,radius=16.dp,fontSize=14.5f)
                    MposPosControl("Сохранить",{onCommit(details.copy(label=label,customerName=name,phone=phone,address=if(details.type=="delivery") address else "",deliveryTariff=delivery,deliveryEdited=true),onClose)},Modifier.weight(1f).testTag("orderSubmit"),enabled=!busy,kind=PosControl.Cash,height=48.dp,radius=16.dp,fontSize=14.5f)
                }
            }
        }
    }
}
@Composable private fun OrderSettingsLabel(text: String) {Text(text,Modifier.padding(bottom=6.dp),fontSize=12.5.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun OrderSettingsChoice(text: String,active: Boolean,enabled: Boolean,modifier: Modifier,onClick: ()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.semantics {selected=active},shape=RoundedCornerShape(10.dp),border=BorderStroke(1.5.dp,if(active) c.primary else c.outline),color=if(active) c.primaryContainer else c.surface,contentColor=if(active) c.primary else c.onSurfaceVariant) {
        Box(Modifier.padding(12.dp),contentAlignment=Alignment.Center) {Text(text,fontSize=14.sp,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun OrderSettingsField(value: String,onChange: (String)->Unit,label: String,placeholder: String,tag: String,enabled: Boolean,phone: Boolean=false) {
    Column(Modifier.fillMaxWidth().padding(bottom=14.dp)) {
        OrderSettingsLabel(label)
        val c=MaterialTheme.colorScheme
        BasicTextField(value,onChange,enabled=enabled,singleLine=true,textStyle=TextStyle(fontSize=15.sp,color=c.onSurface,fontFamily=MaterialTheme.typography.bodyMedium.fontFamily),keyboardOptions=KeyboardOptions(keyboardType=if(phone) KeyboardType.Phone else KeyboardType.Text),modifier=Modifier.fillMaxWidth().testTag(tag),decorationBox={inner->
            Surface(shape=RoundedCornerShape(10.dp),border=BorderStroke(1.5.dp,c.outline),color=c.surface) {
                Box(Modifier.fillMaxWidth().heightIn(min=if(tag=="orderAddress") 48.dp else 46.dp).padding(horizontal=14.dp,vertical=12.dp),contentAlignment=Alignment.CenterStart) {
                    if(value.isEmpty()) Text(placeholder,fontSize=15.sp,color=c.onSurfaceVariant)
                    inner()
                }
            }
        })
    }
}
