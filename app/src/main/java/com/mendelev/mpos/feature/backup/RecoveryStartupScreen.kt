package com.mendelev.mpos.feature.backup

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.recovery.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun RecoveryStartupScreen(context: android.content.Context,onClose: ()->Unit,content: @Composable ()->Unit) {
    val blocking by AppDataset.runtime.blocking.collectAsStateWithLifecycle()
    val startup by produceState<RecoveryStartup?>(null) {value=withContext(Dispatchers.IO) {NativeActivationStartup(context).open().let {startup->
        if(!startup.ready) startup else try {com.mendelev.mpos.data.backup.RestoredDeviceSettings.apply(context);startup}
        catch(_:Exception) {RecoveryStartup(false,"Не удалось применить восстановленные настройки. Закройте и снова откройте M-POS; данные сохранены.")}
    }}}
    var noticeClosed by remember {mutableStateOf(false)}
    val ready=startup?.ready==true && blocking==null
    if(ready) {
        content()
        startup?.notice?.takeIf {!noticeClosed}?.let {notice->MposTheme {MposDialog("Восстановление",{noticeClosed=true}) {Text(notice);MposButton("Продолжить",{noticeClosed=true})}}}
    } else MposTheme {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("Восстановление данных",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.testTag("restoreStartupGate"))
                Text(blocking ?: startup?.notice ?: "Проверяем состояние приложения…")
                if(startup!=null || blocking!=null) {
                    Text("После закрытия снова запустите M-POS обычным способом. Сохранённые наборы данных не будут объединены")
                    MposButton("Закрыть M-POS",onClose,style=ActionStyle.Secondary)
                }
            }
        }
    }
}
