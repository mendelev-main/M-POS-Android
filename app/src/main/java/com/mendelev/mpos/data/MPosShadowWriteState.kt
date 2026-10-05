package com.mendelev.mpos.data

/** Tracks requested versus committed versions, including requests rejected by the queue. */
class MPosShadowWriteState {
    private var sequence = 0L
    private val requested = mutableMapOf<String, Long>()
    private val committed = mutableMapOf<String, Long>()

    @Synchronized fun request(key: String): Long = (++sequence).also { requested[key] = it }
    @Synchronized fun commit(key: String, version: Long) { committed[key] = version }
    @Synchronized fun caughtUp(keys: Set<String>? = null): Boolean =
        (keys ?: requested.keys).all { requested[it] == committed[it] }
    @Synchronized fun pendingKeys(): Int = requested.keys.count { requested[it] != committed[it] }
}
