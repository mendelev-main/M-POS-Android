package com.mendelev.mpos.core.operations

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide boundary. Room handles durable rollback if Android terminates the process. */
object InstallationGate {
    private val mutex=Mutex()
    private var installing=false
    suspend fun <T> write(block: suspend () -> T): T=mutex.withLock {
        check(!installing) {"Установка обновления уже начата. Повторите действие после её завершения"}
        block()
    }
    suspend fun <T> prepare(block: suspend () -> T): T=mutex.withLock {
        check(!installing) {"Установка уже запущена"}
        block().also {installing=true}
    }
    suspend fun release()=mutex.withLock {installing=false}
}
