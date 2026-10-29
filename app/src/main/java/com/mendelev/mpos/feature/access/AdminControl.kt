package com.mendelev.mpos.feature.access

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel : ViewModel() {
    data class Ui(val busy: Boolean=false,val error: String?=null)
    private val mutable=MutableStateFlow(Ui())
    val state=mutable.asStateFlow()
    fun login(password: CharArray) {
        if(state.value.busy) {password.fill('\u0000');return}
        mutable.value=Ui(true)
        viewModelScope.launch {
            try {val ok=Administration.session.login(password);mutable.value=Ui(error=if(ok) null else "Неверный пароль")}
            catch(e: CancellationException) {throw e}
            catch(_: Exception) {mutable.value=Ui(error="Вход недоступен. При нескольких неверных попытках подождите 30 секунд")}
            finally {password.fill('\u0000')}
        }
    }
    fun logout() {
        if(state.value.busy) return
        mutable.value=Ui(true)
        viewModelScope.launch {try {Administration.session.logout()} finally {mutable.value=Ui()}}
    }
}

@Composable fun AdminControl(model: AdminViewModel=viewModel()) {
    val session by Administration.session.state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle()
    var opened by remember {mutableStateOf(false)}
    // Deliberately not rememberSaveable: password never enters saved instance state.
    var password by remember {mutableStateOf("")}
    LaunchedEffect(session.active) {if(session.active) {opened=false;password=""}}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(session.active) "Администрирование активно" else "Режим кассира",style=MaterialTheme.typography.titleMedium,
            modifier=Modifier.testTag("adminStatus"))
        MposButton(if(session.active) "Закрыть сессию администрирования" else "Администрирование",
            {if(session.active) model.logout() else opened=true},enabled=!ui.busy,style=ActionStyle.Secondary,
            modifier=Modifier.testTag("adminControl"))
    }
    if(opened && !session.active) MposDialog("Администрирование",{opened=false;password=""},closable=!ui.busy) {
        MposField(password,{password=it.take(128)},"Пароль администратора",Modifier.testTag("adminPassword"),
            enabled=!ui.busy,password=true)
        ui.error?.let {MposNotice(it,true)}
        MposButton("Войти",{val submitted=password.toCharArray();password="";model.login(submitted)},
            enabled=password.isNotEmpty() && !ui.busy,busy=ui.busy,modifier=Modifier.testTag("adminLogin"))
    }
}
