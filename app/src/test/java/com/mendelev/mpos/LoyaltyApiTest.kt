package com.mendelev.mpos

import com.mendelev.mpos.backend.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LoyaltyApiTest {
    private val settings=BackendSettings(url="https://fixture.example.org/pos",deviceKey="fixture-key",datasetId="dataset")
    private val profile="""{"customer":{"id":"customer","name":"Тест","normalized_phone":"375290000000"},"membership":{"active":true,"version":4},"programs":[{"id":"program","name":"Кофе","required_quantity":5,"is_active":true,"progress":2,"rewards":1,"loyalty_earning_products":[{"product_id":"p"}],"loyalty_reward_products":[{"product_id":"p"}]}]}"""
    private fun api(settingsProvider: ()->BackendSettings={settings},handler: (Request)->Pair<Int,String>): LoyaltyApi {
        val http=BackendHttp(OkHttpClient.Builder().addInterceptor {chain->
            val request=chain.request();assertEquals("fixture-key",request.header("X-Device-Key"))
            val (code,body)=handler(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build())
        return LoyaltyApi(settingsProvider,http)
    }
    @Test fun qrFetchesExactCustomerAndFreshProfileWithoutCreatingOne()=runBlocking {
        val calls=mutableListOf<String>()
        val service=api {r->
            assertEquals("GET",r.method);calls+=r.url.encodedPath
            if(r.url.encodedPath.endsWith("/search")) {
                assertEquals("375290000000",r.url.queryParameter("q"))
                200 to """{"customers":[{"id":"wrong","name":"Другой","normalized_phone":"375330000000"},{"id":"customer","name":"Тест","normalized_phone":"375290000000"}]}"""
            } else 200 to profile
        }
        val result=service.scan("+375290000000")
        assertEquals("customer",result.customer.id);assertTrue(result.customer.active)
        assertEquals(2,calls.size);assertTrue(calls.last().endsWith("/customer/loyalty"))
    }
    @Test fun qrDoesNotSelectAnUnverifiedFallbackProfile()=runBlocking {
        val service=api {r->if(r.url.encodedPath.endsWith("/search")) 200 to """{"customers":[{"id":"customer","name":"Тест","normalized_phone":"375290000000"}]}""" else 503 to "{}"}
        try {service.scan("375290000000");fail()} catch(_:BackendFailure) {}
    }
    @Test fun profileMapsWebIdsConsentAndBalanceWithoutInventingMembership()=runBlocking {
        val fresh=api {request->assertEquals("/pos/api/customers/customer/loyalty",request.url.encodedPath);200 to profile}.profile("customer")
        assertEquals(4L,fresh.customer.consentVersion);assertEquals("dataset",fresh.datasetId)
        assertEquals(listOf("p"),fresh.programs.single().earning);assertEquals(1,fresh.programs.single().rewards)
        val inactive=api {200 to profile.replace("\"active\":true","\"active\":false")}.profile("customer")
        assertFalse(inactive.customer.active);assertTrue(inactive.programs.isEmpty())
    }
    @Test fun consentPostsExplicitVersionStaffAndStableRequestThenReloadsProfile()=runBlocking {
        val calls=mutableListOf<String>()
        val service=api {r->calls+=r.method
            if(r.method=="POST") {
                assertEquals("/pos/api/customers/customer/loyalty/consent",r.url.encodedPath)
                val buffer=Buffer();r.body!!.writeTo(buffer);val body=JSONObject(buffer.readUtf8())
                assertEquals("terms-v2",body.getString("documentVersion"));assertEquals(4L,body.getLong("expectedVersion"))
                assertEquals("request",body.getString("requestId"));assertTrue(body.getBoolean("phoneChecked"));assertTrue(body.getBoolean("acknowledged"))
                assertEquals("staff",body.getJSONObject("staff").getString("id"));200 to "{\"ok\":true}"
            } else 200 to profile
        }
        val p=service.profile("customer");service.consent(p,"terms-v2","staff","Кассир","request")
        assertEquals(listOf("GET","POST","GET"),calls)
    }
    @Test fun failedAndChangedEndpointNeverBecomeZeroRewardProfiles()=runBlocking {
        try {api {503 to "{}"}.profile("customer");fail()} catch(e:BackendFailure) {assertEquals(503,e.status)}
        var current=settings
        try {api({current}) {current=settings.copy(deviceKey="another");200 to profile}.profile("customer");fail()} catch(_:IllegalStateException) {}
    }

    @Test fun adjustmentUsesWebConsentCasAndExplicitAdministrativeProof()=runBlocking {
        val previous=com.mendelev.mpos.core.access.Administration.session
        val session=com.mendelev.mpos.core.access.AdminSession(com.mendelev.mpos.core.access.PasswordVerifier {true})
        com.mendelev.mpos.core.access.Administration.session=session
        try {
            session.login("local-fixture".toCharArray())
            val targets=mutableListOf<Pair<Int,Int>>()
            val service=api {r->
                if(r.method=="GET") 200 to profile else {
                    val sink=Buffer();r.body!!.writeTo(sink);val body=JSONObject(sink.readUtf8())
                    assertEquals(4L,body.getLong("consentVersion"))
                    assertEquals(2,body.getJSONObject("expectedBalance").getInt("progress"))
                    assertEquals(if(targets.isEmpty()) 1 else 0,body.getJSONObject("expectedBalance").getInt("rewards"))
                    assertEquals("web-fixture",body.getString("adminPassword"))
                    assertEquals("android-admin-2026-11",body.getString("adminCredentialVersion"))
                    assertEquals(if(targets.isEmpty()) "stable-request" else "grant-request",body.getString("requestId"))
                    val target=body.getJSONObject("targetBalance");targets+=target.getInt("progress") to target.getInt("rewards")
                    200 to "{\"ok\":true}"
                }
            }
            val p=service.profile("customer")
            service.adjust(p,p.programs.single(),0,"Снять подарок","stable-request",session.state.value.generation,"web-fixture".toCharArray())
            service.adjust(p,p.programs.single().copy(rewards=0),5,"Начислить подарок","grant-request",session.state.value.generation,"web-fixture".toCharArray())
            assertEquals(listOf(0 to 0,0 to 1),targets)
            session.logout()
            try {service.adjust(p,p.programs.single(),0,"Причина","stable-request",session.state.value.generation);fail()} catch(_:com.mendelev.mpos.domain.catalog.CatalogFailure) {}
            assertEquals(2,targets.size)
        } finally {com.mendelev.mpos.core.access.Administration.session=previous}
    }
    @Test fun adjustmentErrorsDistinguishAdminProofDeviceAccessAndStaleConsent()=runBlocking {
        val cases=listOf(
            Triple(403,"{\"error\":\"Administrator authorization required\"}","ADMIN_AUTH_REQUIRED"),
            Triple(403,"{\"error\":\"untrusted secret response\"}",null),
            Triple(409,"{\"code\":\"LOYALTY_BALANCE_CHANGED\"}","LOYALTY_BALANCE_CHANGED"),
            Triple(409,"{\"code\":\"LOYALTY_CONSENT_REQUIRED\"}","LOYALTY_CONSENT_REQUIRED"))
        cases.forEach {(status,body,code)->
            try {api {status to body}.profile("customer");fail()}
            catch(e:BackendFailure) {assertEquals(status,e.status);assertEquals(code,e.code);assertFalse(e.message!!.contains("untrusted secret response"))
                if(code=="ADMIN_AUTH_REQUIRED") assertFalse(e.message!!.contains("ключ устройства"))
            }
        }
    }

    @Test fun invalidAdjustmentNeverReachesTransport()=runBlocking {
        var writes=0
        val service=api {r->if(r.method!="GET") writes++;200 to profile}
        val p=service.profile("customer");val program=p.programs.single().copy(rewards=0)
        suspend fun rejected(customer: com.mendelev.mpos.domain.loyalty.LoyaltySelection,count: Int,reason: String) {
            try {service.adjust(customer,program,count,reason,"fixture-request",0);fail()}
            catch(_:IllegalArgumentException) {}
        }
        rejected(p.copy(customer=p.customer.copy(consentVersion=null)),3,"Причина")
        rejected(p.copy(customer=p.customer.copy(active=false)),3,"Причина")
        rejected(p,2,"Причина")
        rejected(p,6,"Причина")
        rejected(p,3," ")
        rejected(p,3,"x".repeat(501))
        assertEquals(0,writes)
    }

    @Test fun creationSendsFixedBelarusPhoneAndNeverEnrollsImplicitly()=runBlocking {
        val calls=mutableListOf<String>()
        val service=api {r->
            calls+=r.url.encodedPath;assertEquals("POST",r.method)
            val sink=Buffer();r.body!!.writeTo(sink);val body=JSONObject(sink.readUtf8())
            assertEquals("Тест",body.getString("name"));assertEquals("+375291234567",body.getString("phone"))
            assertFalse(body.has("consent"));assertFalse(body.has("acknowledged"))
            200 to """{"customer":{"id":"customer","name":"Тест","normalized_phone":"375291234567"}}"""
        }
        val customer=service.create(" Тест ","29 123 45 67")
        assertEquals("customer",customer.id);assertFalse(customer.active);assertEquals(listOf("/pos/api/customers"),calls)
        try {service.create("Тест","1234");fail()} catch(_:IllegalArgumentException) {}
        assertEquals(1,calls.size)
    }
    @Test fun selectedClientSurvivesLoyaltyOutageButCannotUseRewardsOrCrossBackend()=runBlocking {
        val customer=com.mendelev.mpos.domain.loyalty.LoyaltyCustomer("customer","Тест","375291234567")
        val selected=api {503 to "{}"}.select(customer)
        assertEquals("customer",selected.customer.id);assertFalse(selected.customer.active)
        assertTrue(selected.programs.isEmpty());assertTrue(selected.selected.isEmpty())
        assertEquals("dataset",selected.datasetId)
        var current=settings
        try {api({current}) {current=settings.copy(deviceKey="another-key");503 to "{}"}.select(customer);fail()}
        catch(_:IllegalStateException) {}
        try {api {409 to "{}"}.create("Тест","291234567");fail()}
        catch(e:BackendFailure) {assertEquals("CUSTOMER_EXISTS",e.code)}
    }
}
