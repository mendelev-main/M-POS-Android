package com.mendelev.mpos.feature.sale

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReceiptHistoryAppearanceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun twoPanelsSelectionHistoryAmountsReturnAndEmptyStateInBothThemes() {
        val cart=CurrentCart(lines=listOf(CartLine("line","p","Капучино","6.5",false,"6.5","2","шт",emptyList(),emptyList(),emptyMap())))
        val quote=SaleMoney.quote(listOf(SaleLineInput("line","6.5","2")),"here",null)
        val payment=SaleMoney.payments(quote,listOf(PaymentInput("cash",1300,2000)),0)
        val snapshot=ReceiptSnapshot(cart,CheckoutDraft("cmd","session",0,"shift",0,"actor"),quote,payment,emptyMap(),emptyList())
        val receipt=ReceiptEntity("id","cmd","session","shift",1,1,1300,1300,0,0,"")
        val returned=RefundEntity("ref","id","ref-cmd","shift","actor",2,1300,1300,0,0,"hash","")
        var picked="";var refunds=0;var print=0;var changes=0
        val dark=mutableStateOf(false);val isReturned=mutableStateOf(false);val empty=mutableStateOf(false);val allowed=mutableStateOf(true)
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalDensity provides Density(1f,1f),LocalConfiguration provides config) {MposTheme {Surface(Modifier.size(1200.dp,760.dp)) {
                ReceiptHistoryPage(if(empty.value) emptyList() else listOf(receipt),mapOf("id" to "here"),mapOf("id" to "Наличные"),"id",if(empty.value) null else receipt to snapshot,
                    if(isReturned.value) listOf(returned) else emptyList(),allowed.value,true,{picked=it},{refunds++},{print++},onChangePayment={changes++})
            }}}
        }
        val left=compose.onNodeWithTag("receiptsListPanel").getUnclippedBoundsInRoot()
        val right=compose.onNodeWithTag("receiptDetailPanel").getUnclippedBoundsInRoot()
        val type=compose.onNodeWithTag("parkedType:here",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val method=compose.onNodeWithTag("receiptPayment:Наличные",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertEquals((type.bottom-type.top).value,(method.bottom-method.top).value,1f)
        assertEquals(type.bottom.value,method.bottom.value,1f)
        assertTrue(method.left>=type.right)

        assertEquals(26f,left.left.value,1f);assertEquals(16f,(right.left-left.right).value,1f)
        assertEquals(.82f/1.18f,(left.right-left.left).value/(right.right-right.left).value,.01f)
        compose.onNodeWithTag("receipt:cmd").assertIsSelected().performClick();assertEquals("id",picked)
        compose.onNodeWithTag("receiptLine:line").assertTextEquals("Капучино × 2")
        compose.onNodeWithTag("receiptTotal").assertTextEquals("13,00 BYN")
        compose.onNodeWithText("7,00 BYN").assertExists()
        compose.onNodeWithTag("receiptChangePayment").performClick();assertEquals(1,changes)
        compose.onNodeWithTag("refundPrepare").performClick();assertEquals(1,refunds)
        compose.onNodeWithTag("receiptPrint").performClick();assertEquals(1,print)
        compose.runOnIdle {allowed.value=false}
        compose.onNodeWithTag("refundPrepare").assertIsNotEnabled();compose.onNodeWithTag("receipt:cmd").assertIsNotEnabled()
        compose.runOnIdle {allowed.value=true;dark.value=true;isReturned.value=true}
        compose.onNodeWithTag("receiptChangePayment").assertDoesNotExist();compose.onNodeWithTag("returned:id",useUnmergedTree=true).assertExists();compose.onNodeWithTag("refundPrepare").assertDoesNotExist()
        compose.runOnIdle {empty.value=true}
        compose.onNodeWithText("Чеков пока нет — они появятся здесь после первой оплаты.").assertExists()
        compose.onNodeWithText("Выберите чек слева.").assertExists()
    }
}
