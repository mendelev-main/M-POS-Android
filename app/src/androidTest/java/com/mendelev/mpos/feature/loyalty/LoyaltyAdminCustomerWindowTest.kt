package com.mendelev.mpos.feature.loyalty

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.core.ui.MposTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test

/** Isolated responses only; never writes customer data or logs in an application administrator. */
class LoyaltyAdminCustomerWindowTest {
    @get:Rule val compose=createComposeRule()
    private fun show(rewards: Int,ledgerFails: Boolean=false) {
        val http=BackendHttp(OkHttpClient.Builder().addInterceptor {chain->
            val path=chain.request().url.encodedPath
            val status=if(ledgerFails && path.endsWith("/ledger")) 503 else 200
            val body=when {
                path.endsWith("/loyalty")->"""{"customer":{"id":"client","name":"Тестовый клиент","normalized_phone":"375290000000"},"membership":{"active":true,"version":4},"programs":[{"id":"program","name":"Кофе","required_quantity":5,"progress":2,"rewards":REWARDS}]}""".replace("REWARDS",rewards.toString())
                path.endsWith("/ledger")->if(ledgerFails) "{}" else """{"ledger":[]}"""
                else->"""{"orders":[]}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build())
        val api=LoyaltyApi({BackendSettings(url="https://fixture.example.org",deviceKey="fixture-key",datasetId="fixture")},http)
        compose.setContent {MposTheme {LoyaltyAdminCustomerWindow("client",api,0,{})}}
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("adminLoyaltyProgram:program").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun adjustmentStaysInsideCardAndRequiresChangedCountAndReason() {
        show(0)
        compose.onNodeWithText("Изменить").performScrollTo().performClick()
        compose.onNodeWithTag("loyaltyAdminCustomerWindow").assertExists()
        compose.onNodeWithTag("loyaltyAdjustmentSave").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("loyaltyAdjustmentTarget").performTextReplacement("5")
        compose.onNodeWithTag("loyaltyAdjustmentReason").performTextInput("Исправление пропущенной покупки")
        compose.onNodeWithTag("loyaltyAdjustmentSave").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Будет начислен один подарок, прогресс начнётся с нуля.").assertExists()
    }
    @Test fun failedLedgerDoesNotHideProfileAndAvailableGiftRequiresExplicitRemoval() {
        show(1,true)
        compose.onNodeWithText("Изменить").performScrollTo().performClick()
        compose.onNodeWithTag("loyaltyAdjustmentTarget").assertDoesNotExist()
        compose.onNodeWithTag("loyaltyAdjustmentReason").performTextInput("Снять ошибочный подарок")
        compose.onNodeWithTag("loyaltyAdjustmentSave").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("loyaltyRemoveGift").performScrollTo().performClick()
        compose.onNodeWithTag("loyaltyAdjustmentSave").performScrollTo().assertIsEnabled()
    }
}
