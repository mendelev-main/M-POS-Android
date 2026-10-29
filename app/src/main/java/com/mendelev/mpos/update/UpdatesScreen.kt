package com.mendelev.mpos.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.R
import com.mendelev.mpos.core.ui.MposButton
import com.mendelev.mpos.core.ui.ActionStyle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdatesScreen(model: UpdateViewModel, onCatalog: (() -> Unit)? = null, onGallery: (() -> Unit)? = null, onSuppliers: (() -> Unit)? = null, onInventory: (() -> Unit)? = null, onStock: (() -> Unit)? = null, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.stage == UpdateStage.VERIFYING) { }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.installerClosed(if (context.packageManager.canRequestPackageInstalls())
            context.getString(R.string.update_permission_granted) else context.getString(R.string.update_permission_required))
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.installerClosed()
    }
    var updatesOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val administration by com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle()
    val locked=state.stage in setOf(UpdateStage.VERIFYING,UpdateStage.INSTALLER)
    LaunchedEffect(model) { model.openSettings() }
    com.mendelev.mpos.feature.settings.SettingsHome({updatesOpen=true},onCatalog,onSuppliers,onInventory,onStock,onGallery,onBack,state.candidate!=null)
    if(updatesOpen) com.mendelev.mpos.core.ui.MposDialog(stringResource(R.string.update_title),{updatesOpen=false},closable=!locked,maxWidth=820.dp) {
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()).testTag("updatesWindow"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(if(administration.active) "Администрирование активно" else "Режим кассира",style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.update_installed, model.repository.installedName, model.repository.installedCode))
                Text(stringResource(R.string.update_channel))
                if (state.lastChecked > 0) Text(stringResource(R.string.update_last_checked,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.lastChecked))),
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(when (state.stage) {
                    UpdateStage.RESTORING -> R.string.update_restoring
                    UpdateStage.IDLE -> R.string.update_idle
                    UpdateStage.CHECKING -> R.string.update_checking
                    UpdateStage.CURRENT -> R.string.update_current
                    UpdateStage.AVAILABLE -> R.string.update_available
                    UpdateStage.DOWNLOADING -> R.string.update_downloading
                    UpdateStage.READY -> R.string.update_ready
                    UpdateStage.VERIFYING -> R.string.update_verifying
                    UpdateStage.INSTALLER -> R.string.update_installer
                    UpdateStage.ERROR -> R.string.update_error
                }), style = MaterialTheme.typography.titleMedium)
                state.candidate?.let { candidate ->
                    Text(stringResource(R.string.update_candidate, candidate.versionName,
                        "%.1f".format(candidate.apkBytes / (1024.0 * 1024))))
                    if (candidate.notes.isNotBlank()) Text(candidate.notes)
                }
                if (state.stage == UpdateStage.DOWNLOADING) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.update_percent, state.progress))
                    MposButton(stringResource(R.string.update_cancel), model::cancelDownload, style = ActionStyle.Secondary)
                } else if (state.stage in setOf(UpdateStage.CHECKING, UpdateStage.RESTORING, UpdateStage.VERIFYING)) {
                    CircularProgressIndicator()
                }
                state.message?.let { Text(it, color = if (state.stage == UpdateStage.ERROR)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!state.busy) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        if (state.stage == UpdateStage.READY) {
                            MposButton(stringResource(R.string.update_install), onClick = {
                                if (!context.packageManager.canRequestPackageInstalls()) {
                                    try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                                    catch (_: ActivityNotFoundException) { model.installerClosed(context.getString(R.string.update_settings_unavailable)) }
                                } else scope.launch {
                                    val intent = model.prepareInstallation() ?: return@launch
                                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                        model.installerClosed(context.getString(R.string.update_return_to_install))
                                        return@launch
                                    }
                                    try { installer.launch(intent) }
                                    catch (_: ActivityNotFoundException) { model.installerClosed(context.getString(R.string.update_installer_unavailable)) }
                                    catch (_: SecurityException) { model.installerClosed(context.getString(R.string.update_permission_required)) }
                                }
                            })
                        } else if (state.candidate != null) {
                            MposButton(stringResource(R.string.update_download), model::download)
                        }
                        MposButton(stringResource(R.string.update_check), model::check, style = ActionStyle.Secondary)
                    }
                }

            Text(stringResource(R.string.update_explanation),style=MaterialTheme.typography.bodySmall)
            MposButton("К настройкам",{updatesOpen=false},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("updatesBack"))
        }
    }
}
