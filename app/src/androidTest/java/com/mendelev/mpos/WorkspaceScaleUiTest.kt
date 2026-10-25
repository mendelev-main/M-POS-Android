package com.mendelev.mpos

import android.content.Context
import android.app.UiAutomation
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.workspace.WorkspaceViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID

class WorkspaceScaleUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun prepare() {CatalogUiTestSupport.prepare(compose)}
    private fun hideKeyboard() {compose.runOnIdle {(compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use {it.readText()}
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val folder=File(compose.activity.filesDir,"qa").apply {mkdirs()}
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("No screenshot")
        File(folder,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun largeCatalogSearchAndAddEvaluateOnlyRequestedTiles() {
        val suffix=UUID.randomUUID().toString().take(6);val category="bench-$suffix";val target="bench-$suffix-1999"
        val name="Нагрузка $suffix 1999";val db=CatalogDatabase.get(compose.activity.applicationContext);val repo=WorkspaceRepository(db)
        runBlocking {
            repo.initialize();repo.cart(CartAction.Clear(repo.snapshot().cartRevision),"bench-clear-$suffix",repo.snapshot().cart.sessionId)
            CatalogRepository(db).saveCategory(category,null,"Нагрузка $suffix","bench-category-$suffix")
            db.withTransaction {
                repeat(2000) {n->
                    val id="bench-$suffix-$n";val label="Нагрузка $suffix ${n.toString().padStart(4,'0')}"
                    db.catalog().insertProduct(ProductEntity(id,category,label,CatalogRules.key(label),"2","1",1,noStockTracking=true))
                    db.catalog().insertBalance(BalanceEntity(id,"0",1))
                }
            }
        }
        try {
            val start=SystemClock.elapsedRealtime();compose.onNodeWithText("Рабочая зона").performClick()
            lateinit var model: WorkspaceViewModel
            compose.runOnIdle {model=ViewModelProvider(compose.activity)[WorkspaceViewModel::class.java]}
            compose.waitUntil(20_000) {model.state.value.snapshot?.products?.count {it.categoryId==category}==2000}
            val load=SystemClock.elapsedRealtime()-start
            val searchStart=SystemClock.elapsedRealtime()
            compose.onNodeWithTag("workspaceSearch").performTextInput(name);hideKeyboard()
            compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceProduct:$target").fetchSemanticsNodes().isNotEmpty()}
            val search=SystemClock.elapsedRealtime()-searchStart
            compose.waitUntil(20_000) {(model.state.value.availability?.evaluatedProducts ?: 0)>0}
            assertTrue("Must not calculate the whole catalog",model.state.value.availability!!.evaluatedProducts<32)
            val addStart=SystemClock.elapsedRealtime();compose.onNodeWithTag("workspaceProduct:$target").performClick()
            compose.waitUntil(20_000) {model.state.value.pending==0 && model.state.value.snapshot?.cart?.lines?.singleOrNull()?.productId==target}
            val add=SystemClock.elapsedRealtime()-addStart
            if(compose.onAllNodesWithTag("workspaceShowOrder").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("workspaceShowOrder").performClick()
            compose.onNodeWithTag("workspaceTotal").assertTextEquals("Предварительно: 2 BYN")
            assertTrue("Load exceeded CI responsiveness budget: $load",load<15_000)
            assertTrue("Search exceeded CI responsiveness budget: $search",search<15_000)
            assertTrue("Add exceeded CI responsiveness budget: $add",add<15_000)
            val metrics=JSONObject().put("products",2000).put("loadMs",load).put("searchMs",search).put("addMs",add)
            File(compose.activity.filesDir,"qa").apply {mkdirs()}.resolve("performance.json").writeText(metrics.toString())
        } finally {
            runBlocking {
                repo.cart(CartAction.Clear(repo.snapshot().cartRevision),"bench-finish-$suffix",repo.snapshot().cart.sessionId)
                db.withTransaction {
                    db.openHelper.writableDatabase.execSQL("DELETE FROM stock_balances WHERE productId IN (SELECT id FROM products WHERE categoryId = ?)",arrayOf(category))
                    db.openHelper.writableDatabase.execSQL("DELETE FROM products WHERE categoryId = ?",arrayOf(category))
                    db.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE id = ?",arrayOf(category))
                }
            }
        }
    }
    @Test fun portraitAndLandscapeAtLargeFontKeepOrderAndControlsAccessible() {
        require(Build.HARDWARE in setOf("ranchu","goldfish")) {"Font-scale fixture is emulator-only"}
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalRotation=compose.activity.display!!.rotation
        val original=Settings.System.getFloat(compose.activity.contentResolver,Settings.System.FONT_SCALE,1f)
        val suffix=UUID.randomUUID().toString();val id="scale-$suffix";val db=CatalogDatabase.get(compose.activity.applicationContext);val repo=WorkspaceRepository(db)
        runBlocking {
            repo.initialize();val c=CatalogRepository(db)
            c.saveProduct(ProductInput(id,null,"Крупный шрифт",null,"2","1","0",null,true,"scale-product-$suffix",noStockTracking=true))
            var s=repo.snapshot();repo.cart(CartAction.Clear(s.cartRevision),"scale-clear-$suffix",s.cart.sessionId);s=repo.snapshot()
            repo.cart(CartAction.Add(id,1,emptySet(),null,"scale-line-$suffix"),"scale-add-$suffix",s.cart.sessionId)
        }
        try {
            compose.onNodeWithText("Рабочая зона").performClick()
            check(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
            shell("settings put system font_scale 1.5")
            compose.waitUntil(20_000) {compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT && compose.activity.resources.configuration.fontScale>1.4f}
            compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceTotal").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("workspaceShowOrder").fetchSemanticsNodes().isNotEmpty()}
            if(compose.onAllNodesWithTag("workspaceShowOrder").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("workspaceShowOrder").performClick()
            compose.onNodeWithTag("workspaceTotal").assertTextEquals("Предварительно: 2 BYN").assertIsDisplayed()
            compose.onNodeWithTag("workspacePlus:scale-line-$suffix").performScrollTo().performClick()
            compose.waitUntil(20_000) {compose.onAllNodes(hasTestTag("workspaceTotal") and hasText("Предварительно: 4 BYN")).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("workspaceTotal").assertTextEquals("Предварительно: 4 BYN").assertIsDisplayed()
            compose.onNodeWithTag("workspaceParked").assertIsDisplayed()
            screenshot("workspace-portrait150")
            check(automation.setRotation(UiAutomation.ROTATION_FREEZE_0))
            compose.waitUntil(20_000) {compose.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            compose.waitUntil(20_000) {compose.onAllNodesWithTag("workspaceTotal").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("workspaceTotal").assertTextEquals("Предварительно: 4 BYN").assertIsDisplayed()
            compose.onNodeWithTag("workspaceParked").assertIsDisplayed()
            screenshot("workspace-landscape150")
        } finally {
            shell("settings put system font_scale $original")
            check(automation.setRotation(originalRotation))
            compose.waitUntil(20_000) {kotlin.math.abs(compose.activity.resources.configuration.fontScale-original)<0.01f && compose.activity.display!!.rotation==originalRotation}
            check(automation.setRotation(UiAutomation.ROTATION_UNFREEZE))
        }
    }
}
