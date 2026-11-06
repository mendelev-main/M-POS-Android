package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.feature.products.CatalogViewModel
import com.mendelev.mpos.feature.suppliers.SupplierViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class SupplierUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shot(name: String) {
        compose.waitForIdle();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(200,5_000)
        check(automation.rootInActiveWindow?.packageName?.toString()==BuildConfig.APPLICATION_ID) {"Supplier screenshot is blocked by another app/system window"}
        val bitmap=checkNotNull(automation.takeScreenshot());val file=File(compose.activity.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun createLinkRecreateRenameDeleteAndKeepStockUnchanged() {
        lateinit var catalog: CatalogViewModel
        compose.runOnIdle {catalog=ViewModelProvider(compose.activity)[CatalogViewModel::class.java]}
        compose.waitUntil(20_000) {!catalog.state.value.loading && !catalog.state.value.busy}
        if(catalog.state.value.draft!=null) {compose.runOnIdle {catalog.discard()};compose.waitUntil(20_000) {catalog.state.value.draft==null && !catalog.state.value.busy}}
        val key=UUID.randomUUID().toString().take(6);val product="supplier-product-$key";val productName="Чай поставщика $key";val name="Поставщик $key"
        val db=CatalogDatabase.get(compose.activity.applicationContext)
        val before=runBlocking {
            CatalogRepository(db).saveProduct(ProductInput(product,null,productName,null,"2","1","0",null,true,"supplier-ui-$key",noStockTracking=true))
            db.catalog().product(product)!! to db.catalog().balance(product)!!
        }
        compose.onNodeWithTag("topNav-settings").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("settingsSuppliers").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("settingsSuppliers").performClick()
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("supplierNew") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        lateinit var model: SupplierViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[SupplierViewModel::class.java]}
        compose.onNodeWithTag("supplierNew").performClick()
        compose.onNodeWithTag("supplierSave").performScrollTo().performClick()
        compose.onNodeWithText("Введите название от 1 до 200 символов").assertExists()
        compose.onNodeWithTag("supplierName").performScrollTo().performTextInput(name)
        compose.onNodeWithTag("supplierSearch").performScrollTo().performTextInput(productName)
        compose.onNodeWithTag("supplierProduct:$product").performClick();hideKeyboard()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithText("Изменения поставщика ещё не сохранены.").assertIsDisplayed()
        compose.onNodeWithText("Продолжить редактирование").performClick()
        compose.onNodeWithTag("supplierName").assertTextContains(name)
        shot("supplier-card")
        compose.onNodeWithTag("supplierSave").performScrollTo().performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.form==null}
        val id=model.state.value.snapshot!!.suppliers.single {it.name==name}.id
        assertEquals(setOf(product),model.state.value.snapshot!!.suppliers.single {it.id==id}.productIds)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("supplier:$id") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("supplier:$id").performScrollTo().performClick()
        compose.onNodeWithTag("supplierName").performScrollTo().performTextReplacement("$name новый");hideKeyboard()
        compose.onNodeWithTag("supplierSave").performScrollTo().performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.form==null && model.state.value.snapshot!!.suppliers.any {it.id==id && it.name=="$name новый"}}
        compose.onNodeWithTag("supplier:$id").performScrollTo().performClick()
        compose.onNodeWithTag("supplierDelete").performScrollTo().performClick()
        compose.onNodeWithTag("supplierDeleteConfirm").performScrollTo().performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.form==null && model.state.value.snapshot!!.suppliers.none {it.id==id}}
        assertEquals(before,runBlocking {db.catalog().product(product)!! to db.catalog().balance(product)!!})
        // Leave a live supplier for the update UI evidence, without changing workspace/home fixtures.
        compose.onNodeWithTag("supplierNew").performClick()
        compose.onNodeWithTag("supplierName").performScrollTo().performTextInput("Поставка $key")
        compose.onNodeWithTag("supplierSearch").performScrollTo().performTextInput(productName)
        compose.onNodeWithTag("supplierProduct:$product").performClick();hideKeyboard()
        compose.onNodeWithTag("supplierSave").performScrollTo().performClick()
        compose.waitUntil(20_000) {!model.state.value.pending && model.state.value.form==null}
        shot("supplier-list")
    }
}
