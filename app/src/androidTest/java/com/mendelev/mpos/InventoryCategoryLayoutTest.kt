package com.mendelev.mpos

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.inventory.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InventoryCategoryLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun categoriesAreSeparateCardsAndHeaderActionsShareHeight() {
        val products=listOf(InventoryProduct("a","Кофе","Напитки","piece",1,1,"10","2"),InventoryProduct("b","Булка","Выпечка","piece",1,1,"10","2"))
        val snapshot=InventorySnapshot("owner",1,InventoryDocument(InventoryConfig(true,"monthly",setOf("a","b")),InventoryDraft("draft","adhoc",1,"2026-11-10",products)),products,"2026-11-10","fixture")
        val access=object: InventoryAccess {
            override suspend fun snapshot()=snapshot
            override suspend fun commit(c: InventoryCommand): String=error("Read-only UI fixture")
            override suspend fun outcome(c: InventoryCommand): String?=null
        }
        val store=ViewModelStore()
        lateinit var model: InventoryViewModel
        compose.runOnIdle {model=ViewModelProvider(store,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=InventoryViewModel(access) as T
        })[InventoryViewModel::class.java]}
        try {
            compose.setContent {MposTheme {Surface(Modifier.size(1100.dp,800.dp)) {InventoryScreen(model,{},work=true)}}}
            compose.waitUntil(5000) {model.state.value.snapshot!=null && !model.state.value.pending}
            val first=compose.onNodeWithTag("inventoryCategory:Напитки").getUnclippedBoundsInRoot()
            val next=compose.onNodeWithTag("inventoryCategory:Выпечка").getUnclippedBoundsInRoot()
            assertTrue(next.top.value-first.bottom.value>=15f)
            assertEquals(first.left.value,next.left.value,1f)
            compose.onNodeWithTag("inventoryPause").assertHeightIsEqualTo(44.dp)
            compose.onNodeWithTag("inventoryComplete").assertHeightIsEqualTo(44.dp)
            compose.onNodeWithTag("inventoryCancel").assertHeightIsEqualTo(44.dp)
        } finally {compose.runOnIdle {store.clear()}}
    }
}
