package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.access.Administration

/** Authorization spans the whole transaction, including its durable commit. Never call inside another Room transaction. */
internal suspend fun <T> CatalogDatabase.adminTransaction(block: suspend ()->T): T =
    Administration.session.authorized(kotlinx.coroutines.currentCoroutineContext()[com.mendelev.mpos.core.access.AdminAuthorization]) { withTransaction {block()} }


/** Pending-order edit is operational; cancellation remains an administrative action. */
internal suspend fun <T> CatalogDatabase.purchaseOrderTransaction(administrative: Boolean,block: suspend ()->T): T =
    if(administrative) adminTransaction(block) else withTransaction {block()}


/** Catalog dictionaries have operational save/edit actions; destructive and discount actions remain admin-only. */
internal suspend fun <T> CatalogDatabase.dictionaryTransaction(administrative: Boolean,block: suspend ()->T): T =
    if(administrative) adminTransaction(block) else withTransaction {block()}
