package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.workspace.CartAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceLiveDocumentsTest {
    @Test fun archivedPayloadsStayUntouchedWhileObservedParkedOrderResumes(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val repo=WorkspaceRepository(db);repo.initialize()
            CatalogRepository(db).saveProduct(ProductInput("p",null,"Чай",null,"2","1","10",null,true,"create"))
            var s=repo.snapshot();repo.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",s.cart.sessionId)
            s=repo.snapshot();repo.park(s.cartRevision,s.cart.sessionId,"Заказ","park")
            val payload="x".repeat(64*1024)
            repeat(256) {db.workspace().put(WorkspaceDocument("web-original-source:fixture-$it",1,payload))}
            val observed=repo.observe().stateIn(scope,SharingStarted.Eagerly,null)
            scope.launch {CatalogRepository(db).recipeBatchIds.collect {}}
            scope.launch {SettingsRepository(db).observe().collect {}}
            val before=withTimeout(15_000) {observed.filterNotNull().first()};val target=before.parked.single()
            repo.resume(target.id,target.revision,before.cartRevision,before.cart.sessionId,"resume")
            val after=withTimeout(15_000) {observed.filterNotNull().first {it.cart.sessionId==target.id}}
            assertEquals(target.cart,after.cart);assertTrue(after.parked.isEmpty())
            assertEquals("10",db.catalog().balance("p")!!.quantity)
            assertEquals(payload,db.workspace().document("web-original-source:fixture-255")!!.payload)
            assertTrue(db.workspace().documentsByPrefix("parked:").isEmpty())
        } finally {scope.cancel();scope.coroutineContext.job.join();db.close()}
    }
}
