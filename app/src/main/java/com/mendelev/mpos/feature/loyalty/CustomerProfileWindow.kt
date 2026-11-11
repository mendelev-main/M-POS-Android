package com.mendelev.mpos.feature.loyalty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.workspace.CurrentCart

/** Web05db2b7: customer-profile-modal / loyalty-summary-card, 560px, avatar52, gap14. */
@Composable internal fun CustomerProfileWindow(profile: LoyaltySelection,cart: CurrentCart,loading: Boolean,busy: Boolean,error: String?,
    onClose: ()->Unit,onChange: ()->Unit,onDone: ()->Unit,onRemove: ()->Unit,onRetry: ()->Unit,onEnroll: ()->Unit,
    onGift: (String,Boolean)->Unit,onTelegram: ()->Unit) {
    val locked=busy || loading
    val telegram=Regex("[A-Za-z][A-Za-z0-9_]{3,31}").matches(profile.customer.username.removePrefix("@"))
    MposDialog("",onClose,closable=!locked,maxWidth=560.dp,header={
        CustomerProfileHeading(profile.customer,telegram && !locked,onTelegram)
    }) {
        Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("loyaltyCustomerWindow"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            when {
                loading->CustomerProfileState("Загружаем программы","Данные клиента обновляются…")
                error!=null->CustomerProfileState("Не удалось загрузить программы","Проверьте интернет и повторите.",true) {
                    MposButton("Повторить",onRetry,enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("customerProfileRetry"))
                }
                !profile.customer.active->CustomerProfileState("Участие в лояльности не оформлено","Начисления и подарки недоступны до получения согласия.") {
                    MposButton("Оформить участие на кассе",onEnroll,enabled=!locked,modifier=Modifier.testTag("loyaltyEnroll"))
                }
                profile.programs.isEmpty()->Text("Для клиента пока нет активных программ.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                else->profile.programs.forEach {program->
                    val available=program.rewards>0
                    val selected=program.id in profile.selected
                    val applied=if(selected) runCatching {LoyaltyRules.allocation(cart.copy(loyalty=profile),cart.lines.mapNotNull {l->l.discount?.let {l.id to it}}.toMap()).gifts.any {it.programId==program.id}}.getOrDefault(false) else false
                    Surface(Modifier.fillMaxWidth().testTag("customerProgram:${program.id}"),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(program.name,fontSize=14.sp,fontWeight=FontWeight.Bold)
                                    Text(if(available) "Накопление продолжится после использования подарка" else "Прогресс: ${program.progress} из ${program.required}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment=Alignment.End) {
                                    Text("Подарок:",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if(available) "доступен" else "нет",fontSize=20.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                            if(available) {
                                Surface(onClick={onGift(program.id,!selected)},enabled=!locked,shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("loyaltyGift:${program.id}")) {
                                    Row(Modifier.heightIn(min=48.dp).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        Checkbox(selected,null,enabled=!locked,modifier=Modifier.size(22.dp))
                                        Text("Использовать подарок в этом заказе",fontSize=14.sp,fontWeight=FontWeight.Bold)
                                    }
                                }
                                Text(when {selected && applied->"Подарок применён к этому заказу.";selected->"Подарок ещё не применён. Добавьте в заказ товар, участвующий в акции.";else->"Отметьте подарок, чтобы использовать его в этом заказе."},fontSize=13.sp,lineHeight=19.5.sp,color=if(selected && applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=if(selected && applied) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Сменить клиента",onChange,Modifier.weight(1f),enabled=!locked,style=ActionStyle.Secondary)
            MposButton("Готово",onDone,Modifier.weight(1f).testTag("customerDone"),enabled=!locked && (error==null || profile.selected.isEmpty()))
        }
        OutlinedButton(onRemove,Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("customerRemove"),enabled=!locked,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
            Text("Убрать клиента из заказа",fontWeight=FontWeight.Bold)
        }
    }
}

@Composable private fun CustomerProfileHeading(customer: LoyaltyCustomer,telegramEnabled: Boolean,onTelegram: ()->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("customerProfileHeading")) {
        val narrow=maxWidth<464.dp
        Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Surface(Modifier.size(52.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment=Alignment.Center) {Text(customer.name.trim().take(1).uppercase(),fontSize=20.sp,fontWeight=FontWeight.ExtraBold)}
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(customer.name,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text(customer.phone,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(!narrow) MposButton("Написать в Telegram",onTelegram,enabled=telegramEnabled,style=ActionStyle.Secondary,modifier=Modifier.testTag("customerTelegram"))
            }
            if(narrow) MposButton("Написать в Telegram",onTelegram,Modifier.fillMaxWidth().testTag("customerTelegram"),enabled=telegramEnabled,style=ActionStyle.Secondary)
        }
    }
}

@Composable private fun CustomerProfileState(title: String,detail: String,isError: Boolean=false,action: (@Composable ColumnScope.()->Unit)?=null) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,if(isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(title,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text(detail,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke(this)
        }
    }
}
