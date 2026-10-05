package com.mendelev.mpos.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MPosStorageQueueTest {
    @Test fun fifoDoesNotRunReadBeforeSuspendedWriteCompletes() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val queue = MPosStorageQueue(scope)
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val read = CompletableDeferred<Int>()
            var value = 0
            assertTrue(queue.submit({ read.completeExceptionally(it) }) { started.complete(Unit); release.await(); value = 1 })
            withTimeout(5_000) { started.await() }
            assertTrue(queue.submit({ read.completeExceptionally(it) }) { value = 2 })
            assertTrue(queue.submit({ read.completeExceptionally(it) }) { read.complete(value) })
            assertFalse(read.isCompleted)
            release.complete(Unit)
            assertEquals(2, withTimeout(5_000) { read.await() })
        } finally { queue.close(); scope.cancel() }
    }

    @Test fun fullQueueRejectsImmediatelyAndWorkerSurvivesFailedCommand() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val queue = MPosStorageQueue(scope, capacity = 1)
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val failure = CompletableDeferred<Unit>()
            val complete = CompletableDeferred<Unit>()
            assertTrue(queue.submit({ failure.completeExceptionally(it) }) { started.complete(Unit); release.await() })
            withTimeout(5_000) { started.await() }
            assertTrue(queue.submit({ failure.complete(Unit) }) { error("synthetic failure") })
            assertFalse(queue.submit({}) { error("rejected command must not execute") })
            release.complete(Unit)
            withTimeout(5_000) { failure.await() }
            assertTrue(queue.submit({ complete.completeExceptionally(it) }) { complete.complete(Unit) })
            withTimeout(5_000) { complete.await() }
        } finally { queue.close(); scope.cancel() }
    }

    @Test fun closingQueueCancelsActiveAndDiscardedWork() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val queue = MPosStorageQueue(scope)
        try {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val queued = CompletableDeferred<Unit>()
            queue.submit({}) { try { started.complete(Unit); CompletableDeferred<Unit>().await() } finally { cancelled.complete(Unit) } }
            withTimeout(5_000) { started.await() }
            queue.submit({}) { queued.complete(Unit) }
            queue.close()
            withTimeout(5_000) { cancelled.await() }
            assertFalse(queued.isCompleted)
            assertFalse(queue.submit({}) { queued.complete(Unit) })
        } finally { queue.close(); scope.cancel() }
    }

    @Test fun olderCompletionCannotHideNewerFailedOrRejectedWrite() {
        val state = MPosShadowWriteState()
        val first = state.request("products")
        val rejected = state.request("products")
        state.commit("products", first)
        assertFalse(state.caughtUp(setOf("products")))
        assertEquals(1, state.pendingKeys())
        assertTrue(state.caughtUp(setOf("employees")))
        val retry = state.request("products")
        assertTrue(retry > rejected)
        state.commit("products", retry)
        assertTrue(state.caughtUp())
        assertEquals(0, state.pendingKeys())
    }
}
