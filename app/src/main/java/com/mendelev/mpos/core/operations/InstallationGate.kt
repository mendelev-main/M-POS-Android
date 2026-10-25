package com.mendelev.mpos.core.operations

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide boundary. Room handles durable rollback if Android terminates the process. */
object InstallationGate {
    private val mutex=Mutex()
    private var installing: String?=null
    suspend fun <T> write(block: suspend () -> T): T=mutex.withLock {
        check(installing==null) {"Установка обновления уже начата. Повторите действие после её завершения"}
        block()
    }
    suspend fun <T> prepare(owner: String,block: suspend () -> T): T=mutex.withLock {
        check(installing==null) {"Установка уже запущена"}
        require(owner.isNotBlank())
        block().also {installing=owner}
    }
    suspend fun release(owner: String)=mutex.withLock {if(installing==owner) installing=null}
}
