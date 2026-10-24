package com.mendelev.mpos.update

import android.content.Intent
import android.util.Log
import com.mendelev.mpos.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// INSTALLER is not success: only the actual installed package version proves an update.
enum class UpdateStage { RESTORING, IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, VERIFYING, INSTALLER, ERROR }
data class UpdateUiState(
    val stage: UpdateStage = UpdateStage.RESTORING,
    val candidate: UpdateDescriptor? = null,
    val progress: Int = 0,
    val message: String? = null,
    val lastChecked: Long = 0,
) {
    val busy get() = stage in setOf(UpdateStage.RESTORING, UpdateStage.CHECKING, UpdateStage.DOWNLOADING, UpdateStage.VERIFYING, UpdateStage.INSTALLER)
}

class UpdateViewModel(val repository: UpdateRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var firstOpen = true

    init {
        operation = viewModelScope.launch {
            val completed = repository.completedUpdate()
            val restored = repository.restore()
            mutableState.value = UpdateUiState(
                stage = if (restored == null) UpdateStage.IDLE else UpdateStage.READY,
                candidate = restored,
                message = if (completed) "Приложение успешно обновлено до ${repository.installedName}" else null,
                lastChecked = repository.lastChecked,
            )
            if (restored == null && !completed) checkNow()
        }
    }

    fun openSettings() {
        // Initialization already restores/checks. Do not race it or erase the post-update receipt.
        if (firstOpen) { firstOpen = false; return }
        if (!state.value.busy && state.value.stage != UpdateStage.READY) check()
    }

    fun check() {
        if (state.value.busy) return
        operation = viewModelScope.launch { checkNow() }
    }

    private suspend fun checkNow() {
        if (!repository.releaseChannelAvailable) {
            mutableState.update { it.copy(stage = UpdateStage.IDLE, message = "Автообновления доступны в подписанной версии M-POS. Эта сборка предназначена для разработки.") }
            return
        }
        mutableState.update { it.copy(stage = UpdateStage.CHECKING, candidate = null, message = null) }
        try {
            val candidate = repository.check()
            mutableState.value = UpdateUiState(if (candidate == null) UpdateStage.CURRENT else UpdateStage.AVAILABLE,
                candidate, lastChecked = repository.lastChecked)
        } catch (_: NoPublishedUpdate) {
            mutableState.update { it.copy(stage = UpdateStage.CURRENT, message = "Опубликованных обновлений пока нет") }
        } catch (error: Exception) { handleError(error) }
    }

    fun download() {
        val update = state.value.candidate ?: return
        if (state.value.busy) return
        operation = viewModelScope.launch {
            mutableState.update { it.copy(stage = UpdateStage.DOWNLOADING, progress = 0, message = null) }
            try {
                repository.download(update) { percent -> mutableState.update { it.copy(progress = percent) } }
                mutableState.update { it.copy(stage = UpdateStage.READY, progress = 100) }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(stage = UpdateStage.AVAILABLE, progress = 0, message = "Загрузка отменена") }
                throw cancelled
            } catch (error: Exception) { handleError(error) }
        }
    }

    fun cancelDownload() { if (state.value.stage == UpdateStage.DOWNLOADING) operation?.cancel() }

    suspend fun prepareInstallation(): Intent? {
        val update = state.value.candidate ?: return null
        if (state.value.stage != UpdateStage.READY) return null
        mutableState.update { it.copy(stage = UpdateStage.VERIFYING, message = null) }
        return try {
            repository.installationIntent(update).also { mutableState.update { state -> state.copy(stage = UpdateStage.INSTALLER) } }
        } catch (error: Exception) { handleError(error); null }
    }

    fun installerClosed(message: String = "Установка не завершена. Можно повторить установку проверенного APK.") {
        mutableState.update { it.copy(stage = UpdateStage.READY, message = message) }
    }

    private fun handleError(error: Exception) {
        if (error is CancellationException) throw error
        // Release diagnostics exclude request URLs and credentials. Fixtures are loopback/emulator-host-only in DEBUG.
        Log.w("MposUpdate", "stage=${state.value.stage}; error=${error.javaClass.simpleName}", if (BuildConfig.DEBUG) error else null)
        val message = when (error) {
            is java.net.UnknownHostException -> "Нет соединения с сервером. Проверьте интернет и повторите."
            is java.net.SocketTimeoutException -> "Сервер не ответил вовремя. Повторите попытку."
            is IOException -> "Не удалось загрузить обновление. Проверьте соединение и свободное место."
            is IllegalArgumentException, is IllegalStateException -> error.message ?: "Проверка обновления не пройдена"
            else -> "Не удалось проверить обновление. Повторите попытку."
        }
        mutableState.update { it.copy(stage = UpdateStage.ERROR, message = message) }
    }
}
