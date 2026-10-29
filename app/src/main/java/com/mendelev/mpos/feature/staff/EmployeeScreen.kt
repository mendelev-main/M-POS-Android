package com.mendelev.mpos.feature.staff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*

/** Name, optional phone and administrator switch follow the pinned Web employee modal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun EmployeeScreen(model: EmployeeViewModel,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    val locked=ui.pending || ui.unresolved!=null
    val form=ui.form
    LaunchedEffect(model,admin.active) {model.refresh()}
    val close={if(form?.discarding==true) model.change {it.copy(discarding=false)} else if(form?.deleting==true) model.change {it.copy(deleting=false)} else model.dismiss()}
    BackHandler {if(!locked) {if(form!=null && admin.active) close() else onBack()}}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("Сотрудники",navigationEnabled=!locked && form==null) {}
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                MposButton("Настройки",onBack,enabled=!locked && form==null,style=ActionStyle.Secondary)
                Text("Сотрудники",style=MaterialTheme.typography.titleLarge)
                MposButton("Новый сотрудник",{model.edit()},enabled=admin.active && !locked && !ui.blocked,modifier=Modifier.testTag("employeeNew"))
            }
            if(!admin.active) MposNotice("Для изменения сотрудников войдите в администрирование в настройках")
            if(ui.message!=null && (form==null || !admin.active)) MposNotice(ui.message!!,ui.blocked)
            if(ui.blocked && ui.unresolved==null) MposButton("Повторить чтение",model::refresh,enabled=!ui.pending)
            if(ui.unresolved!=null && (form==null || !admin.active)) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("employeeRetry"))
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).testTag("employeeList"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(ui.snapshot?.employees.orEmpty(),key={it.id}) {employee->
                    val active=employee.id==ui.snapshot?.activeActorId
                    Surface(onClick={model.edit(employee.id)},enabled=admin.active && !locked && !ui.blocked && !active,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().testTag("employee:${employee.id}")) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(employee.name,style=MaterialTheme.typography.titleMedium)
                            Text(if(employee.role=="admin") "Администратор · не открывает смены" else "Кассир",style=MaterialTheme.typography.bodySmall)
                            if(employee.phone.isNotBlank()) Text(employee.phone)
                            if(active) Text("Работает в открытой смене · изменения после закрытия",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if(!ui.pending && !ui.blocked && ui.snapshot?.employees?.isEmpty()==true) item {Text("Сотрудников пока нет. Создайте кассира перед открытием новой смены.",Modifier.testTag("employeeEmpty"))}
            }
        }
    }
    if(form!=null && admin.active) MposDialog(if(form.discarding) "Закрыть без сохранения?" else if(form.deleting) "Удалить сотрудника?" else if(form.revision==null) "Новый сотрудник" else "Сотрудник",close,!locked,maxWidth=700.dp) {
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(ui.message!=null) MposNotice(ui.message!!,true)
            if(form.discarding) {
                Text("Изменения сотрудника ещё не сохранены.")
                MposButton("Продолжить редактирование",{model.change {it.copy(discarding=false)}},enabled=!locked,style=ActionStyle.Secondary)
                MposButton("Закрыть без сохранения",model::discard,enabled=!locked,style=ActionStyle.Danger)
            } else if(form.deleting) {
                Text("Удалить сотрудника «${form.name}»? История смен и чеков сохранится.")
                MposButton("Отмена",{model.change {it.copy(deleting=false)}},enabled=!locked,style=ActionStyle.Secondary)
                MposButton("Удалить",model::save,enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("employeeDeleteConfirm"))
            } else {
                MposField(form.name,{value->model.change {it.copy(name=value)}},"ФИО",Modifier.testTag("employeeName"),!locked)
                MposField(form.phone,{value->model.change {it.copy(phone=value)}},"Номер телефона (необязательно)",Modifier.testTag("employeePhone"),!locked)
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text("Администратор");Text("Управляет приложением через отдельный вход. Кассовые смены не открывает.",style=MaterialTheme.typography.bodySmall)}
                    Switch(form.role=="admin",{value->model.change {it.copy(role=if(value) "admin" else "employee")}},enabled=!locked,modifier=Modifier.testTag("employeeAdmin"))
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposButton("Отмена",model::dismiss,enabled=!locked,style=ActionStyle.Secondary)
                    MposButton(if(form.revision==null) "Добавить" else "Сохранить",model::save,enabled=!locked,modifier=Modifier.testTag("employeeSave"))
                    if(form.revision!=null && form.role!="admin") MposButton("Удалить",{model.change {it.copy(deleting=true)}},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("employeeDelete"))
                }
            }
            if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("employeeRetry"))
        }
    }
}
