package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.SalesReportAccess
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.feature.report.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*

class ReportUiTest {
    @get:Rule val compose=createComposeRule()
    private val clock=Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"),ZoneId.of("Europe/Minsk"))
    private fun model(store: ViewModelStore)=ViewModelProvider(store,object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=ReportViewModel(object: SalesReportAccess {
            override suspend fun snapshot(period: ReportPeriod): SalesReport {
                val sold=ReportSale("sale",Instant.parse("2026-10-30T12:00:00Z").toEpochMilli(),2500,1000,1500,500)
                val refund=ReportRefund("refund",sold.id,clock.millis(),2500,1000,1500,500)
                return SalesReports.build(period,clock.millis(),listOf(sold),listOf(refund))
            }
        },SavedStateHandle(),clock) as T
    })[ReportViewModel::class.java]
    @Test fun periodTotalsValidationAndLogoutRemoveAmountsAndDateDialog() {
        val store=ViewModelStore();lateinit var vm: ReportViewModel
        compose.runOnIdle {vm=model(store)}
        compose.setContent {MposTheme {ReportScreen(vm,{},{})}}
        compose.waitUntil(20_000) {vm.state.value.report!=null}
        compose.onNodeWithTag("reportNet").assertTextEquals("-25,00 BYN")
        compose.onNodeWithTag("reportCount").assertTextEquals("0")
        compose.onNodeWithTag("reportFrom").performClick()
        compose.onNodeWithTag("reportPeriodFrom").performTextReplacement("2026-11-01")
        compose.onNodeWithTag("reportApply").performClick()
        compose.onNodeWithText("Введите обе даты в формате ГГГГ-ММ-ДД; начало не позже окончания").assertExists()
        compose.onNodeWithTag("reportPeriodFrom").performTextReplacement("2026-10-30")
        compose.onNodeWithTag("reportApply").performClick()
        compose.waitUntil(20_000) {vm.state.value.report?.saleCount==1L}
        compose.onNodeWithTag("reportNet").assertTextEquals("0,00 BYN")
        shot("analytics-period")
        compose.onNodeWithTag("reportFrom").performClick()
        runBlocking {Administration.session.logout()}
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("reportLocked").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("reportNet").assertDoesNotExist();compose.onNodeWithTag("reportApply").assertDoesNotExist()
        shot("analytics-locked")
        runBlocking {Administration.session.login("fixture-admin".toCharArray())}
        compose.waitUntil(20_000) {vm.state.value.report!=null}
        compose.onNodeWithTag("reportApply").assertDoesNotExist()
        compose.runOnIdle {store.clear()}
    }
    @Test fun narrowLargeFontHasScrollablePaymentPanelsAndNoDeferredCustomerBlocks() {
        val store=ViewModelStore();lateinit var vm: ReportViewModel
        compose.runOnIdle {vm=model(store)}
        compose.setContent {MposTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                androidx.compose.foundation.layout.Box(Modifier.width(520.dp)) {ReportScreen(vm,{},{})}
            }
        }}
        compose.waitUntil(20_000) {vm.state.value.report!=null}
        compose.onNodeWithTag("reportRefunds").performScrollTo().assertTextEquals("25,00 BYN")
        compose.onNodeWithTag("reportScroll").performScrollToNode(hasTestTag("reportRefundsCard"))
        compose.onNodeWithTag("reportRefundsCard").assertTextEquals("15,00 BYN")
        shot("analytics-narrow")
        compose.onNodeWithText("Клиенты и лояльность").assertDoesNotExist()
        compose.runOnIdle {store.clear()}
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap=checkNotNull(i.uiAutomation.takeScreenshot());val file=java.io.File(i.targetContext.filesDir,"qa/$name.png")
        file.parentFile!!.mkdirs();file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
