package com.mendelev.mpos.feature.loyalty

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.loyalty.LoyaltyProgram

@Composable internal fun LoyaltyAdminScreen(onBack: ()->Unit,model: LoyaltyAdminViewModel=viewModel(key="loyalty:"+Administration.session.state.value.generation)) {
    val ui by model.state.collectAsStateWithLifecycle()
    val session by Administration.session.state.collectAsStateWithLifecycle()
    var section by rememberSaveable {mutableStateOf("programs")}
    var customerId by rememberSaveable {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf<LoyaltyProgram?>(null)}
    val permitted=session.active && session.generation==model.generation
    LaunchedEffect(permitted) {if(!permitted) onBack()}
    if(!permitted) return
    BackHandler(enabled=ui.editor==null && customerId==null && deleting==null) {if(!ui.busy) onBack()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("loyaltyAdminScreen")) {
        MposAppBar {}
        Column(Modifier.fillMaxSize().padding(26.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                MposButton("Назад",onBack,enabled=!ui.busy,style=ActionStyle.Secondary)
                Text("Программа лояльности",fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("programs" to "Программы","clients" to "Клиенты").forEach {(key,label)->
                    MposButton(label,{section=key},enabled=!ui.busy,style=if(section==key) ActionStyle.Primary else ActionStyle.Secondary,modifier=Modifier.testTag("loyaltySection:$key"))
                }
            }
            if(ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.error?.takeIf {ui.editor==null && deleting==null}?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if(section=="programs") {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Всего программ: ${ui.programs.size}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        MposButton("Обновить",model::refresh,enabled=!ui.busy,style=ActionStyle.Secondary)
                        MposButton("Новая программа",{model.edit(null)},enabled=!ui.busy && ui.loaded,modifier=Modifier.testTag("loyaltyProgramNew"))
                    }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val columns=if(maxWidth>760.dp) 2 else 1
                    LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        if(ui.loaded && ui.programs.isEmpty()) item {Text("Пока нет программ",fontSize=20.sp,fontWeight=FontWeight.Bold)}
                        items(ui.programs.chunked(columns)) {row->Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            row.forEach {p->Surface(Modifier.weight(1f).testTag("loyaltyProgram:${p.id}"),shape=RoundedCornerShape(22.dp)) {
                                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Text(p.name,Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.Bold)
                                        Text(if(p.active) "Активна" else "Выключена",color=if(p.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                                    }
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                                        Column(horizontalAlignment=Alignment.CenterHorizontally) {Text(p.required.toString(),fontSize=32.sp,fontWeight=FontWeight.ExtraBold);Text("купить товаров",fontSize=12.sp)}
                                        MposPosIcon("next",Modifier.size(24.dp))
                                        Column(horizontalAlignment=Alignment.CenterHorizontally) {Text("1",fontSize=32.sp,fontWeight=FontWeight.ExtraBold);Text("получить в подарок",fontSize=12.sp)}
                                    }
                                    Text("Для накопления: ${p.earning.size}    В подарок: ${p.reward.size}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        MposButton("Изменить",{model.edit(p)},Modifier.weight(1f),enabled=!ui.busy)
                                        MposButton(if(p.active) "Выключить" else "Включить",{model.active(p)},Modifier.weight(1f),enabled=!ui.busy,style=ActionStyle.Secondary)
                                        MposButton("Удалить",{deleting=p},enabled=!ui.busy,style=ActionStyle.Secondary)
                                    }
                                }
                            }}
                            repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                        }}
                    }
                }
            } else {
                MposWebField(ui.query,model::search,"Найти клиента","loyaltyAdminSearch",placeholder="Имя или телефон")
                if(ui.searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(!ui.searchLoading && ui.customers.isEmpty()) item {Text("Клиенты не найдены",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    items(ui.customers,key={it.id}) {customer->Surface(onClick={customerId=customer.id},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().testTag("loyaltyAdminCustomer:${customer.id}")) {
                        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(customer.name,fontWeight=FontWeight.Bold);Text(customer.phone,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            Text("Профиль",color=MaterialTheme.colorScheme.primary)
                        }
                    }}
                }
            }
        }
    }
    ui.editor?.let {form->LoyaltyProgramEditor(form,ui,model)}
    customerId?.let {LoyaltyAdminCustomerWindow(it,model.api,model.generation) {customerId=null}}
    deleting?.let {program->MposDialog("Удалить программу?",{if(!ui.busy) deleting=null},closable=!ui.busy) {
        Text(program.name)
        Text("История начислений сохраняется. Если сервер запрещает удаление использованной программы, её можно выключить.",fontSize=13.sp)
        ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена",{deleting=null},Modifier.weight(1f),enabled=!ui.busy,style=ActionStyle.Secondary)
            MposButton("Удалить",{model.delete(program) {deleting=null}},Modifier.weight(1f).testTag("loyaltyProgramDeleteConfirm"),enabled=!ui.busy,style=ActionStyle.Danger)
        }
    }}
}

