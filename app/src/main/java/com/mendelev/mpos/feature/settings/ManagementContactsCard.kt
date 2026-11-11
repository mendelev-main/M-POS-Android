package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.*
import java.util.UUID

@Composable internal fun ManagementContactsScreen(generation: Long,onBack: ()->Unit) {
    androidx.activity.compose.BackHandler {onBack()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("managementScreen")) {
        MposAppBar {}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                MposButton("Назад",onBack,style=ActionStyle.Outline)
                Text("Руководитель",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            }
            ManagementContactsCard(generation)
        }
    }
}

/** Contact directory only; a form is committed atomically, never an account/password. */
@Composable internal fun ManagementContactsCard(generation: Long) {
    val context=LocalContext.current
    val repo=remember {ManagementContactsRepository(CatalogDatabase.get(context))}
    val scope=rememberCoroutineScope()
    var baseline by remember {mutableStateOf<ManagementContacts?>(null)}
    var form by remember {mutableStateOf<ManagementContacts?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(repo) {
        try {val saved=withContext(Dispatchers.IO) {repo.read()};baseline=saved;form=saved}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {error="Не удалось прочитать контакты"}
    }
    val current=form
    if(current==null) {
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)} ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    Column(Modifier.fillMaxWidth().testTag("managementCard"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ManagementContactFields("Собственник",current.owner,!busy) {form=current.copy(owner=it)}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Администраторы",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            MposPosControl("",{form=current.copy(administrators=current.administrators+ManagementContact(UUID.randomUUID().toString()))},
                Modifier.size(44.dp).testTag("managementAdd"),enabled=!busy && current.administrators.size<100,
                kind=PosControl.Secondary,height=44.dp,radius=16.dp,icon="add")
        }
        current.administrators.forEachIndexed {index,c->
            ManagementContactFields("Администратор "+(index+1),c,!busy) {updated->
                form=current.copy(administrators=current.administrators.map {if(it.id==c.id) updated else it})
            }
        }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        MposButton("Сохранить",{
            fun trim(c: ManagementContact)=c.copy(phone=c.phone.trim(),email=c.email.trim(),telegramId=c.telegramId.trim())
            val next=current.copy(owner=trim(current.owner),administrators=current.administrators.map(::trim))
            busy=true;error=null
            scope.launch {
                try {
                    val saved=withContext(Dispatchers.IO) {repo.save(next,generation);repo.read()}
                    baseline=saved;form=saved
                } catch(e:CancellationException) {throw e}
                catch(e:Exception) {error=e.message ?: "Не удалось сохранить контакты"}
                finally {busy=false}
            }
        },Modifier.fillMaxWidth().testTag("managementSave"),enabled=!busy && current!=baseline,busy=busy)
    }
}

@Composable private fun ManagementContactFields(title: String,c: ManagementContact,enabled: Boolean,onChange: (ManagementContact)->Unit) {
    val suffix=if(c.id=="owner") "" else ":"+c.id
    Surface(Modifier.fillMaxWidth().testTag("managementContact:"+c.id),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            MposField(c.phone,{onChange(c.copy(phone=it.take(64)))},"Номер телефона",Modifier.testTag("managementPhone"+suffix),enabled=enabled)
            MposField(c.email,{onChange(c.copy(email=it.take(254)))},"Почта",Modifier.testTag("managementEmail"+suffix),enabled=enabled)
            MposField(c.telegramId,{onChange(c.copy(telegramId=it.take(64)))},"Telegram ID",Modifier.testTag("managementTelegram"+suffix),enabled=enabled)
        }
    }
}
