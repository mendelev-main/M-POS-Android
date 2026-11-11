package com.mendelev.mpos.feature.loyalty

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.loyalty.LoyaltySelection
import com.mendelev.mpos.domain.workspace.CurrentCart
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomerSelectionFlowTest {
    @get:Rule val compose=createComposeRule()
    private fun api(): LoyaltyApi {
        val settings=BackendSettings(url="https://fixture.example.org",deviceKey="fixture-key",datasetId="fixture")
        val customer="""{"id":"customer","name":"Тест","normalized_phone":"375290004567"}"""
        val http=BackendHttp(OkHttpClient.Builder().addInterceptor {chain->
            val request=chain.request()
            check(request.url.host=="fixture.example.org")
            val body=if(request.url.encodedPath.endsWith("/search")) """{"customers":[$customer]}"""
                else """{"customer":$customer,"membership":{"active":true,"version":1},"programs":[]}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build())
        return LoyaltyApi({settings},http)
    }
    @Test fun selectionPersistsThenOpensProfileWithoutClosingAndDoneIsExplicit() {
        var attached: LoyaltySelection?=null;var saves=0;var closes=0
        val client=api()
        compose.setContent {MposTheme {CustomerWindow(CurrentCart(sessionId="fixture"),false,null,{closes++},
            persist={attached=it},client=client) {saves++}}}
        compose.onNodeWithTag("customerSearch").performTextInput("4567")
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("customer:customer").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("customer:customer").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("customerProfileHeading").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("customer",attached!!.customer.id)
        assertEquals(0,closes);assertEquals(0,saves)
        compose.onNodeWithTag("customerDone").performClick()
        assertEquals(1,saves)
    }
    @Test fun failedAttachmentKeepsSearchOpenAndDoesNotClaimSuccess() {
        var saves=0;val client=api()
        compose.setContent {MposTheme {CustomerWindow(CurrentCart(sessionId="fixture"),false,null,{},
            persist={error("fixture persistence failed")},client=client) {saves++}}}
        compose.onNodeWithTag("customerSearch").performTextInput("4567")
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("customer:customer").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("customer:customer").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithText("fixture persistence failed").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("customerProfileHeading").assertDoesNotExist()
        compose.onNodeWithTag("customerSearch").assertExists()
        assertEquals(0,saves)
    }
}
