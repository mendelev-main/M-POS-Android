package com.mendelev.mpos.feature.sale

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PaymentAppearanceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val cart=CurrentCart(lines=listOf(
        CartLine("coffee","coffee","Капучино","6.50",false,"6.50","2","шт",emptyList(),emptyList(),emptyMap()),
        CartLine("cake","cake","Чизкейк классический","8",false,"8","1","шт",emptyList(),emptyList(),emptyMap())),sessionId="web-atlas")
    private fun shot(name: String) {
        val bitmap=compose.onNodeWithTag("paymentPage").captureToImage().asAndroidBitmap()
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"qa").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun webPaymentGeometryKeypadTenderAndActionsInBothThemes() {
        val dark=mutableStateOf(false);val allowed=mutableStateOf(true)
        var given by mutableStateOf("21.00");var method="";var split=0;var back=0
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window,false)
            WindowCompat.getInsetsController(compose.activity.window,compose.activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(dark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            CompositionLocalProvider(LocalConfiguration provides config) {MposTheme {Surface {
                PaymentPage(cart.copy(sessionId=if(dark.value) "dark" else "light"),SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity)},"here",null),given,allowed.value,true,null,false,{given=it},{back++},{split++},{method=it},{})
            }}}
        }
        compose.onNodeWithTag("paymentPage").assertWidthIsEqualTo(1280.dp).assertHeightIsEqualTo(900.dp)
        val receipt=compose.onNodeWithTag("paymentReceipt").getUnclippedBoundsInRoot()
        val box=compose.onNodeWithTag("paymentBox").getUnclippedBoundsInRoot()
        assertEquals(22f,receipt.left.value,1f);assertEquals(82f,receipt.top.value,1f)
        assertEquals(353.22f,(receipt.right-receipt.left).value,1f)
        assertEquals(18f,(box.left-receipt.right).value,1f)
        compose.onNodeWithTag("cashGiven").assertHeightIsEqualTo(58.dp)
        compose.onNodeWithTag("salePrepare").assertHeightIsEqualTo(52.dp)
        compose.onNodeWithTag("paymentQuick:5000").assertHeightIsEqualTo(46.dp)
        shot("P01-native-light")
        compose.onNodeWithTag("paymentQuick:5000").performClick()
        compose.onNodeWithTag("paymentChange").assertTextEquals("29,00 BYN");shot("P03-native-light")
        compose.onNodeWithTag("cashGiven").performClick()
        compose.onNodeWithTag("paymentQuick:5000").assertDoesNotExist()
        compose.onNodeWithTag("paymentKey:0").assertHeightIsEqualTo(46.dp)
        val digit=compose.onNodeWithTag("paymentKey:1").getUnclippedBoundsInRoot()
        val zero=compose.onNodeWithTag("paymentKey:0").getUnclippedBoundsInRoot()
        assertEquals(2*(digit.right-digit.left).value+8,(zero.right-zero.left).value,1f)
        shot("P02-native-light")
        compose.onNodeWithTag("paymentKey:1").performClick();compose.onNodeWithTag("paymentKey:0").performClick()
        compose.onNodeWithTag("paymentKey:,").performClick();compose.onNodeWithTag("paymentKey:5").performClick()
        assertEquals("10,5",given)
        compose.onNodeWithTag("paymentKey:backspace").performClick();assertEquals("10,",given)
        compose.onNodeWithTag("salePrepareCard").performClick();assertEquals("card",method)
        compose.onNodeWithTag("salePrepare").performClick();assertEquals("cash",method)
        compose.onNodeWithTag("paymentSplit").performClick();assertEquals(1,split)
        compose.onNodeWithTag("paymentBack").performClick();assertEquals(1,back)
        compose.runOnIdle {dark.value=true;given="21.00"};shot("P01-native-dark")
        compose.onNodeWithTag("paymentQuick:5000").performClick();shot("P03-native-dark")
        compose.onNodeWithTag("cashGiven").performClick();shot("P02-native-dark")
        compose.runOnIdle {allowed.value=false}
        compose.onNodeWithTag("salePrepare").assertIsNotEnabled();compose.onNodeWithTag("salePrepareCard").assertIsNotEnabled()
        compose.onNodeWithTag("paymentBack").assertIsNotEnabled();compose.onNodeWithTag("paymentSplit").assertIsNotEnabled()
    }
}