@Composable private fun LoyaltyProgramEditor(form: LoyaltyProgramForm,ui: LoyaltyAdminUi,model: LoyaltyAdminViewModel) {
    var earningQuery by rememberSaveable(form.original?.id) {mutableStateOf("")}
    var rewardQuery by rememberSaveable(form.original?.id) {mutableStateOf("")}
    val missing=(form.earning+form.reward).filter {id->ui.products.none {it.id==id}}.map {com.mendelev.mpos.backend.LoyaltyServices.Product(it,"Товар отсутствует в локальном каталоге","ID: $it")}
    val products=ui.products+missing
    MposDialog(if(form.original==null) "Новая программа" else "Изменить программу",model::closeEditor,closable=!ui.busy,maxWidth=1080.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            MposWebField(form.name,{value->model.change {it.copy(name=value)}},"Название программы","loyaltyProgramName",!ui.busy,placeholder="Например, каждый шестой кофе в подарок")
            Row(horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically) {
                MposField(form.required,{value->model.change {it.copy(required=value)}},"Купить товаров",Modifier.weight(1f).testTag("loyaltyProgramRequired"),enabled=!ui.busy,numeric=true)
                Text("Один подарок. Новое накопление начнётся после его использования.",Modifier.weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BoxWithConstraints {
                val panel: @Composable (Boolean,Modifier)->Unit={earning,modifier->
                    val selected=if(earning) form.earning else form.reward;val query=if(earning) earningQuery else rewardQuery
                    Surface(modifier,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(if(earning) "Товары для накопления" else "Товары в подарок",fontWeight=FontWeight.Bold)
                            Text("Выбрано: ${selected.size}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            MposWebField(query,{if(earning) earningQuery=it else rewardQuery=it},"Поиск",if(earning) "loyaltyEarningSearch" else "loyaltyRewardSearch",!ui.busy,placeholder="Название или категория")
                            LazyColumn(Modifier.height(240.dp)) {
                                val visible=products.filter {query.isBlank() || it.name.contains(query,true) || it.category.contains(query,true)}
                                if(visible.isEmpty()) item {Text("Товары не найдены")}
                                items(visible,key={it.id}) {p->Row(verticalAlignment=Alignment.CenterVertically) {
                                    Checkbox(p.id in selected,{checked->val next=if(checked) selected+p.id else selected-p.id;model.change {if(earning) it.copy(earning=next) else it.copy(reward=next)}},enabled=!ui.busy,modifier=Modifier.testTag("loyaltyProduct:${if(earning) "earning" else "reward"}:${p.id}"))
                                    Column {Text(p.name,fontSize=13.sp);Text(p.category,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                }}
                            }
                        }
                    }
                }
                if(maxWidth>650.dp) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {panel(true,Modifier.weight(1f));panel(false,Modifier.weight(1f))}
                else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {panel(true,Modifier.fillMaxWidth());panel(false,Modifier.fillMaxWidth())}
            }
            Text("Изменения применяются к будущим продажам. История начислений сохраняется.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена",model::closeEditor,Modifier.weight(1f),enabled=!ui.busy,style=ActionStyle.Secondary)
            MposButton(if(form.original==null) "Создать программу" else "Сохранить изменения",model::save,Modifier.weight(1f).testTag("loyaltyProgramSave"),enabled=!ui.busy,busy=ui.busy)
        }
    }
}
