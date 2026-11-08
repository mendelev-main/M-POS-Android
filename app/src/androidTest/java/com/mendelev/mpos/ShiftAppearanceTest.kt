package com.mendelev.mpos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.feature.shift.ShiftOverview
import com.mendelev.mpos.feature.shift.ShiftReportWindow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShiftAppearanceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun activeActionsRouteAndClosedHistoryOpensReadOnlyReport() {
        val closed=Shift("closed","r","anna","Анна",1,"UTC",0,null,2,0,0,0)
        val active=Shift("active","r","boris","Борис",3,"UTC",0,"closed")
        val data=ShiftDashboardRules.build(ShiftSnapshot("r","owner",2,active),listOf(closed,active),emptyList(),emptyList(),emptyList()).copy(nextHistory=ShiftHistoryCursor(2,"closed"))
        var deposits=0;var withdrawals=0;var closes=0;var more=0;var drawers=0;var report by mutableStateOf<String?>(null)
        compose.setContent {MposTheme {
            Column(Modifier.width(1000.dp).height(850.dp).verticalScroll(rememberScrollState())) {
                ShiftOverview(data,true,true,false,{}, {closes++},{deposits++},{withdrawals++},{drawers++},{report=it},onMore={more++})
            }
            if(report!=null) ShiftReportWindow(data.history.single(),{report=null},{})
        }}
        compose.onNodeWithTag("shiftDrawer").performScrollTo().performClick()
        assertEquals(1,drawers)
        val sales=compose.onNodeWithTag("shiftSalesSummary").getUnclippedBoundsInRoot()
        val revenue=compose.onNodeWithTag("shiftRevenueSummary").getUnclippedBoundsInRoot()
        val cash=compose.onNodeWithTag("shiftCashSummary").getUnclippedBoundsInRoot()
        assertEquals(sales.top,revenue.top);assertTrue(sales.right<=revenue.left)
        assertTrue(cash.top>=sales.bottom && cash.top>=revenue.bottom)
        compose.onNodeWithTag("shiftDeposit").performScrollTo().performClick()
        compose.onNodeWithTag("shiftWithdraw").performScrollTo().performClick()
        compose.onNodeWithTag("shiftClose").performScrollTo().performClick()
        assertEquals(1,deposits);assertEquals(1,withdrawals);assertEquals(1,closes)
        compose.onNodeWithTag("shiftHistory:closed").performScrollTo().performClick()
        compose.onNodeWithText("Отчёт по смене").assertIsDisplayed()
        compose.onNodeWithText("Закрыть").performScrollTo().performClick()
        assertNull(report)
        compose.onNodeWithTag("shiftHistoryMore").performScrollTo().performClick()
        assertEquals(1,more)
    }
}
