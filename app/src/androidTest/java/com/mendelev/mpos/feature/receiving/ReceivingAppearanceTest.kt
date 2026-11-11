package com.mendelev.mpos.feature.receiving

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.warehouse.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReceivingAppearanceTest {
    @get:Rule val compose=createComposeRule()
    private val order=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","l"))),1)
    @Test fun employeeCanStartNewOrPendingReceivingWithoutAdministrativeSession() {
        var expanded by mutableStateOf(false);var history by mutableStateOf(false);var selected=""
        compose.setContent {MposTheme {
            ReceivingHome(listOf(order),emptyList(),emptyList(),expanded,history,false,{history=!history},{expanded=!expanded},{selected=it},{},{}) {Text("Ввод фактического количества")}
        }}
        compose.onNodeWithTag("receivingNew").assertIsEnabled().performClick()
        compose.onNodeWithText("Ввод фактического количества").assertExists()
        compose.onNodeWithTag("receivingHistory").performClick()
        compose.onNodeWithText("Приёмок пока нет.").assertExists()
        compose.onNodeWithTag("receivingOrder:o").performScrollTo().performClick()
        assertEquals("o",selected)
    }
    @Test fun singleConfirmationRequiresExplicitOverageAndDoesNotSubmitUntilClicked() {
        val p=ReceivingProduct("p","Молоко","l",1,1,"10","2",false)
        val d=ReceivingDraft("invoice",order,lines=listOf(ReceivingLine(p,"12","l","","l","48")))
        val preview=ReceivingPostingRules.preview(d,order,listOf(p));var agreed by mutableStateOf(false);var posts=0
        compose.setContent {MposTheme {ReceivingConfirmationWindow(preview,true,false,false,agreed,{agreed=!agreed},{},{posts++})}}
        compose.onNodeWithTag("receivingPostConfirm").assertIsNotEnabled()
        assertEquals(0,posts)
        compose.onNodeWithTag("receivingOverage").performClick()
        compose.onNodeWithTag("receivingPostConfirm").assertIsEnabled().performClick()
        assertEquals(1,posts)
    }
    @Test fun pendingCardsUseOneColumnAndSeparateAcceptAndResumeStyles() {
        val resumed=order.copy(id="resume")
        var selected=""
        compose.setContent {MposTheme {
            ReceivingHome(listOf(order,resumed),listOf(ReceivingRecord("resume",1,ReceivingDraft("draft",resumed,lines=emptyList()))),emptyList(),false,false,false,{},{},{selected=it},{},{}) {}
        }}
        val accept=compose.onNodeWithTag("receivingOrder:o")
        val resume=compose.onNodeWithTag("receivingOrder:resume")
        accept.performScrollTo().assertTextContains("Принять").assertHeightIsEqualTo(48.dp)
        val a=accept.getUnclippedBoundsInRoot()
        val green=accept.captureToImage().toPixelMap().let {it[20,it.height/2]}
        resume.performScrollTo().assertTextContains("Продолжить приёмку").assertHeightIsEqualTo(48.dp)
        val b=resume.getUnclippedBoundsInRoot()
        val yellow=resume.captureToImage().toPixelMap().let {it[20,it.height/2]}
        assertEquals(a.left.value,b.left.value,1f);assertEquals((a.right-a.left).value,(b.right-b.left).value,1f)
        assertNotEquals(green,yellow)
        resume.performClick();assertEquals("resume",selected)
    }

}
