package com.mendelev.mpos.core.access

import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Runtime-only authority. Never put this in a command fingerprint, draft, SavedState or database. */
class AdminAuthorization internal constructor(internal val session: AdminSession,internal val generation: Long): AbstractCoroutineContextElement(Key) {
    companion object Key: CoroutineContext.Key<AdminAuthorization>
    fun current(): Boolean=session.state.value.let {it.active && it.generation==generation}
    fun requireCurrent() {if(!current()) throw CatalogFailure("access","Форма закрыта вместе с сессией администрирования. Откройте её заново")}
    suspend fun <T> write(block: suspend ()->T): T {
        requireCurrent()
        return withContext(this) {block()}
    }
}

/** A screen may explicitly read/reopen saved business drafts in a new session, never revive old transient input. */
class AdminForms(private val scope: CoroutineScope,private val session: AdminSession=Administration.session) {
    private var generation=session.state.value.generation
    private val commands=mutableMapOf<String,AdminAuthorization>()
    fun current(): Boolean=session.state.value.let {it.active && it.generation==generation}
    fun watch(revoke: ()->Unit) {scope.launch(start=CoroutineStart.UNDISPATCHED) {session.state.collect {if(!current()) revoke()}}}
    fun reopen(revoke: ()->Unit): Boolean {
        if(!current()) revoke()
        val state=session.state.value
        if(!state.active) return false
        generation=state.generation
        return true
    }
    fun permit(): AdminAuthorization {
        if(!current()) throw CatalogFailure("access","Откройте сессию администрирования и заново откройте форму")
        return AdminAuthorization(session,generation)
    }
    /** Unknown-result retries retain their first authority even after logout/login. Outcome reads need no permit. */
    fun command(id: String): AdminAuthorization=commands[id] ?: permit().also {commands[id]=it}
}
