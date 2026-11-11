package com.mendelev.mpos.backend

import android.content.Context
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Durable chronological replay. A retry always uses the original receipt/customer/consent/time. */
internal object LoyaltyDelivery {
    private val mutex=Mutex()
    suspend fun sync(context: Context,expected: BackendSettings)=mutex.withLock {
        fun current() {check(LoyaltyServices.settings(context)==expected) {"Подключение лояльности изменилось"}}
        current()
        val outbox=LoyaltyOutbox(CatalogDatabase.get(context));val http=BackendHttp()
        deliver(outbox,expected,http,::current)
    }
    internal suspend fun deliver(outbox: LoyaltyOutbox,expected: BackendSettings,http: BackendHttp,current: ()->Unit) {
        for(row in outbox.pending(expected.loyaltyTarget(),expected.datasetId,10)) {
            if(row.target!=expected.loyaltyTarget() || row.dataset!=expected.datasetId) continue
            try {
                if(!row.saleDone) {
                    current()
                    val data=http.json(expected.copy(timeoutSeconds=5),expected.loyaltyPath+"/sales","POST",JSONObject(row.body))
                    check(data.optBoolean("ok")) {"Сервер не подтвердил начисление"}
                    current()
                    val summary=JSONObject().put("skipped",data.optString("skipped")).put("events",data.optJSONArray("events") ?: JSONArray())
                    val programs=data.optJSONArray("programs") ?: JSONArray()
                    summary.put("programs",JSONArray(List(programs.length()) {i->val p=programs.getJSONObject(i)
                        JSONObject().put("id",p.getString("id")).put("name",p.getString("name")).put("required_quantity",p.getInt("required_quantity")).put("progress",p.get("progress")).put("rewards",p.getInt("rewards"))
                    }))
                    outbox.update(row.receiptId) {latest->current();check(latest.target==expected.loyaltyTarget() && latest.dataset==expected.datasetId);latest.copy(saleDone=true,blocked=if(data.optString("skipped")=="consent_required") "consent_required" else "",result=summary.toString())}
                }
                val latest=outbox.read(row.receiptId) ?: continue
                if(latest.reverseRequested && !latest.reversalDone && latest.blocked.isEmpty()) {
                    current()
                    val body=JSONObject(latest.body)
                    val result=http.json(expected.copy(timeoutSeconds=5),expected.loyaltyPath+"/reversal","POST",JSONObject().put("orderId",latest.receiptId).put("customerId",body.getString("customerId")))
                    check(result.optBoolean("ok")) {"Сервер не подтвердил возврат лояльности"}
                    current();outbox.update(latest.receiptId) {current();it.copy(reversalDone=true)}
                }
            } catch(e:CancellationException) {throw e}
            catch(e:BackendFailure) {
                if(e.status in listOf(400,409)) {
                    current();outbox.update(row.receiptId) {it.copy(blocked="http_${e.status}")}
                } else throw e
            }
        }
    }
}
