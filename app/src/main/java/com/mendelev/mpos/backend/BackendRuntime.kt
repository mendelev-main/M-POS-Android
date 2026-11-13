package com.mendelev.mpos.backend

import android.content.Context
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.recovery.AppDataset
import com.mendelev.mpos.telegram.TelegramKeystoreCipher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

class BackendRuntime private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: BackendRuntime?=null
        fun get(context: Context)=instance ?: synchronized(this) {instance ?: BackendRuntime(context.applicationContext).also {instance=it}}
    }
    private val connectionState=MutableStateFlow<String?>(null);val connection=connectionState.asStateFlow()
    private val operation=Mutex()
    private val projection=Any()
    private val acceptedHere=mutableSetOf<String>()
    private val inboxState=MutableStateFlow<List<JSONObject>>(emptyList());val orders=inboxState.asStateFlow()
    private val recordState=MutableStateFlow<List<OnlineAcceptance>>(emptyList());val accepted=recordState.asStateFlow()
    private val statusState=MutableStateFlow("Backend не настроен");val status=statusState.asStateFlow()
    private val busyState=MutableStateFlow(false);val busy=busyState.asStateFlow()
    private val demandState=MutableStateFlow(false);val overloaded=demandState.asStateFlow()
    private val publication=BackendPublication(context)
    private var lastInventorySignature: String?=null
    private var lastHeartbeat=0L
    private val settings get()=BackendAccess(context,0).store
    private fun usable(s: BackendSettings)=s.url.isNotBlank() && s.datasetId==AppDataset.paths(context).id && AppDataset.runtime.blocking.value==null
    private fun current(expected: BackendSettings): BackendSettings {
        check(expected.url.isNotBlank()) {"Сначала сохраните подключение в настройках backend"}
        check(usable(expected)) {"После восстановления копии сохраните подключение заново"}
        return settings.read().also {check(it==expected) {"Подключение изменилось; действие остановлено"}}
    }
    private fun inbox(s: BackendSettings)=OrderInbox(File(context.noBackupFilesDir,"backend-inbox-${s.target()}-${s.datasetId}.enc"),TelegramKeystoreCipher("mpos.backend.inbox.v1"))
    private fun showInbox(rows: List<JSONObject>)=synchronized(projection) {
        val filtered=rows.filter {"${it.optString("_mposTarget")}:${it.getString("id")}" !in acceptedHere}
        if(inboxState.value.map {it.toString()}!=filtered.map {it.toString()}) inboxState.value=filtered
    }
    /** Called from repeatOnLifecycle(STARTED); leaving the foreground cancels SSE and retries. */
    suspend fun foreground(): Unit=coroutineScope {
        val configurations=MutableStateFlow<BackendSettings?>(null)
        launch(Dispatchers.IO) {while(isActive) {
            try {val s=settings.read();connectionState.value=s.takeIf {usable(it)}?.let {it.target()+":"+it.datasetId};configurations.value=s.takeIf {usable(it) && it.receiveOrders}}
            catch(_:Exception) {configurations.value=null;connectionState.value=null;statusState.value="Не удалось прочитать подключение backend"}
            delay(2000)
        }}
        launch(Dispatchers.IO) {configurations.collectLatest {s->
            inboxState.value=emptyList();recordState.value=emptyList()
            if(s==null) return@collectLatest
            val repo=OnlineOrderRepository(CatalogDatabase.get(context))
            try {
                recordState.value=repo.records().filter {it.target==s.target()}
                val known=inbox(s).read().mapNotNull {repo.record(OnlineOrderCodec.session(s.target(),it.getString("id")))?.orderId}.toSet()
                showInbox(inbox(s).merge(emptyList(),known+recordState.value.map {it.orderId}.toSet()))
            }
            catch(_:Exception) {statusState.value="Не удалось прочитать события. Перезапись остановлена";return@collectLatest}
            var attempt=0
            while(isActive) {
                var retry=0L
                try {
                    current(s)
                    statusState.value="Подключаем канал заказов…"
                    OrderStream().read(s) {event->
                        current(s)
                        event.retryMillis?.let {retry=it}
                        if(event.type=="sleeping") statusState.value="Сайт в режиме сна; подключение повторится по расписанию сервера"
                        if(event.type=="ready") {attempt=0;statusState.value="Канал заказов подключён"}
                        OrderEvents.orders(event)?.let {rows->
                            val records=runBlocking {repo.records().filter {it.target==s.target()}}
                            val known=runBlocking {(rows+inbox(s).read()).mapNotNull {row->repo.record(OnlineOrderCodec.session(s.target(),row.getString("id")))?.orderId}.toSet()}
                            recordState.value=records
                            rows.forEach {it.put("_mposTarget",s.target()).put("_mposDataset",s.datasetId)}
                            synchronized(projection) {showInbox(inbox(s).merge(rows,records.map {it.orderId}.toSet()+known))}
                            attempt=0;statusState.value="Канал заказов подключён"
                        }
                    }
                } catch(e:CancellationException) {throw e}
                catch(e:BackendFailure) {statusState.value=e.message ?: "Нет связи с backend";if(e.status in listOf(401,403)) retry=60_000}
                catch(_:Exception) {statusState.value="Не удалось сохранить или прочитать события. Повтор после восстановления связи"}
                val backoff=minOf(60_000L,1000L shl minOf(attempt++,6))+Random.nextLong(500)
                delay(maxOf(retry,backoff))
            }
        }}
        launch(Dispatchers.IO) {
            var nextAttempt=0L;var revision=-1L;var failures=0
            var nextLoyaltyAttempt=0L;var loyaltyFailures=0;var nextTelegramAttempt=0L
            while(isActive) {
                try {
                    val s=settings.read()
                    if(s.revision!=revision) {revision=s.revision;nextAttempt=0;failures=0;nextLoyaltyAttempt=0;loyaltyFailures=0}
                    if(usable(s) && System.currentTimeMillis()>=nextAttempt) {
                        try {recover(s);failures=0;nextAttempt=System.currentTimeMillis()+5000}
                        catch(e:CancellationException) {throw e}
                        catch(e:Exception) {
                            val wait=if(e is BackendFailure && e.status in listOf(401,403,429)) 60_000L else minOf(60_000L,5000L shl minOf(failures++,4))
                            nextAttempt=System.currentTimeMillis()+wait
                            statusState.value=if(e is BackendFailure) e.message.orEmpty() else "Подтверждения заказов ожидают связи с backend"
                        }
                    }
                    if(usable(s) && System.currentTimeMillis()>=nextTelegramAttempt) {
                        try {com.mendelev.mpos.telegram.TelegramRuntime.get(context).syncOnline(s);nextTelegramAttempt=System.currentTimeMillis()+5000}
                        catch(e:CancellationException) {throw e}
                        catch(_:Exception) {nextTelegramAttempt=System.currentTimeMillis()+30000}
                    }
                    if(usable(s) && System.currentTimeMillis()>=nextLoyaltyAttempt) {
                        try {LoyaltyDelivery.sync(context,s);loyaltyFailures=0;nextLoyaltyAttempt=System.currentTimeMillis()+5000}
                        catch(e:CancellationException) {throw e}
                        catch(_:Exception) {nextLoyaltyAttempt=System.currentTimeMillis()+minOf(60_000L,5000L shl minOf(loyaltyFailures++,4))}
                    }
                } catch(e:CancellationException) {throw e} catch(_:Exception) {statusState.value="Не удалось прочитать подключение backend"}
                delay(2000)
            }
        }
        awaitCancellation()
    }
    suspend fun preview(generation: Long): Pair<BackendSettings,MenuPublication> = withContext(Dispatchers.IO) {
        val access=BackendAccess(context,generation);access.requireSession();val s=access.store.read();current(s)
        s to publication.preview(s)
    }
    suspend fun publish(s: BackendSettings,p: MenuPublication,generation: Long): String=withContext(Dispatchers.IO) {operation.withLock {
        busyState.value=true
        try {publication.publish(s,p,{BackendAccess(context,generation).requireSession();current(s)}) {statusState.value=it}.also {statusState.value=it}}
        catch(e:Exception) {val message="Публикация не завершена. Часть данных могла сохраниться на сервере. "+(if(e is BackendFailure || e is IllegalStateException) e.message else "Проверьте подключение и повторите действие");statusState.value=message;message}
        finally {busyState.value=false}
    }}
    suspend fun setDemand(value: Boolean): String=withContext(Dispatchers.IO) {operation.withLock {
        val s=settings.read();current(s);publication.storeDemand(s,value);demandState.value=value
        try {publication.heartbeat(s);"Загрузка передана на сайт"} catch(_:Exception) {"Загрузка сохранена на планшете; отправка на сайт ожидает связи"}
    }}
    suspend fun refreshInbox(): Unit=withContext(Dispatchers.IO) {operation.withLock {
        busyState.value=true
        try {
            val s=settings.read();current(s)
            check(s.receiveOrders) {"Включите получение онлайн-заказов в настройках"}
            val fresh=OrderDirectory().current(s)
            currentCoroutineContext().ensureActive();current(s)
            val repo=OnlineOrderRepository(CatalogDatabase.get(context))
            val records=repo.records().filter {it.target==s.target()}
            val known=records.map {it.orderId}.toSet()+fresh.mapNotNull {row->repo.record(OnlineOrderCodec.session(s.target(),row.getString("id")))?.orderId}.toSet()
            fresh.forEach {it.put("_mposTarget",s.target()).put("_mposDataset",s.datasetId)}
            synchronized(projection) {
                showInbox(inbox(s).reconcileCurrent(fresh,known))
                recordState.value=records
            }
        } finally {busyState.value=false}
    }}
    internal suspend fun orderHistory(cursor: String?): OrderHistoryPage=withContext(Dispatchers.IO) {
        val s=settings.read();current(s)
        val page=OrderDirectory().history(s,cursor)
        currentCoroutineContext().ensureActive();current(s)
        page.orders.forEach {it.put("_mposTarget",s.target()).put("_mposDataset",s.datasetId)}
        page
    }
    suspend fun accept(order: JSONObject,estimate: String): String=withContext(Dispatchers.IO) {operation.withLock {
        busyState.value=true
        try {
            val s=settings.read();current(s)
            check(s.receiveOrders) {"Включите получение онлайн-заказов в сетевых настройках"}
            check(!inbox(s).isRemoved(order.getString("id"))) {"Заказ уже удалён. Откройте актуальный список событий"}
            check(order.optString("_mposTarget")==s.target() && order.optString("_mposDataset")==s.datasetId) {"Подключение изменилось. Откройте заказ из актуального списка событий"}
            val repo=OnlineOrderRepository(CatalogDatabase.get(context))
            val customerId=order.optString("customer_id").takeIf {Regex("[A-Za-z0-9_-]{1,100}").matches(it) && it!="null"}
            val loyalty=customerId?.let {id->
                try {LoyaltyServices.api(context).profile(id)}
                catch(e:CancellationException) {throw e}
                catch(_:Exception) {com.mendelev.mpos.domain.loyalty.LoyaltySelection(s.loyaltyTarget(),com.mendelev.mpos.domain.loyalty.LoyaltyCustomer(id,order.optString("customer_name").takeUnless {it.isBlank() || it=="null"} ?: "Клиент",order.optString("phone").takeUnless {it=="null"}.orEmpty()),emptyList(),datasetId=s.datasetId)}
            }
            current(s)
            val record=repo.accept(order,s.target(),s.datasetId,estimate,loyalty)
            synchronized(projection) {
                acceptedHere+="${s.target()}:${record.orderId}"
                inboxState.value=inboxState.value.filterNot {it.getString("id")==record.orderId}
            }
            recordState.value=repo.records().filter {it.target==s.target()}
            showInbox(inbox(s).merge(emptyList(),recordState.value.map {it.orderId}.toSet()))
            // The accepted order already exists locally. A network error must not present it as absent.
            val confirmed=runCatching {acknowledge(s,record,repo)}.getOrDefault(false)
            com.mendelev.mpos.print.PrintRuntime.get(context).autoPark(record.sessionId)
            if(confirmed) "Заказ принят и добавлен в Отложенные" else "Заказ сохранён в Отложенных. Подтверждение на сервере ожидает связи"
        } finally {busyState.value=false}
    }}
    suspend fun deleteNewOrder(order: JSONObject,generation: Long)=withContext(Dispatchers.IO) {operation.withLock {
        busyState.value=true
        try {
            val s=settings.read();current(s)
            val id=order.getString("id")
            OnlineOrderDeletion().delete(s,id,generation,before={
                current(s)
                check(order.optString("_mposTarget")==s.target() && order.optString("_mposDataset")==s.datasetId) {"Подключение изменилось. Откройте заказ из актуального списка"}
                check(order.optString("status")=="new") {"Удалить можно только новый заказ из событий"}
                check(OnlineOrderRepository(CatalogDatabase.get(context)).record(OnlineOrderCodec.session(s.target(),id))==null) {"Заказ уже принят на этом планшете"}
            },after={
                current(s)
                val remaining=inbox(s).remove(id)
                synchronized(projection) {acceptedHere+="${s.target()}:$id";inboxState.value=inboxState.value.filterNot {it.getString("id")==id}}
                showInbox(remaining)
            })
        } finally {busyState.value=false}
    }}
    suspend fun retryAcceptance(session: String): String=withContext(Dispatchers.IO) {operation.withLock {
        val s=settings.read();current(s);val repo=OnlineOrderRepository(CatalogDatabase.get(context))
        val record=repo.records().single {it.sessionId==session && it.target==s.target() && it.dataset==s.datasetId}
        check(!record.cancelPending && record.conflict.isEmpty()) {"Проверьте состояние заказа"}
        check(acknowledge(s,record,repo)) {"Подтверждение ожидает связи"}
        "Принятие подтверждено на сервере"
    }}
    suspend fun ready(session: String): String=withContext(Dispatchers.IO) {operation.withLock {
        val s=settings.read();current(s);val repo=OnlineOrderRepository(CatalogDatabase.get(context))
        val record=repo.records().single {it.sessionId==session && it.target==s.target() && it.dataset==s.datasetId}
        repo.markReady(session)
        recordState.value=repo.records().filter {it.target==s.target()}
        val confirmed=runCatching {acknowledge(s,record.copy(ready=true),repo)}.getOrDefault(false)
        if(confirmed) "Готовность подтверждена на сервере" else "Готово на планшете. Подтверждение на сервере ожидает связи"
    }}
    suspend fun cancel(session: String): String=withContext(Dispatchers.IO) {operation.withLock {
        val s=settings.read();current(s);val repo=OnlineOrderRepository(CatalogDatabase.get(context))
        val r=repo.records().single {it.sessionId==session && it.target==s.target() && it.dataset==s.datasetId}
        repo.cancel(session)
        val done=runCatching {acknowledge(s,r.copy(cancelPending=true),repo)}.getOrDefault(false)
        recordState.value=repo.records().filter {it.target==s.target()}
        if(done) "Заказ отменён на сайте и на планшете" else "Отмена сохранена. Подтверждение на сайте ожидает связи; оплата заблокирована"
    }}
    private suspend fun acknowledge(s: BackendSettings,r: OnlineAcceptance,repo: OnlineOrderRepository): Boolean {
        if(r.dataset!=s.datasetId || r.target!=s.target() || !repo.localExists(r)) return false
        val http=BackendHttp()
        if(r.cancelPending) {
            current(s)
            val result=http.json(s,s.ordersPath+"/${r.orderId}/status","PATCH",JSONObject().put("status","cancelled"))
            check(result.opt("ok")==true && result.optJSONObject("order")?.optString("id")==r.orderId && result.getJSONObject("order").optString("status")=="cancelled")
            repo.finishCancel(r.sessionId)
            lastInventorySignature=null
            runCatching {publication.availability(current(s),listOf(r.orderId))}
            return true
        }
        if(!r.accepted) {
            current(s)
            val result=try {OnlineOrderTransport(http).accept(s,r.orderId,r.estimate)} catch(e:BackendFailure) {
                if(e.status==409 && e.orderId==r.orderId && e.orderStatus in setOf("ready","preparing")) {
                    repo.update(r.sessionId) {it.copy(accepted=true,ready=e.orderStatus=="ready",readyConfirmed=e.orderStatus=="ready")}
                    return true
                }
                if(e.status==409 && e.orderId==r.orderId && e.orderStatus=="cancelled") repo.update(r.sessionId) {it.copy(conflict="cancelled")}
                throw e
            }
            check(result.opt("ok")==true && result.optJSONObject("order")?.optString("id")==r.orderId && result.getJSONObject("order").optString("status")=="accepted") {"Backend не подтвердил принятие"}
            repo.update(r.sessionId) {it.copy(accepted=true)}
        }
        if(r.ready && !r.readyConfirmed) {
            current(s)
            OnlineOrderTransport(http).ready(s,r.orderId)
            repo.update(r.sessionId) {it.copy(readyConfirmed=true)}
        }
        recordState.value=repo.records().filter {it.target==s.target()}
        return true
    }
    private suspend fun recover(s: BackendSettings)=operation.withLock {
        val db=CatalogDatabase.get(context);val repo=OnlineOrderRepository(db)
        val records=repo.records().filter {it.target==s.target() && it.dataset==s.datasetId}
        records.filter {!it.cancelled && (it.cancelPending || it.conflict.isEmpty() && (!it.accepted || it.ready && !it.readyConfirmed))}.forEach {r->
            try {acknowledge(s,r,repo)} catch(e:CancellationException) {throw e}
            catch(e:BackendFailure) {if(e.status !in listOf(404,409)) throw e;statusState.value="Проверьте подтверждение заказа ${r.externalId}: HTTP ${e.status}"}
        }
        val paid=records.filter {!it.settled && db.sales().session(it.sessionId)!=null}
        paid.chunked(100).forEach {batch->
            // A complete new availability snapshot is necessary before releasing website reservations.
            publication.availability(current(s),batch.map {it.orderId})
            batch.forEach {r->repo.update(r.sessionId) {it.copy(settled=true)}}
        }
        recordState.value=repo.records().filter {it.target==s.target()}
        if(s.receiveOrders) {
            demandState.value=publication.demand(s)
            val signature=s.target()+":"+s.revision+":"+db.catalog().allProducts().joinToString {"${it.id}:${it.revision}"}+db.catalog().allBalances().joinToString {"${it.productId}:${it.revision}"}
            if(signature!=lastInventorySignature) {publication.availability(current(s),emptyList());lastInventorySignature=signature}
            if(System.currentTimeMillis()-lastHeartbeat>=15_000) {publication.heartbeat(current(s));lastHeartbeat=System.currentTimeMillis()}
        }
    }
}
