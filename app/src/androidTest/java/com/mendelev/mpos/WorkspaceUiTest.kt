package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.workspace.WorkspaceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class WorkspaceUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepareCatalog() { CatalogUiTestSupport.prepare(compose) }
    private fun waitText(value: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val folder=File(compose.activity.filesDir,"qa").apply {mkdirs()}
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {bitmap ->
            File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    private fun assertPlatformHomeDescription(expected: String) {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        var diagnostic=""
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?,lines: MutableList<String>): Boolean {
            if(node==null) return false
            val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
            val description=node.contentDescription?.toString().orEmpty()
            if(lines.size<200) lines.add("${node.packageName}/${node.viewIdResourceName}: text=${node.text}; description=$description; visible=${node.isVisibleToUser}; bounds=$bounds")
            if(node.packageName?.toString()==BuildConfig.APPLICATION_ID && node.isVisibleToUser && bounds.width()>0 && bounds.height()>0 &&
                (description==expected || description.startsWith(expected+",") || description.startsWith(expected+"\n"))) return true
            for(index in 0 until node.childCount) if(find(node.getChild(index),lines)) return true
            return false
        }
        try {
            compose.waitUntil(10_000) {
                val lines=mutableListOf<String>();val found=find(automation.rootInActiveWindow,lines)
                diagnostic=lines.joinToString("\n");found
            }
        } catch(e:Exception) {throw AssertionError("Accessible home identity/slot missing: $expected\n$diagnostic",e)}
    }
    @Test fun folderConfigurationAndCommittedCartSurviveRecreation() {
        val suffix=UUID.randomUUID().toString().take(6);val category="workspace-category-$suffix";val coffee="workspace-coffee-$suffix";val milk="workspace-milk-$suffix"
        val name="Кофе POS $suffix";val folderName="Популярное $suffix"
        runBlocking {
            val db=CatalogDatabase.get(compose.activity.applicationContext);val repo=CatalogRepository(db);repo.initialize()
            repo.saveCategory(category,null,"Кофейня $suffix","ui-category-$suffix")
            repo.saveProduct(ProductInput(milk,null,"Молоко POS $suffix",null,"1","1","10",null,true,"ui-milk-$suffix",unit="l",displayUnit="l"))
            repo.saveProduct(ProductInput(coffee,null,name,category,"0","1","0",null,true,"ui-coffee-$suffix",noStockTracking=true,
                modifierGroups=listOf(ModifierGroupInput("milk-group","Молоко","1","1",listOf(ModifierOptionInput("milk-option",milk,"Молоко","0.1","0.5"))))))
            val work=WorkspaceRepository(db);work.initialize();work.cart(CartAction.Clear(work.snapshot().cartRevision),"ui-clear-$suffix",work.snapshot().cart.sessionId)
        }
        compose.onNodeWithTag("topNav-workspace").performClick();compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCartPanel").fetchSemanticsNodes().isNotEmpty()}
        lateinit var model: WorkspaceViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[WorkspaceViewModel::class.java]}
        compose.waitUntil(20_000) {model.state.value.snapshot?.categories?.any {it.id==category}==true}
        val index=model.state.value.snapshot!!.categories.indexOfFirst {it.id==category}
        compose.onNodeWithTag("workspaceGrid").performScrollToIndex(index)
        compose.onNodeWithTag("workspaceCategory:$category").performClick()
        compose.onNodeWithTag("workspaceEdit").performClick()
        compose.onNodeWithText("Создать папку").performClick()
        compose.onNodeWithTag("workspaceFolderName").performTextInput(folderName)
        compose.onNodeWithText("Сохранить папку").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.layout.any {it.type=="folder" && it.name==folderName}}
        val folder=model.state.value.snapshot!!.layout.first {it.type=="folder" && it.name==folderName}.id
        compose.onNodeWithTag("workspaceProduct:$coffee").performClick()
        compose.onNodeWithText("Корень категории").performClick();compose.onNode(hasText(folderName) and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Переместить").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.layout.any {it.id==coffee && it.folderId==folder}}
        compose.onNodeWithTag("workspaceEdit").performClick()
        compose.onNodeWithTag("workspaceFolder:$folder").performClick()
        compose.onNodeWithTag("workspaceProduct:$coffee").performClick()
        compose.onNodeWithTag("workspaceManualPrice").performTextInput("2,50")
        compose.onNodeWithTag("workspaceConfigurationTotal").assertTextEquals("Итого: 2,5 BYN")
        compose.onNodeWithText("Добавить в заказ").performClick()
        waitText("Проверьте выбор группы: Молоко")
        compose.onNodeWithContentDescription("Молоко (0,5 BYN)").performClick()
        compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.onNodeWithTag("workspaceConfigurationTotal").assertTextEquals("Итого: 3 BYN")
        screenshot("workspace-configuration")
        compose.onNodeWithText("Добавить в заказ").performClick()
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.cart.lines.size==1}
        compose.onNodeWithTag("workspaceFolderGrid").assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        val line=model.state.value.snapshot!!.cart.lines.single().id
        compose.onNodeWithTag("workspaceLine:$line").performClick()
        repeat(4) {compose.onNodeWithTag("workspacePlus:$line").performClick()}
        compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot!!.cart.lines.single().quantity=="5"}
        compose.onNodeWithTag("workspaceEditQuantity:$line").performClick()
        compose.onNodeWithTag("workspaceQuantity").performTextReplacement("0")
        compose.onNodeWithText("Применить количество").performClick();waitText("Количество должно быть больше нуля")
        compose.onNodeWithText("Отмена").performClick()
        assertEquals("5",model.state.value.snapshot!!.cart.lines.single().quantity)
        compose.onNodeWithTag("workspaceFolder:$folder").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("workspaceFolderGrid").assertExists();screenshot("workspace-folder")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("workspaceTotal").assertTextEquals("15,00 BYN")
        screenshot("workspace")
        File(compose.activity.filesDir,"qa/workspaceLineName.txt").writeText(name)
        val homeName="Быстрая плитка $suffix"
        runBlocking {
            val db=CatalogDatabase.get(compose.activity.applicationContext);val work=WorkspaceRepository(db)
            CatalogRepository(db).saveProduct(ProductInput("home-upgrade-$suffix",null,homeName,null,"1","0","0",null,true,"home-product-$suffix",noStockTracking=true))
            work.home(HomeAction.Reset,work.snapshot().homeRevision,"home-reset-$suffix")
            work.snapshot().home.tiles.forEach {work.home(HomeAction.Remove(it.type,it.id),work.snapshot().homeRevision,"home-remove-${it.id}-$suffix")}
            work.home(HomeAction.Add("product","home-upgrade-$suffix"),work.snapshot().homeRevision,"home-add-$suffix")
            work.home(HomeAction.Move("product","home-upgrade-$suffix",7),work.snapshot().homeRevision,"home-move-$suffix")
        }
        File(compose.activity.filesDir,"qa/homeTileName.txt").writeText(homeName)
        compose.onNodeWithTag("workspaceBack").performClick()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceHomeProduct:home-upgrade-$suffix").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("workspaceHomeProduct:home-upgrade-$suffix").assertContentDescriptionContains("$homeName · Место 8",substring=false)
        screenshot("workspace-home")
        assertPlatformHomeDescription("$homeName · Место 8")

        compose.onNodeWithTag("topNav-settings").performClick();compose.onNodeWithText("Товары").performClick();waitText("Добавить товар")
    }
}
