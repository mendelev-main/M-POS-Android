package com.mendelev.mpos
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.feature.workspace.ParkedType
import com.mendelev.mpos.feature.sale.ReceiptPaymentSticker
import org.junit.Rule
import org.junit.Test
class ReceiptStickerSizeTest {
    @get:Rule val compose=createComposeRule()
    @Test fun everyOrderAndPaymentBadgeHasSameDimensions() {
        compose.setContent {MposTheme {Column {
            listOf("here","takeaway","delivery").forEach {ParkedType(it,compact=true)}
            listOf("Карта","Наличные","Наличные + карта","—").forEach {ReceiptPaymentSticker(it)}
        }}}
        listOf("here","takeaway","delivery").forEach {
            compose.onNodeWithTag("parkedType:$it").assertWidthIsEqualTo(128.dp).assertHeightIsEqualTo(23.dp)
        }
        listOf("Карта","Наличные","Наличные + карта","—").forEach {
            compose.onNodeWithTag("receiptPayment:$it").assertWidthIsEqualTo(128.dp).assertHeightIsEqualTo(23.dp)
        }
    }
}
