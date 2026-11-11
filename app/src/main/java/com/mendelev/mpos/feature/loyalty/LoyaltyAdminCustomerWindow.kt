package com.mendelev.mpos.feature.loyalty

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalUriHandler
import com.mendelev.mpos.backend.BackendFailure
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.backend.LoyaltyApi
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.workspace.CurrentCart
import kotlinx.coroutines.*
import org.json.JSONArray
import java.util.UUID

@Composable internal fun LoyaltyAdminCustomerWindow(id: String,api: LoyaltyApi,generation: Long,onClose: ()->Unit) {
    var profile by remember(id) {mutableStateOf<LoyaltySelection?>(null)}
    var ledger by remember(id) {mutableStateOf(JSONArray())};var orders by remember(id) {mutableStateOf(JSONArray())}
    var ledgerError by remember {mutableStateOf<String?>(null)}
    var ordersError by remember {mutableStateOf<String?>(null)}
    var details by remember(id) {mutableStateOf(org.json.JSONObject())}
    var loading by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    var editor by remember {mutableStateOf<LoyaltyProgram?>(null)}
    var target by rememberSaveable {mutableStateOf("0")};var reason by rememberSaveable {mutableStateOf("")}
    var requestId by rememberSaveable {mutableStateOf(UUID.randomUUID().toString())}
    var enroll by remember {mutableStateOf(false)}
    var removeGift by remember {mutableStateOf(false)}
    var adminRejected by remember {mutableStateOf(false)}
    var webPassword by remember {mutableStateOf("")}
    var sentPayload by remember {mutableStateOf<String?>(null)}
    var tab by remember {mutableStateOf("ledger")}
    val uri=LocalUriHandler.current
    val scope=rememberCoroutineScope()
    DisposableEffect(id) {onDispose {webPassword=""}}

    LaunchedEffect(id,revision) {
        loading=true;error=null
        try {supervisorScope {
            suspend fun <T> result(block: suspend ()->T): Result<T> = try {Result.success(block())} catch(e:CancellationException) {throw e} catch(e:Exception) {Result.failure(e)}
            val p=async {result {api.profileDetails(id)}};val l=async {result {api.ledger(id)}};val o=async {result {api.orders(id)}}
            val loaded=p.await()
            profile=loaded.getOrNull()?.first;details=loaded.getOrNull()?.second ?: org.json.JSONObject()
            error=loaded.exceptionOrNull()?.message
            val history=l.await();ledger=history.getOrDefault(JSONArray());ledgerError=history.exceptionOrNull()?.message
            val purchases=o.await();orders=purchases.getOrDefault(JSONArray());ordersError=purchases.exceptionOrNull()?.message
        }} finally {loading=false}
    }
    val p=profile
    if(enroll && p!=null) {
        CustomerWindow(CurrentCart(sessionId="admin-consent-$id",loyalty=p),loading,null,{enroll=false;revision++}) {enroll=false;revision++}
        return
    }
    Dialog(onDismissRequest={if(!loading) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=!loading,dismissOnClickOutside=false)) {
      Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
       Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Карточка клиента",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
            MposButton("Назад",onClose,enabled=!loading,style=ActionStyle.Secondary)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("loyaltyAdminCustomerWindow"),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(editor==null) error?.let {
                Text(it,color=MaterialTheme.colorScheme.error)
                MposButton("Повторить загрузку",{revision++},enabled=!loading,style=ActionStyle.Secondary)
            }
            if(p!=null) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                  val wide=maxWidth>540.dp
                  val sidebarWidth=if(maxWidth>760.dp) 240.dp else 190.dp
                  val contacts: @Composable ()->Unit = {
                    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.background) {
                     Column(Modifier.padding(horizontal=22.dp,vertical=26.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.Start) {
                        Surface(Modifier.size(48.dp),shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment=Alignment.Center) {Text(p.customer.name.take(1).uppercase(),fontSize=22.sp,fontWeight=FontWeight.Bold)}
                        }
                        Text(p.customer.name,fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Text(p.customer.phone,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if(p.customer.active) "Участие активно" else "Участие не оформлено",color=if(p.customer.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Данные клиента",fontWeight=FontWeight.Bold)
                        Text(p.customer.username.takeIf {it.isNotBlank()}?.let {"Telegram: @"+it.removePrefix("@")} ?: if(details.optJSONObject("customer")?.opt("telegram_user_id")?.toString()?.let {it.isNotBlank() && it!="null"}==true) "Telegram привязан без username" else "Telegram не привязан")
                        val membership=details.optJSONObject("membership")
                        membership?.optString("consented_at")?.takeIf {it.isNotBlank() && it!="null"}?.let {Text("Дата согласия: "+customerDate(it),fontSize=13.sp)}
                        if(p.customer.active) membership?.optString("expires_at")?.takeIf {it.isNotBlank() && it!="null"}?.let {Text("Участие действует до: "+customerDate(it),fontSize=13.sp)}
                        val username=p.customer.username.removePrefix("@")
                        MposButton("Написать в Telegram",{uri.openUri("https://t.me/$username")},enabled=Regex("[A-Za-z][A-Za-z0-9_]{3,31}").matches(username),style=ActionStyle.Secondary)
                     }
                    }
                  }
                  val programs: @Composable ()->Unit = {
                   Column(Modifier.padding(horizontal=28.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("Программы лояльности",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    if(!p.customer.active) MposButton("Оформить участие на кассе",{enroll=true},enabled=!loading)
                    else if(p.programs.isEmpty()) Text("У клиента нет активных программ лояльности.")
                    p.programs.forEach {program->
                     Surface(modifier=Modifier.testTag("adminLoyaltyProgram:"+program.id),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
                      Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(program.name,fontSize=18.sp,fontWeight=FontWeight.Bold)
                                Text("${program.required} товаров — 1 подарок",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${program.progress} / ${program.required}",fontSize=26.sp,fontWeight=FontWeight.Bold)
                        }
                        val progress=program.progress.toBigDecimalOrNull()?.toInt() ?: 0
                        if(program.required in 1..20) Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            repeat(program.required) {i->Surface(Modifier.weight(1f).height(8.dp),shape=RoundedCornerShape(4.dp),color=if(i<progress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {}}
                        } else LinearProgressIndicator(progress={ (progress.toFloat()/program.required).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                        Text(if(program.rewards>0) "Доступен 1 подарок" else "Ещё ${(program.required-progress).coerceAtLeast(0)} товаров до подарка",fontWeight=FontWeight.Bold)
                        if(program.rewards>0) Text("Накопление продолжится после использования",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(if(program.rewards>0) "Подарок не использован" else "Подарков нет",Modifier.weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            MposButton("Изменить",{
                                editor=program;target=progress.toString();removeGift=false;reason="";requestId=UUID.randomUUID().toString()
                                error=null;adminRejected=false;webPassword="";sentPayload=null
                            },enabled=!loading && p.customer.active,style=ActionStyle.Secondary)
                        }
                        if(editor?.id==program.id) {
                            Text("Корректировка · ${program.name}",fontWeight=FontWeight.Bold)
                            if(program.rewards>0) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Checkbox(removeGift,{removeGift=it},enabled=!loading && sentPayload==null,modifier=Modifier.testTag("loyaltyRemoveGift"))
                                    Text("Снять доступный подарок")
                                }
                                Text("При снятии подарка прогресс останется равным нулю.",fontSize=13.sp)
                            } else {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    MposButton("−",{target=((target.toIntOrNull() ?: 0)-1).coerceAtLeast(0).toString()},enabled=!loading && sentPayload==null,style=ActionStyle.Secondary)
                                    MposField(target,{target=it},"Новое количество товаров",Modifier.weight(1f).testTag("loyaltyAdjustmentTarget"),enabled=!loading && sentPayload==null,numeric=true)
                                    MposButton("+",{target=((target.toIntOrNull() ?: 0)+1).coerceAtMost(program.required).toString()},enabled=!loading && sentPayload==null,style=ActionStyle.Secondary)
                                }
                                Text(if(target.toIntOrNull()==program.required) "Будет начислен один подарок, прогресс начнётся с нуля." else "Новый прогресс: $target из ${program.required}",fontSize=13.sp)
                            }
                            MposField(reason,{reason=it.take(500)},"Причина изменения",Modifier.testTag("loyaltyAdjustmentReason"),enabled=!loading && sentPayload==null,singleLine=false)
                            if(adminRejected) OutlinedTextField(webPassword,{webPassword=it},label={Text("Пароль администратора Web")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(autoCorrectEnabled=false,keyboardType=KeyboardType.Password),singleLine=true,enabled=!loading,modifier=Modifier.fillMaxWidth())
                            error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                MposButton("Отмена",{editor=null;error=null;webPassword=""},enabled=!loading,style=ActionStyle.Secondary)
                                MposButton(if(loading) "Сохранение…" else "Сохранить",{
                                    val count=if(program.rewards>0) 0 else target.toInt()
                                    sentPayload="$count|$reason";loading=true;error=null
                                    val credential=webPassword.takeIf {adminRejected}?.toCharArray()
                                    scope.launch {
                                        try {api.adjust(p,program,count,reason,requestId,generation,credential);editor=null;webPassword="";revision++}
                                        catch(e:CancellationException) {throw e}
                                        catch(e:Exception) {
                                            error=e.message ?: "Не удалось сохранить корректировку"
                                            if(e is BackendFailure && e.code=="ADMIN_AUTH_REQUIRED") {adminRejected=true;sentPayload=null}
                                        } finally {credential?.fill('\u0000');loading=false}
                                    }
                                },Modifier.testTag("loyaltyAdjustmentSave"),enabled=!loading && reason.isNotBlank() && (!adminRejected || webPassword.isNotEmpty()) && (if(program.rewards>0) removeGift else target.toIntOrNull()?.let {it in 0..program.required && it!=progress}==true))
                                if(error!=null) MposButton("Обновить данные",{editor=null;webPassword="";revision++},enabled=!loading,style=ActionStyle.Secondary)
                            }
                        }
                      }
                     }
                    }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposButton("История начислений (${ledger.length()})",{tab="ledger"},style=if(tab=="ledger") ActionStyle.Primary else ActionStyle.Secondary)
                    MposButton("Покупки (${orders.length()})",{tab="orders"},style=if(tab=="orders") ActionStyle.Primary else ActionStyle.Secondary)
                }
                if(tab=="orders") {
                Text("Покупки",fontSize=16.sp,fontWeight=FontWeight.Bold)
                ordersError?.let {Text(it,color=MaterialTheme.colorScheme.error);MposButton("Повторить загрузку",{revision++},enabled=!loading,style=ActionStyle.Secondary)}
                if(ordersError==null && orders.length()==0) Text("Покупок пока нет",color=MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(orders.length()) {i->val row=orders.getJSONObject(i)
                    val items=row.optJSONArray("order_items") ?: JSONArray()
                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text("${row.optString("external_id",row.optString("id"))} · ${com.mendelev.mpos.feature.workspace.onlineMoney(row.opt("total"))}",fontWeight=FontWeight.Bold)
                            Text(customerDate(row.optString("created_at"))+" · "+customerOrderStatus(row.optString("status"))+" · "+List(items.length()) {j->val item=items.getJSONObject(j);"${item.optString("product_name")} ×${item.opt("quantity")}"}.joinToString(", "),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                } else {
                Text("История начислений",fontSize=16.sp,fontWeight=FontWeight.Bold)
                ledgerError?.let {Text(it,color=MaterialTheme.colorScheme.error);MposButton("Повторить загрузку",{revision++},enabled=!loading,style=ActionStyle.Secondary)}
                if(ledgerError==null && ledger.length()==0) Text("Операций пока нет",color=MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(ledger.length()) {i->val row=ledger.getJSONObject(i)
                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(customerOperation(row.optString("operation_type")),fontWeight=FontWeight.Bold)
                            Text((row.optJSONObject("loyalty_programs")?.optString("name") ?: "")+" · "+customerDate(row.optString("created_at")),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Прогресс: ${customerSigned(row.opt("progress_delta"))} · Подарки: ${customerSigned(row.opt("reward_delta"))}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            row.optJSONObject("metadata")?.optString("reason")?.takeIf {it.isNotBlank()}?.let {Text(it,fontSize=12.sp)}
                        }
                    }
                }
                }
                   }
                  }
                  if(wide) Row(horizontalArrangement=Arrangement.spacedBy(0.dp),verticalAlignment=Alignment.Top) {
                    Box(Modifier.width(sidebarWidth)) {contacts()};Box(Modifier.weight(1f)) {programs()}
                  } else Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {contacts();programs()}
                }

            }
        }
       }
      }
    }
}

private fun customerDate(value: String): String=runCatching {
    java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}.getOrDefault("")
private fun customerSigned(value: Any?): String {
    val n=value?.toString()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
    return (if(n.signum()>0) "+" else "")+n.stripTrailingZeros().toPlainString()
}
private fun customerOperation(value: String)=when(value) {
    "EARN"->"Начисление за покупку";"REWARD_GRANTED"->"Подарок начислен";"REWARD_REDEEMED"->"Подарок использован"
    "ADJUSTMENT","MANUAL_ADJUSTMENT"->"Корректировка";"REVERSAL"->"Возврат начислений";else->value
}
private fun customerOrderStatus(value: String)=when(value) {
    "paid"->"Оплачен";"completed"->"Завершён";"returned"->"Возврат";"cancelled","canceled"->"Отменён"
    "accepted"->"Принят";"pending"->"Ожидает обработки";"new"->"Новый";else->value
}
