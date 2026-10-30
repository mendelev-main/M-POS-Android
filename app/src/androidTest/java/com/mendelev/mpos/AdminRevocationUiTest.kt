package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.feature.products.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AdminRevocationUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun productAndCategoryConfirmationsCloseAndDoNotReappearAfterLogin() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build();val store=ViewModelStore()
        lateinit var model: CatalogViewModel
        try {
            val repo=CatalogRepository(db)
            runBlocking {repo.initialize();repo.saveProduct(ProductInput("p",null,"Товар",null,"2","1","10",null,true,"create"))}
            compose.runOnIdle {model=ViewModelProvider(store,object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=CatalogViewModel(repo) as T
            })[CatalogViewModel::class.java]}
            compose.setContent {MposTheme {CatalogScreen(model,{},{})}}
            compose.waitUntil(20_000) {!model.state.value.loading && model.state.value.products.isNotEmpty()}
            compose.onNodeWithTag("catalogProduct:p").performClick()
            compose.waitUntil(20_000) {!model.state.value.busy && model.state.value.draft!=null}
            compose.onNodeWithText("Удалить товар").performScrollTo().performClick()
            compose.onNodeWithText("Удаление товара").assertExists()
            runBlocking {Administration.session.logout()}
            compose.waitUntil(20_000) {model.state.value.draft==null}
            compose.onNodeWithText("Удаление товара").assertDoesNotExist()
            compose.onNodeWithTag("catalogProduct:p").assertIsNotEnabled()
            shot("admin-revoked-catalog")
            runBlocking {assertTrue(Administration.session.login("fixture-admin".toCharArray()))}
            compose.onNodeWithTag("productName").assertDoesNotExist()
            compose.onNodeWithText("Удаление товара").assertDoesNotExist()
            compose.onNodeWithText("Открыть сохранённый черновик").performClick()
            compose.waitUntil(20_000) {!model.state.value.busy && model.state.value.draft!=null}
            assertEquals("p",model.state.value.draft!!.id)
            compose.runOnIdle {model.discard()};compose.waitUntil(20_000) {!model.state.value.busy && model.state.value.draft==null}
            compose.onNodeWithText("Категории",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Создать категорию").performClick()
            compose.onNodeWithTag("categoryName").performTextInput("Не сохраняли")
            // No intermediate composition is required: generation, rather than just active=false, invalidates local state.
            runBlocking {Administration.session.logout();assertTrue(Administration.session.login("fixture-admin".toCharArray()))}
            compose.onNodeWithTag("categoryName").assertDoesNotExist()
            compose.onNodeWithText("Новая категория").assertDoesNotExist()
            assertEquals(0,runBlocking {db.catalog().categorySnapshot().size});assertEquals("Товар",runBlocking {db.catalog().product("p")!!.name})
        } finally {compose.runOnIdle {store.clear()};db.close()}
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation();val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file=File(instrumentation.targetContext.filesDir,"qa/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
