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
import java.util.UUID

class RevalidationUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepare() {CatalogUiTestSupport.prepare(compose)}
    @Test fun reviewCancelRecreateAndConfirmKeepOrderUntilCommit() {
        val id="revalidate-${UUID.randomUUID()}";val line="line-$id"
        val repo=WorkspaceRepository(CatalogDatabase.get(compose.activity.applicationContext))
        runBlocking {
            val c=CatalogRepository(CatalogDatabase.get(compose.activity.applicationContext));c.initialize();repo.initialize()
            val input=ProductInput(id,null,"Чай пересчёт",null,"2","1","10",null,true,"create-$id")
            c.saveProduct(input);var s=repo.snapshot()
            repo.cart(CartAction.Clear(s.cartRevision),"clear-$id",s.cart.sessionId);s=repo.snapshot()
            repo.cart(CartAction.Add(id,1,emptySet(),null,line),"add-$id",s.cart.sessionId)
            c.saveProduct(input.copy(expectedRevision=1,stockEdited=false,price="3",commandId="price-$id"))
        }
        compose.onNodeWithTag("topNav-workspace").performClick()
        lateinit var model: WorkspaceViewModel
        compose.runOnIdle {model=ViewModelProvider(compose.activity)[WorkspaceViewModel::class.java]}
        compose.waitUntil(20_000) {model.state.value.snapshot?.cart?.lines?.singleOrNull()?.id==line}
        compose.onNodeWithTag("workspaceRevalidate").performClick()
        compose.onNodeWithTag("revalidationTotal").assertTextEquals("Сумма: 2 → 3 BYN")
        compose.onNodeWithText("Отмена").performClick()
        assertEquals("2",runBlocking {repo.snapshot()}.cart.lines.single().unitPrice)
        compose.onNodeWithTag("workspaceRevalidate").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {compose.onAllNodesWithTag("revalidationConfirm").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("revalidationTotal").assertTextEquals("Сумма: 2 → 3 BYN")
        compose.onNodeWithTag("revalidationConfirm").performClick()
        compose.waitUntil(20_000) {runBlocking {repo.snapshot()}.cart.lines.single().unitPrice=="3"}
        compose.onNodeWithTag("workspaceRevalidate").assertDoesNotExist()
    }
}
