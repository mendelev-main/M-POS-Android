package com.mendelev.mpos.feature.loyalty

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mendelev.mpos.backend.LoyaltyServices
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.workspace.CurrentCart
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

@Composable internal fun CustomerWindow(cart: CurrentCart,busy: Boolean,externalError: String?,onClose: ()->Unit,persist: (suspend (LoyaltySelection)->Unit)?=null,client: com.mendelev.mpos.backend.LoyaltyApi?=null,onSave: (LoyaltySelection?)->Unit) {
    val context=LocalContext.current;val api=remember(context,client) {client ?: LoyaltyServices.api(context)};val scope=rememberCoroutineScope()
    var profile by remember(cart.sessionId) {mutableStateOf(cart.loyalty)}
    var mode by remember(cart.sessionId) {mutableStateOf(if(cart.loyalty==null) "search" else "profile")}
    var query by remember {mutableStateOf("")};var rows by remember {mutableStateOf(emptyList<LoyaltyCustomer>())}
    var loading by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
    var name by remember {mutableStateOf("")};var phone by remember {mutableStateOf("")}
    var terms by remember {mutableStateOf<JSONObject?>(null)};var consent by remember {mutableStateOf(false)}
    var consentRequest by remember {mutableStateOf(UUID.randomUUID().toString())}
    var expandedDocument by remember {mutableStateOf("")}
    var writing by remember {mutableStateOf(false)}
    var consentStaff by remember {mutableStateOf<Pair<String,String>?>(null)}
    var enrollAfterCreate by remember {mutableStateOf(false)}
    var createdCustomer by remember {mutableStateOf<LoyaltyCustomer?>(null)}
    var searching by remember {mutableStateOf(false)}
    var searchError by remember {mutableStateOf<String?>(null)}
    var searchSequence by remember {mutableIntStateOf(0)}
    val saveLatest by rememberUpdatedState(onSave)
    val persistLatest by rememberUpdatedState(persist)
    val locked=busy || loading || writing
    suspend fun load(id: String) {
        val previous=profile
        val fresh=api.profile(id)
        profile=if(previous?.target==fresh.target && previous.customer.id==id && fresh.customer.active) fresh.copy(selected=previous.selected.filter {key->fresh.programs.any {it.id==key && it.rewards>0}}.toSet()) else fresh
    }
    fun action(block: suspend ()->Unit) {
        if(busy || loading || writing) return
        loading=true;error=null
        scope.launch {try {block()} catch(e:CancellationException) {throw e} catch(e:Exception) {error=e.message ?: "Нет ответа от сервера"} finally {loading=false}}
    }
    LaunchedEffect(cart.sessionId) {cart.loyalty?.let {loading=true;try {load(it.customer.id)} catch(e:CancellationException) {throw e} catch(e:Exception) {error="Не удалось обновить профиль. Подарки недоступны до повторной проверки"} finally {loading=false}}}
    suspend fun prepareConsent() {
        val p=profile ?: return
        val db=CatalogDatabase.get(context)
        val shift=ShiftRepository(db).snapshot().active ?: error("Для фиксации согласия выберите сотрудника открытой смены")
        val employee=EmployeeRepository(db).snapshot().employees.firstOrNull {it.id==shift.actorId} ?: error("Выберите сотрудника открытой смены")
        consentStaff=employee.id to employee.name
        terms=api.terms();load(p.customer.id);consent=false;consentRequest=UUID.randomUUID().toString();mode="consent"
    }
    LaunchedEffect(query,mode) {
        val sequence=++searchSequence
        rows=emptyList();searchError=null;searching=false
        if(mode!="search" || query.length!=4) return@LaunchedEffect
        searching=true
        try {
            delay(250)
            val result=CustomerEntryRules.matching(api.search(query),query)
            if(sequence==searchSequence) rows=result
        } catch(e:CancellationException) {throw e}
        catch(e:Exception) {if(sequence==searchSequence) searchError=e.message ?: "Не удалось найти клиента"}
        finally {if(sequence==searchSequence) searching=false}
    }
    if(mode=="scan") {
        CustomerQrScannerWindow(onClose={mode="search"},onResult={raw->
            mode="search"
            action {
                val next=api.scan(raw)
                persistLatest?.invoke(next)
                profile=next;mode="profile"
            }
        })
        return
    }
    if(mode=="search") {
        CustomerPickerWindow(query,rows,searching || loading,locked,searchError ?: error ?: externalError,
            onQuery={query=CustomerEntryRules.suffix(it);error=null},
            onSelect={customer->action {
                val next=api.select(customer)
                persistLatest?.invoke(next)
                profile=next;mode="profile"
            }},
            onCreate={mode="create";createdCustomer=null;name="";phone="";enrollAfterCreate=false;error=null},onClose=onClose,onScan={error=null;query="";mode="scan"})
        return
    }
    if(mode=="create") {
        CustomerCreateWindow(name,phone,enrollAfterCreate,locked,error ?: externalError,identityLocked=createdCustomer!=null,
            onName={name=it;error=null},onPhone={phone=CustomerEntryRules.national(it);error=null},onEnroll={enrollAfterCreate=it},
            onBack={mode="search";query="";error=null},onClose=onClose,onCreate={action {
                val customer=createdCustomer ?: api.create(name,phone).also {createdCustomer=it}
                profile=api.select(customer)
                if(!enrollAfterCreate) {persistLatest?.invoke(profile!!);mode="profile"}
                else {
                    persistLatest?.invoke(profile!!)
                    mode="profile"
                    prepareConsent()
                }
            }})
        return
    }
    if(mode=="profile" && profile!=null) {
        val p=profile!!
        CustomerProfileWindow(p,cart,loading,busy || writing,error ?: externalError,onClose,
            onChange={mode="search";query="";error=null},
            onDone={onSave(profile)},onRemove={onSave(null)},
            onRetry={action {load(p.customer.id)}},
            onEnroll={action {prepareConsent()}},
            onGift={id,checked->
                val next=p.copy(selected=if(checked) p.selected+id else p.selected-id,verified="")
                profile=next
                if(persistLatest!=null) {
                    writing=true;error=null
                    scope.launch {
                        try {persistLatest?.invoke(next)}
                        catch(e:CancellationException) {throw e}
                        catch(e:Exception) {profile=p;error=e.message ?: "Не удалось сохранить выбор подарка"}
                        finally {writing=false}
                    }
                }
            },
            onTelegram={
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://t.me/"+p.customer.username.removePrefix("@"))))
            })
        return
    }
    MposDialog(when(mode) {"create"->"Новый клиент";"consent"->"Участие в лояльности на кассе";else->"Клиент заказа"},onClose,closable=!locked,maxWidth=460.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("loyaltyCustomerWindow"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: externalError)?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
            when(mode) {
                "consent"->{
                    val p=profile
                    if(p!=null) Text("${p.customer.name} · ${p.customer.phone}",fontWeight=FontWeight.Bold)
                    Text("Предоставьте покупателю условия, сверьте его телефон и получите добровольное согласие. Отказ не мешает покупке. Telegram не требуется.")
                    val documents=terms?.optJSONArray("documents")
                    if(documents!=null) repeat(documents.length()) {i->val d=documents.getJSONObject(i);val slug=d.getString("slug")
                        MposButton(d.getString("title"),{expandedDocument=if(expandedDocument==slug) "" else slug},Modifier.fillMaxWidth(),style=ActionStyle.Secondary)
                        if(expandedDocument==slug) Text(d.getString("text"),fontSize=13.sp,lineHeight=20.sp)
                    }
                    Text("Редакция ${terms?.optString("version").orEmpty()} · сотрудник ${consentStaff?.second.orEmpty()}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(consent,{consent=it},enabled=!locked,modifier=Modifier.testTag("loyaltyConsentCheck"))
                        Text("Покупатель ознакомлен с условиями и добровольно дал согласие на участие и обработку данных для лояльности. Телефон сверен лично.",fontSize=13.sp)
                    }
                }

            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            when(mode) {
                "consent"->{
                    MposButton("Отмена",{if(createdCustomer!=null && persistLatest==null) saveLatest(profile) else onClose()},Modifier.weight(1f),enabled=!locked,style=ActionStyle.Secondary)
                    MposButton("Подключить к лояльности",{action {
                        val db=CatalogDatabase.get(context);val shift=ShiftRepository(db).snapshot().active ?: error("Для согласия откройте смену")
                        val employee=EmployeeRepository(db).snapshot().employees.firstOrNull {it.id==shift.actorId} ?: error("Выберите сотрудника открытой смены")
                        check(consentStaff?.first==employee.id) {"Сотрудник смены изменился. Откройте согласие заново"}
                        profile=api.consent(profile!!,terms!!.getString("version"),employee.id,employee.name,consentRequest);persistLatest?.invoke(profile!!);mode="profile"
                    }},Modifier.weight(1f).testTag("loyaltyConsentSave"),enabled=!locked && consent && terms!=null)
                }
                else->MposButton("Закрыть",onClose,Modifier.fillMaxWidth(),enabled=!locked,style=ActionStyle.Secondary)
            }
        }
    }
}
