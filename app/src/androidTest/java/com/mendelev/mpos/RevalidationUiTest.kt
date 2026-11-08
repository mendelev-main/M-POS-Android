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
    @Test fun catalogChangeAutomaticallyRefreshesOrderAndSurvivesRecreation() {
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
        compose.waitUntil(20_000) {model.state.value.snapshot?.cart?.lines?.singleOrNull()?.unitPrice=="3"}
        compose.onNodeWithTag("workspaceRevalidate").assertDoesNotExist()
        val before=runBlocking {repo.snapshot()}
        assertFalse(CartEngine.stale(before.cart.lines.single(),before.products))
        assertEquals("3",before.cart.lines.single().unitPrice)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {model.state.value.snapshot?.cart?.lines?.singleOrNull()?.unitPrice=="3"}
        compose.onNodeWithTag("workspaceRevalidate").assertDoesNotExist()
        assertEquals(before,runBlocking {repo.snapshot()})
        assertEquals("10",runBlocking {CatalogDatabase.get(compose.activity.applicationContext).catalog().balance(id)!!.quantity})
    }
}
