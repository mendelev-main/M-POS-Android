package com.mendelev.mpos.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Activity-owned root lifecycle. Observation is read-only; commands still read inside transactions. */
class MPosRootSessionOwner(private val database: MPosDatabase, private val scope: CoroutineScope) {
    sealed interface State {
        data object Loading : State
        data object AwaitingMigration : State
        data class Ready(val revision: Long, val snapshot: MPosRootSessionRepository.Snapshot) : State
        data object Failed : State
        data object Closed : State
    }

    private val monitor = Any()
    private val repository = MPosRootSessionRepository(database)
    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = mutableState.asStateFlow()
    private var worker: Job? = null
    private var epoch = 0L
    private var revision = 0L
    private var closed = false
    private var lastFingerprint: List<Pair<String, String>>? = null
    private var lastReady: State.Ready? = null

    init { refresh() }

    /** Foreground/retry rebuilds observation; a cancelled prior generation cannot publish. */
    fun refresh() = synchronized(monitor) {
        if (closed) return@synchronized
        val ticket = ++epoch
        worker?.cancel()
        mutableState.value = State.Loading
        worker = scope.launch(Dispatchers.IO) {
            database.legacyStorageShadowDao().observe(MPosRootSessionRepository.KEYS)
                // Room invalidates by table; unrelated writes and timestamp-only changes do not reproject root state.
                .distinctUntilChangedBy { rows -> rows.map { it.key to it.payload } }
                .catch { error ->
                    if (error is CancellationException) throw error
                    publish(ticket, State.Failed)
                }
                .collect { rows ->
                    if (!MPosRootSessionRepository.owned(rows)) {
                        publish(ticket, State.AwaitingMigration)
                    } else {
                        val fingerprint = rows.map { it.key to it.payload }
                        val unchanged = synchronized(monitor) { lastReady.takeIf { lastFingerprint == fingerprint } }
                        if (unchanged != null) {
                            publish(ticket, unchanged)
                            return@collect
                        }
                        val snapshot = try { repository.project(rows) } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            publish(ticket, State.Failed)
                            return@collect
                        }
                        synchronized(monitor) {
                            if (!closed && epoch == ticket) {
                                lastFingerprint = fingerprint
                                val ready = State.Ready(++revision, snapshot)
                                lastReady = ready
                                mutableState.value = ready
                            }
                        }
                    }
                }
        }
    }

    private fun publish(ticket: Long, value: State) = synchronized(monitor) {
        if (!closed && epoch == ticket) {
            if (value == State.AwaitingMigration || value == State.Failed) {
                lastReady = null
                lastFingerprint = null
            }
            mutableState.value = value
        }
    }

    /** A bridge/bootstrap request never treats the observed view snapshot as commit authority. */
    suspend fun bootstrap(): JSONObject {
        synchronized(monitor) { check(!closed) }
        val result = repository.read().bootstrap()
        synchronized(monitor) { check(!closed) }
        return result
    }

    fun close() = synchronized(monitor) {
        if (closed) return@synchronized
        closed = true
        epoch++
        worker?.cancel()
        worker = null
        lastReady = null
        lastFingerprint = null
        mutableState.value = State.Closed
    }
}
