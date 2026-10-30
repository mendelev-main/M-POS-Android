package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.access.Administration

/** Authorization spans the whole transaction, including its durable commit. Never call inside another Room transaction. */
internal suspend fun <T> CatalogDatabase.adminTransaction(block: suspend ()->T): T =
    Administration.session.authorized(kotlinx.coroutines.currentCoroutineContext()[com.mendelev.mpos.core.access.AdminAuthorization]) { withTransaction {block()} }
