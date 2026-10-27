package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.feature.products.CatalogViewModel
import com.mendelev.mpos.feature.inventory.InventoryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.io.File

class InventoryUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun selectSaveStartDirtyDiscardAndRecreateDoNotChangeStock() {
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle {catalog=ViewModelProvider(compose.activity)[CatalogViewModel::class.java]}
        compose.waitUntil(20_000) {!catalog.state.value.loading && !catalog.state.value.busy}
        if(catalog.state.value.draft!=null) {compose.runOnIdle {catalog.discard()};compose.waitUntil(20_000) {catalog.state.value.draft==null && !catalog.state.value.busy}}
        val db=CatalogDatabase.get(compose.activity.applicationContext);val key=UUID.randomUUID().toString().take(6)
        val p=runBlocking {standaloneInvoice(db,"inv-$key")}.lines.single().product.id
        val before=runBlocking {Triple(db.catalog().product(p),db.catalog().balance(p),db.catalog().movementCount(p))}
        compose.onNodeWithTag("topNav-settings").performScrollTo().performClick()
        compose.onNodeWithTag("settingsInventory").performScrollTo().performClick()
        lateinit var vm: InventoryViewModel
        compose.runOnIdle {vm=ViewModelProvider(compose.activity)[InventoryViewModel::class.java]}
        compose.waitUntil(20_000) {!vm.state.value.pending && !vm.state.value.blocked}
        compose.onNodeWithTag("inventorySearch").performScrollTo().performTextInput(key)
        compose.onNodeWithTag("inventoryProduct:$p").performScrollTo().performClick();hideKeyboard()
        compose.onNodeWithTag("inventorySave").performClick();compose.waitUntil(20_000) {!vm.state.value.pending && !vm.state.value.dirty}
        compose.onNode(hasAnyAncestor(hasTestTag("inventoryFrequency")) and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasAnyAncestor(isPopup()) and hasText("Ежеквартально")).performClick()
        compose.onNodeWithTag("inventoryStart").assertIsNotEnabled()
        compose.onNodeWithTag("inventoryBack").performClick();compose.onNodeWithTag("inventoryDiscard").performClick()
        compose.onNodeWithTag("settingsInventory").performScrollTo().performClick()
        compose.waitUntil(20_000) {!vm.state.value.pending && !vm.state.value.blocked}
        compose.onNodeWithTag("inventoryStart").performClick();compose.onNodeWithTag("inventoryStartConfirm").performClick()
        compose.waitUntil(20_000) {!vm.state.value.pending && vm.state.value.snapshot?.document?.draft!=null}
        val id=vm.state.value.snapshot!!.document.draft!!.id
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("inventoryActive").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("inventoryActive").performScrollTo().assertTextContains("Товаров: 1")
        compose.onNodeWithTag("inventoryStart").assertIsNotEnabled()
        assertEquals(id,vm.state.value.snapshot!!.document.draft!!.id)
        assertEquals(before,runBlocking {Triple(db.catalog().product(p),db.catalog().balance(p),db.catalog().movementCount(p))})
        shot()
    }
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shot() {
        hideKeyboard();compose.waitForIdle();val a=InstrumentationRegistry.getInstrumentation().uiAutomation;a.waitForIdle(200,5_000)
        compose.waitUntil(10_000) {a.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID}
        check(a.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val b=checkNotNull(a.takeScreenshot());val f=File(compose.activity.filesDir,"qa/inventory-start.png");f.parentFile!!.mkdirs();f.outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
}
