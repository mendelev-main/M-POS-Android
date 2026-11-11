package com.mendelev.mpos.core.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread feedback, independent of persistent validation errors and the screen lifecycle. */
class TransientMessages(private val scope: CoroutineScope) {
    companion object {const val DURATION_MS=2500L}
    private val mutable=MutableStateFlow<String?>(null)
    val state=mutable.asStateFlow()
    private var expiry: Job?=null
    fun show(message: String) {
        expiry?.cancel()
        mutable.value=message
        expiry=scope.launch {delay(DURATION_MS);mutable.value=null}
    }
    fun clear() {expiry?.cancel();expiry=null;mutable.value=null}
}
