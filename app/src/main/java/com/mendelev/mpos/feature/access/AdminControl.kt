package com.mendelev.mpos.feature.access

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

@Composable fun AdminControl(model: AdminViewModel=viewModel(),compact: Boolean=false,onPanel: (() -> Unit)?=null,iconOnly: Boolean=false) {
    val session by Administration.session.state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle()
    var opened by remember {mutableStateOf(false)}
    // Deliberately not rememberSaveable: password never enters saved instance state.
    var password by remember {mutableStateOf("")}
    LaunchedEffect(session.active) {
        if(session.active && opened) {opened=false;password="";onPanel?.invoke()}
        if(!session.active) password=""
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(iconOnly) Surface(onClick={if(session.active) onPanel?.invoke() else opened=true},enabled=!ui.busy,
            modifier=Modifier.size(48.dp).testTag("adminControl").semantics {contentDescription="Панель администратора";selected=session.active},
            shape=RoundedCornerShape(16.dp),color=if(session.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor=if(session.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            border=BorderStroke(if(session.active) 2.dp else 1.dp,if(session.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
            Box(contentAlignment=Alignment.Center) {MposPosIcon("admin",Modifier.size(22.dp))}
        } else
        MposButton("Панель администратора",
            {if(session.active) onPanel?.invoke() else opened=true},enabled=!ui.busy,style=ActionStyle.Secondary,
            modifier=Modifier.testTag("adminControl"))
    }
    if(opened && !session.active) MposDialog("Администрирование",{opened=false;password=""},closable=!ui.busy) {
        MposField(password,{password=com.mendelev.mpos.core.access.RemotePasswordInput.restore(password,it)},"Пароль администратора",Modifier.testTag("adminPassword"),
            enabled=!ui.busy,password=true)
        ui.error?.let {MposNotice(it,true)}
        MposButton("Войти",{val submitted=password.toCharArray();password="";model.login(submitted)},
            enabled=password.isNotEmpty() && !ui.busy,busy=ui.busy,modifier=Modifier.testTag("adminLogin"))
    }
}
