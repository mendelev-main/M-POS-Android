package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.backup.WebSettingsMapping
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun CompanySettingsWindow(generation: Long,onClose: ()->Unit) {
    val db=CatalogDatabase.get(LocalContext.current);val scope=rememberCoroutineScope()
    var values by remember {mutableStateOf(JSONObject())};var revision by remember {mutableLongStateOf(0)}
    var ready by remember {mutableStateOf(false)};var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    val fields=listOf("establishmentName" to "Название заведения","legalName" to "Юридическое наименование","address" to "Адрес","deliveryAddress" to "Адрес доставки")
    LaunchedEffect(Unit) {
        try {val d=withContext(Dispatchers.IO) {db.workspace().document(WebSettingsMapping.COMPANY)};values=JSONObject(d?.payload ?: "{}");revision=d?.revision ?: 0;ready=true}
        catch(_:Exception) {error="Не удалось прочитать реквизиты"}
    }
    MposDialog("Реквизиты организации",onClose,closable=!busy,maxWidth=600.dp) {
        fields.forEach {(key,label)->MposField(values.optString(key),{v->values=JSONObject(values.toString()).put(key,v.take(10000))},label,enabled=ready && !busy)}
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена",onClose,Modifier.weight(1f),enabled=!busy,style=ActionStyle.Secondary)
            MposButton("Сохранить",{
                busy=true;val payload=values.toString()
                scope.launch {
                    try {withContext(Dispatchers.IO) {db.adminTransaction {
                        check(Administration.session.state.value.generation==generation) {"Сессия администратора завершена"}
                        check((db.workspace().document(WebSettingsMapping.COMPANY)?.revision ?: 0)==revision) {"Реквизиты изменились. Откройте окно заново"}
                        db.workspace().put(WorkspaceDocument(WebSettingsMapping.COMPANY,revision+1,payload))
                    }};onClose()}
                    catch(e:CancellationException) {throw e}
                    catch(e:Exception) {error=e.message ?: "Не удалось сохранить реквизиты"}
                    finally {busy=false}
                }
            },Modifier.weight(1f),enabled=ready && !busy,busy=busy)
        }
    }
}
