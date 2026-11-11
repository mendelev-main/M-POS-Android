package com.mendelev.mpos.backend

import com.mendelev.mpos.core.access.AdminSession
import com.mendelev.mpos.core.access.Administration

/** Local role gate remains mandatory even when invoked without a Compose screen. */
internal class OnlineOrderDeletion(private val transport: OnlineOrderTransport=OnlineOrderTransport(),
    private val session: AdminSession=Administration.session) {
    suspend fun delete(settings: BackendSettings,id: String,generation: Long,
        before: suspend ()->Unit={},after: suspend ()->Unit={})=session.authorized {
        check(session.state.value.generation==generation) {"Сессия администратора завершена. Откройте заказ заново"}
        before()
        transport.cancelSilently(settings,id)
        after()
    }
}
