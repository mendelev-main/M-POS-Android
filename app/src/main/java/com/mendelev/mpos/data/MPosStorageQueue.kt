package com.mendelev.mpos.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** FIFO shadow work; never suspends the bridge or accumulates unlimited commands. */
class MPosStorageQueue(scope: CoroutineScope, capacity: Int = 64) {
    private data class Command(val run: suspend () -> Unit, val failed: (Exception) -> Unit)
    private val commands = Channel<Command>(capacity)
    private val worker = scope.launch(Dispatchers.IO) {
        for (command in commands) {
            try {
                command.run()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A failed command/callback cannot kill processing of the next command.
                runCatching { command.failed(error) }
            }
        }
    }.also { job -> job.invokeOnCompletion { commands.cancel() } }

    fun submit(failed: (Exception) -> Unit, run: suspend () -> Unit): Boolean =
        worker.isActive && commands.trySend(Command(run, failed)).isSuccess

    fun close() {
        commands.cancel()
        worker.cancel()
    }
}
