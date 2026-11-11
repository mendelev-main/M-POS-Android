package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.CatalogDatabase
import com.mendelev.mpos.data.local.productDetails
import kotlinx.coroutines.*

/** Read-only, on-demand lookup: internal notes never enter a sale, publication or print document. */
@Composable internal fun WorkspaceProductNoteWindow(productId: String,name: String,onClose: ()->Unit,
    readNote: (suspend (String)->String)?=null) {
    val context=LocalContext.current
    var note by remember(productId) {mutableStateOf<String?>(null)}
    var error by remember(productId) {mutableStateOf(false)}
    var retry by remember(productId) {mutableIntStateOf(0)}
    LaunchedEffect(productId,retry) {
        note=null;error=false
        try {note=withContext(Dispatchers.IO) {readNote?.invoke(productId) ?: CatalogDatabase.get(context).productDetails(productId).note}}
        catch(e:CancellationException) {throw e}
        catch(_:Exception) {error=true}
    }
    MposDialog(name,onClose,maxWidth=460.dp) {
        Column(Modifier.fillMaxWidth().weight(1f,fill=false).heightIn(max=360.dp)
            .verticalScroll(rememberScrollState()).testTag("workspaceProductNote"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Внутренняя заметка",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                error->{
                    Text("Не удалось загрузить заметку",color=MaterialTheme.colorScheme.error)
                    MposButton("Повторить",{retry++},style=ActionStyle.Secondary)
                }
                note==null->LinearProgressIndicator(Modifier.fillMaxWidth())
                else->Text(note!!.ifBlank {"Внутренняя заметка не добавлена."},Modifier.testTag("workspaceProductNoteText"),style=MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
