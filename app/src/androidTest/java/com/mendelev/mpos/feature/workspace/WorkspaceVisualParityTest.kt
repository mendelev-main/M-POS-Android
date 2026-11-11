package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.mendelev.mpos.domain.workspace.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.workspace.CurrentCart
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** External contract: pinned Web W01/W02 metrics, 380px panel / 12px gutters / 34% park / 8px gap. */
class WorkspaceVisualParityTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cartFooterUsesWebPercentOfWholeRowAndNativeActionsRemainDisabledForEmptyCart() {
        val snapshot=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),CurrentCart(),0,emptyList(),0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f,1f)) {
                MposTheme {WorkspaceCartPanel(snapshot,0,Modifier.size(380.dp,800.dp),{},{_,_->},{},{},{},{},{},{},{},false)}
            }
        }
        compose.onNodeWithTag("workspaceCartPanel").assertWidthIsEqualTo(380.dp)
        val park=compose.onNodeWithTag("workspacePark");val pay=compose.onNodeWithTag("workspaceCheckout")
        park.assertHeightIsEqualTo(47.dp).assertIsNotEnabled();pay.assertHeightIsEqualTo(47.dp).assertIsEnabled().assertTextContains("Открыть смену")
        val p=park.getUnclippedBoundsInRoot();val c=pay.getUnclippedBoundsInRoot()
        // W02-light.json: park121.03px (34% of356), pay226.97px; tolerate device rounding only.
        assertEquals(121.04f,(p.right-p.left).value,.75f)
        assertEquals(226.96f,(c.right-c.left).value,.75f)
        assertEquals(8f,(c.left-p.right).value,.75f)
        compose.onNodeWithTag("workspaceCustomer").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        compose.onNodeWithTag("workspaceTotal").assertTextEquals("0,00 BYN")
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"qa").apply {mkdirs()}
        val bitmap=compose.onNodeWithTag("workspaceCartPanel").captureToImage().asAndroidBitmap()
        File(root,"workspace-web-cart-empty.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun addingScrollsToNewLineAndSwipeRemovesWithoutRecipePortionLabel() {
        fun line(n: Int)=CartLine("line$n","p$n","Товар $n","2",false,"2","1","piece",emptyList(),emptyList(),emptyMap(),sellingBasis="recipe-batch")
        val cart=CurrentCart(lines=(1..20).map(::line))
        val state=mutableStateOf(WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),cart,1,emptyList(),0))
        compose.setContent {
            MposTheme {WorkspaceCartPanel(state.value,0,Modifier.size(400.dp,650.dp),{},{_,_->},{id->
                state.value=state.value.copy(cart=state.value.cart.copy(lines=state.value.cart.lines.filterNot {it.id==id}),cartRevision=state.value.cartRevision+1)
            },{},{},{},{},{},{},true)}
        }
        compose.runOnIdle {state.value=state.value.copy(cart=cart.copy(lines=cart.lines+line(21)),cartRevision=2)}
        compose.waitForIdle()
        compose.onNodeWithTag("workspaceLine:line21").assertIsDisplayed()
        compose.onNodeWithText("порция рецепта",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("workspaceLine:line21").performTouchInput {swipeLeft()}
        compose.waitUntil(5000) {state.value.cart.lines.none {it.id=="line21"}}
        assertEquals(20,state.value.cart.lines.size)
    }

}
