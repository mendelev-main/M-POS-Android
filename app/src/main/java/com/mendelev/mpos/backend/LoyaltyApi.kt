package com.mendelev.mpos.backend

import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.domain.loyalty.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal class LoyaltyApi(private val settings: ()->BackendSettings,private val http: BackendHttp=BackendHttp()) {
    private fun id(value: String)=value.also {require(Regex("[A-Za-z0-9_-]{1,100}").matches(it)) {"Некорректный идентификатор"}}
    private suspend fun call(path: (BackendSettings)->String,method: String="GET",body: JSONObject?=null,target: String?=null): Pair<BackendSettings,JSONObject> = withContext(Dispatchers.IO) {
        val s=settings().validated()
        check(target==null || s.loyaltyTarget()==target) {"Подключение клиента изменилось. Выберите клиента заново"}
        val result=http.json(s.copy(timeoutSeconds=5),path(s),method,body)
        check(settings()==s) {"Подключение изменилось. Откройте раздел заново"}
        s to result
    }
    suspend fun search(query: String): List<LoyaltyCustomer> {
        val term=query.trim();if(term.length<2) return emptyList()
        require(term.length<=100)
        val (_,result)=call({it.customersPath+"/search?q="+URLEncoder.encode(term,"UTF-8")})
        val rows=result.getJSONArray("customers");require(rows.length()<=100)
        return List(rows.length()) {customer(rows.getJSONObject(it))}
    }
    suspend fun scan(raw: String): LoyaltySelection {
        val original=settings().validated()
        val phone=CustomerEntryRules.qrPhone(raw)
        val customer=CustomerEntryRules.qrMatch(search(phone),phone)
        check(settings().validated()==original) {"Подключение изменилось. Отсканируйте QR заново"}
        val result=profile(customer.id)
        check(result.target==original.loyaltyTarget() && result.datasetId==original.datasetId &&
            result.customer.phone.filter {it in '0'..'9'}==phone) {"Данные клиента изменились. Повторите поиск."}
        return result
    }
    suspend fun create(name: String,phone: String): LoyaltyCustomer {
        val (n,p)=CustomerEntryRules.registration(name,phone)
        return try {customer(call({it.customersPath},"POST",JSONObject().put("name",n).put("phone",p)).second.getJSONObject("customer"))}
        catch(e:BackendFailure) {
            if(e.status==409) throw BackendFailure("Клиент с таким телефоном уже существует. Вернитесь к поиску.",409,code="CUSTOMER_EXISTS")
            throw e
        }
    }
    suspend fun select(customer: LoyaltyCustomer): LoyaltySelection {
        val original=settings().validated()
        return try {profile(customer.id)} catch(e:BackendFailure) {
            check(settings().validated()==original) {"Подключение изменилось. Найдите клиента заново"}
            LoyaltyRules.valid(LoyaltySelection(original.loyaltyTarget(),customer.copy(active=false,consentVersion=null),emptyList(),datasetId=original.datasetId))
        }
    }
    suspend fun profile(customerId: String): LoyaltySelection=profileDetails(customerId).first
    suspend fun profileDetails(customerId: String): Pair<LoyaltySelection,JSONObject> {
        val (s,data)=call({it.customersPath+"/${id(customerId)}/loyalty"})
        val membership=data.optJSONObject("membership")
        val customer=customer(data.getJSONObject("customer")).copy(active=membership?.optBoolean("active")==true,
            consentVersion=membership?.opt("version")?.toString()?.toLongOrNull())
        return LoyaltyRules.valid(LoyaltySelection(s.loyaltyTarget(),customer,if(customer.active) programs(data.getJSONArray("programs")) else emptyList(),datasetId=s.datasetId)) to data
    }
    suspend fun terms(): JSONObject=call({it.loyaltyTermsPath}).second.also {
        require(it.getString("version").isNotBlank() && it.getJSONArray("documents").length() in 1..10)
    }
    suspend fun consent(profile: LoyaltySelection,termsVersion: String,staffId: String,staffName: String,requestId: String): LoyaltySelection {
        require(staffId.isNotBlank() && staffName.isNotBlank()) {"Для согласия откройте смену и выберите сотрудника"}
        call({it.customersPath+"/${id(profile.customer.id)}/loyalty/consent"},"POST",JSONObject()
            .put("documentVersion",termsVersion).put("expectedVersion",profile.customer.consentVersion ?: JSONObject.NULL)
            .put("acknowledged",true).put("phoneChecked",true).put("requestId",requestId)
            .put("staff",JSONObject().put("id",staffId).put("name",staffName)),profile.target)
        return this.profile(profile.customer.id)
    }
    suspend fun programs(): List<LoyaltyProgram> = programs(call({it.loyaltyPath+"/programs"}).second.getJSONArray("programs"))
    suspend fun saveProgram(program: LoyaltyProgram,generation: Long) = admin(generation) {
        require(program.name.trim().length in 1..200 && program.required in 1..999999 && program.earning.isNotEmpty() && program.reward.isNotEmpty()) {"Укажите название, количество и товары обеих групп"}
        val body=JSONObject().put("name",program.name.trim()).put("requiredQuantity",program.required).put("rewardQuantity",1)
            .put("earningProductIds",JSONArray(program.earning)).put("rewardProductIds",JSONArray(program.reward)).put("isActive",program.active)
        call({it.loyaltyPath+"/programs"+if(program.id.isBlank()) "" else "/${id(program.id)}"},if(program.id.isBlank()) "POST" else "PUT",body)
        Unit
    }
    suspend fun active(program: LoyaltyProgram,value: Boolean,generation: Long)=admin(generation) {
        call({it.loyaltyPath+"/programs/${id(program.id)}/active"},"PATCH",JSONObject().put("isActive",value));Unit
    }
    suspend fun delete(program: LoyaltyProgram,generation: Long)=admin(generation) {
        call({it.loyaltyPath+"/programs/${id(program.id)}"},"DELETE");Unit
    }
    suspend fun ledger(customerId: String): JSONArray=call({it.customersPath+"/${id(customerId)}/ledger"}).second.getJSONArray("ledger")
    suspend fun orders(customerId: String): JSONArray=call({it.customersPath+"/${id(customerId)}/orders"}).second.getJSONArray("orders")
    suspend fun adjust(profile: LoyaltySelection,program: LoyaltyProgram,targetProgress: Int,reason: String,requestId: String,generation: Long,webPassword: CharArray?=null) = withContext(Dispatchers.IO) {
        require(targetProgress in 0..program.required && reason.trim().length in 1..500) {"Укажите результат и причину корректировки"}
        require(profile.customer.active && profile.customer.consentVersion!=null) {"Обновите участие клиента перед корректировкой"}
        require(targetProgress.toBigDecimal().compareTo(program.progress.toBigDecimal())!=0 || program.rewards>0) {"Накопления не изменены"}
        require(program.rewards==0 || targetProgress==0) {"Сначала снимите доступный подарок"}
        require(Regex("[-\\w]{8,100}").matches(requestId)) {"Некорректный идентификатор корректировки"}
        Administration.session.withBackendCredential(generation) {password->
            call({it.customersPath+"/${id(profile.customer.id)}/loyalty-adjustment"},"POST",JSONObject()
                .put("programId",program.id).put("requestId",requestId).put("reason",reason.trim())
                .put("consentVersion",profile.customer.consentVersion)
                .put("expectedBalance",JSONObject().put("progress",program.progress.toBigDecimal()).put("rewards",program.rewards))
                .put("targetBalance",JSONObject().put("progress",if(targetProgress==program.required) 0 else targetProgress).put("rewards",if(targetProgress==program.required) 1 else 0))
                .put("adminEmployeeId","native-administrator").put("adminEmployeeName","Администратор M-POS")
                .put("adminCredentialVersion","android-admin-2026-11").put("adminPassword",(webPassword ?: password).concatToString()),profile.target)
            Unit
        }
    }
    private suspend fun <T> admin(generation: Long,block: suspend ()->T): T=Administration.session.authorized {
        check(Administration.session.state.value.generation==generation) {"Сессия этой формы завершена"};block()
    }
    companion object {
        fun customer(row: JSONObject)=LoyaltyCustomer(row.getString("id"),row.getString("name"),row.optString("normalized_phone",row.optString("phone")),row.optString("telegram_username").takeUnless {it=="null"}.orEmpty())
        fun programs(rows: JSONArray): List<LoyaltyProgram> {
            require(rows.length()<=100)
            fun ids(row: JSONObject,key: String): List<String> {val a=row.optJSONArray(key) ?: JSONArray();require(a.length()<=10000);return List(a.length()) {a.getJSONObject(it).getString("product_id")}.distinct()}
            return List(rows.length()) {i->val p=rows.getJSONObject(i)
                LoyaltyProgram(p.getString("id"),p.getString("name"),p.getInt("required_quantity"),ids(p,"loyalty_earning_products"),ids(p,"loyalty_reward_products"),p.optBoolean("is_active",true),p.opt("progress")?.toString()?.takeUnless {it=="null"} ?: "0",p.optInt("rewards"))
            }
        }
    }
}
