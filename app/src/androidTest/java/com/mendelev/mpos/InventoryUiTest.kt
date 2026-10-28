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
    @Test fun selectStartCountStaleRecountFixAndRecreatePreserveExactHistory() {
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
        compose.waitUntil(20_000) {!vm.state.value.pending && !vm.state.value.blocked && compose.onAllNodesWithTag("inventoryActive").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("inventoryActive").performScrollTo().assertTextContains("Зафиксировано: 0 из 1")
        compose.onNodeWithTag("inventoryPause").assertExists()
        assertEquals(id,vm.state.value.snapshot!!.document.draft!!.id)
        assertEquals(before,runBlocking {Triple(db.catalog().product(p),db.catalog().balance(p),db.catalog().movementCount(p))})
        shot("inventory-start")
        compose.onNodeWithTag("topNav-inventory").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("inventoryCount:$p").performScrollTo().performClick()
        compose.waitUntil(20_000) {!vm.state.value.pending && vm.state.value.countForm!=null}
        compose.onNodeWithTag("topNav-workspace").assertIsNotEnabled()
        compose.onNodeWithTag("inventoryCountSave").performClick();assertNotNull(vm.state.value.countForm)
        compose.onNodeWithTag("inventoryActual").performTextInput("0,0004");hideKeyboard()
        compose.onNodeWithTag("inventoryCountSave").performClick();compose.waitUntil(20_000) {!vm.state.value.pending && vm.state.value.countForm==null}
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {!vm.state.value.pending && !vm.state.value.blocked && compose.onAllNodesWithTag("inventoryFix:$p").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("inventoryFix:$p").performScrollTo().assertIsEnabled()
        // A real product-card stock correction after the physical count invalidates it.
        runBlocking {
            val current=db.catalog().product(p)!!;val balance=db.catalog().balance(p)!!
            CatalogRepository(db).saveProduct(com.mendelev.mpos.domain.catalog.ProductInput(p,current.revision,current.name,current.categoryId,current.price,current.cost,"9",balance.revision,true,"stock-$key",unit=current.unit))
        }
        compose.onNodeWithTag("inventoryFix:$p").performClick();compose.onNodeWithTag("inventoryFixConfirm").performClick()
        compose.waitUntil(20_000) {!vm.state.value.pending && vm.state.value.snapshot?.products?.find {it.id==p}?.stock=="9"}
        compose.onNodeWithTag("inventoryFix:$p").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("inventorySaved:$p").assertTextContains("0,0004",substring=true)
        shot("inventory-stale")
        compose.onNodeWithTag("inventoryCount:$p").performClick()
        compose.waitUntil(20_000) {!vm.state.value.pending && vm.state.value.countForm!=null};assertEquals("",vm.state.value.countForm!!.raw)
        compose.onNodeWithTag("inventoryActual").performTextInput("0,0004");hideKeyboard()
        compose.onNodeWithTag("inventoryCountSave").performClick();compose.waitUntil(20_000) {!vm.state.value.pending && vm.state.value.countForm==null}
        compose.onNodeWithTag("inventoryFix:$p").performScrollTo().performClick();shot("inventory-fix-confirm")
        compose.onNodeWithTag("inventoryFixConfirm").performClick()
        compose.waitUntil(20_000) {!vm.state.value.pending && vm.state.value.snapshot?.document?.draft?.counts?.singleOrNull()?.fixed!=null}
        compose.onNodeWithTag("inventoryFixed:$p").performScrollTo().assertTextContains("0.0004",substring=true)
        compose.onNodeWithTag("inventoryCount:$p").assertDoesNotExist()
        assertEquals("0.0004",runBlocking {db.catalog().balance(p)!!.quantity});assertEquals(3,runBlocking {db.catalog().movementCount(p)})
        val fixed=vm.state.value.snapshot!!.document.draft!!.counts.single().fixed!!;assertEquals("-8.9996",fixed.delta)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {!vm.state.value.pending && !vm.state.value.blocked && compose.onAllNodesWithTag("inventoryFixed:$p").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("inventoryFixed:$p").performScrollTo().assertTextContains("0.0004",substring=true);shot("inventory-fixed")
        compose.onNodeWithTag("inventoryPause").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCheckout").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("topNav-inventory").performScrollTo().performClick()
        compose.waitUntil(20_000) {!vm.state.value.pending && !vm.state.value.blocked && compose.onAllNodesWithTag("inventoryFixed:$p").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("inventoryFixed:$p").performScrollTo().assertTextContains("0.0004",substring=true)
    }
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shot(name: String) {
        hideKeyboard();compose.waitForIdle();val a=InstrumentationRegistry.getInstrumentation().uiAutomation;a.waitForIdle(200,5_000)
        compose.waitUntil(10_000) {a.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID}
        check(a.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID)
        val b=checkNotNull(a.takeScreenshot());val f=File(compose.activity.filesDir,"qa/$name.png");f.parentFile!!.mkdirs();f.outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
}
