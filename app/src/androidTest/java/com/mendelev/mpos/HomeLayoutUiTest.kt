package com.mendelev.mpos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class HomeLayoutUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepare() {CatalogUiTestSupport.prepare(compose)}
    @Test fun longPressDragAccessibleMoveAndRecreatePreserveEmptySlots() {
        val suffix=UUID.randomUUID().toString();val p="home-p-$suffix";val a="home-a-$suffix";val q="home-q-$suffix"
        val repo=WorkspaceRepository(CatalogDatabase.get(compose.activity.applicationContext))
        fun state()=runBlocking {repo.snapshot()}
        runBlocking {
            val c=CatalogRepository(CatalogDatabase.get(compose.activity.applicationContext));c.initialize();repo.initialize()
            c.saveCategory(a,null,"Быстрый выбор","cat-$suffix")
            c.saveProduct(ProductInput(p,null,"Чай быстрый",a,"2","1","0",null,true,"product-$suffix",noStockTracking=true))
            c.saveProduct(ProductInput(q,null,"Чай второй",a,"2","1","0",null,true,"product-q-$suffix",noStockTracking=true))
            repo.home(HomeAction.Reset,state().homeRevision,"reset-$suffix")
            state().home.tiles.forEach {repo.home(HomeAction.Remove(it.type,it.id),state().homeRevision,"clear-${it.id}-$suffix")}
            repo.home(HomeAction.Add("category",a),state().homeRevision,"add-cat-$suffix")
            repo.home(HomeAction.Add("product",p),state().homeRevision,"add-p-$suffix")
        }
        try {
            compose.onNodeWithTag("topNav-workspace").performClick()
            compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceCategory:$a").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("workspaceEdit").performClick()
            val grid=compose.onNodeWithTag("workspaceGrid").fetchSemanticsNode().boundsInRoot
            val from=compose.onNodeWithTag("workspaceCategory:$a").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
            val to=compose.onNodeWithTag("homeSlot:3").fetchSemanticsNode().boundsInRoot.center-grid.topLeft
            compose.onNodeWithTag("workspaceGrid").performTouchInput {down(from);advanceEventTime(650);moveTo(to,delayMillis=300);up()}
            compose.waitUntil(20_000) {state().home.tiles.single {it.id==a}.slot==3}
            compose.onNodeWithTag("homeSlot:0").assertExists()
            compose.onNodeWithTag("workspaceHomeProduct:$p").performClick()
            compose.onNodeWithTag("homePosition").performTextReplacement("8")
            compose.runOnIdle {(compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
            compose.onNodeWithTag("homeMove").performClick()
            compose.waitUntil(20_000) {state().home.tiles.single {it.id==p}.slot==7}
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceHomeProduct:$p").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Место 8").assertExists()
            assertEquals(3,state().home.tiles.single {it.id==a}.slot)
            compose.onNodeWithText("Все категории").performClick()
            compose.onNodeWithTag("homeChoice:category:$a").performScrollTo().performClick()
            compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceProduct:$p").fetchSemanticsNodes().isNotEmpty()}
            val categoryGrid=compose.onNodeWithTag("workspaceGrid").fetchSemanticsNode().boundsInRoot
            val start=compose.onNodeWithTag("workspaceProduct:$p").fetchSemanticsNode().boundsInRoot.center-categoryGrid.topLeft
            val end=compose.onNodeWithTag("workspaceProduct:$q").fetchSemanticsNode().boundsInRoot.center-categoryGrid.topLeft
            compose.onNodeWithTag("workspaceGrid").performTouchInput {down(start);advanceEventTime(650);moveTo(end,delayMillis=300);up()}
            compose.waitUntil(20_000) {state().layout.filter {it.categoryId==a}.map {it.id}==listOf(q,p)}

        } finally {
            runBlocking {repo.home(HomeAction.Reset,state().homeRevision,"finish-$suffix")}
        }
    }
}
