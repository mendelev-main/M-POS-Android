package com.mendelev.mpos

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.workspace.CurrentCart
import com.mendelev.mpos.feature.workspace.WorkspaceCartPanel
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.domain.settings.*
import com.mendelev.mpos.domain.sale.SaleDiscount
import com.mendelev.mpos.domain.workspace.CartLine
import com.mendelev.mpos.feature.workspace.CartItemWindow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CartItemWindowTest {
    @get:Rule val compose=createComposeRule()
    private val line=CartLine("line","p","Чай","10",false,"10","1","piece",emptyList(),emptyList(),emptyMap())
    @Test fun changesStayLocalUntilSave() {
        var saved: List<Any?>?=null
        var open by mutableStateOf(true)
        val discount=SettingEntry("staff",SettingKind.DISCOUNT,"Сотруднику","percent","10")
        compose.setContent {MposTheme {if(open) CartItemWindow(line,null,listOf(discount),false,null,{open=false},{q,c,id,d->saved=listOf(q,c,id,d);open=false})}}
        compose.onNodeWithTag("workspacePlus:line").performClick()
        compose.onNodeWithTag("cartItemComment").performTextInput("Без сахара")
        compose.onNodeWithTag("cartItemDiscount:staff").performScrollTo().performClick().assertIsSelected()
        assertNull(saved)
        compose.onNodeWithTag("cartItemSave").performScrollTo().performClick()
        assertEquals(listOf("2","Без сахара","staff",SaleDiscount("percent","10","Сотруднику")),saved)
        assertFalse(open)
    }
    @Test fun cancelDiscardsDraftAndMinusDoesNotDeletePosition() {
        var saves=0;var closed=false
        compose.setContent {MposTheme {CartItemWindow(line,null,emptyList(),false,null,{closed=true},{_,_,_,_->saves++})}}
        compose.onNodeWithTag("workspaceMinus:line").performClick()
        compose.onNodeWithTag("cartItemQuantity").assertTextEquals("1")
        compose.onNodeWithTag("cartItemComment").performTextInput("Не сохранять")
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        assertTrue(closed);assertEquals(0,saves)
    }
    @Test fun rowSwipeRetainsDeletionOutsideTheEditor() {
        var deletes=0
        val s=WorkspaceSnapshot(emptyList(),emptyList(),emptyList(),emptyMap(),CurrentCart(lines=listOf(line)),0,emptyList(),0)
        compose.setContent {MposTheme {WorkspaceCartPanel(s,0,Modifier.width(380.dp).height(650.dp),{},{_,_->},{deletes++},{},{},{},{},{},{},true)}}
        compose.onNodeWithTag("workspaceLine:line").performTouchInput {swipeLeft()}
        compose.waitUntil(5_000) {deletes==1}
        assertEquals(1,deletes)
    }

}
