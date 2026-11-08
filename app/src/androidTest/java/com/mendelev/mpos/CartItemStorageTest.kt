package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.SaleDiscount
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CartItemStorageTest {
    @Test fun editIsAtomicIdempotentAndSurvivesParkResumeAndCodec(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveProduct(ProductInput("p",null,"Чай",null,"10","1","3",null,true,"seed"))
            val repo=WorkspaceRepository(db);repo.initialize();var s=repo.snapshot()
            repo.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",s.cart.sessionId);s=repo.snapshot()
            val action=CartAction.SetOptions("line","2","Без сахара","staff",SaleDiscount("percent","10","Сотруднику"),s.cartRevision)
            repo.cart(action,"edit",s.cart.sessionId);repo.cart(action,"edit",s.cart.sessionId)
            val saved=repo.snapshot();val line=saved.cart.lines.single()
            assertEquals(s.cartRevision+1,saved.cartRevision);assertEquals("Без сахара",line.comment)
            assertEquals(line.discount,saved.sourceDiscounts["line"])
            assertEquals(saved.cart,WorkspaceCodec.cart(WorkspaceCodec.cart(saved.cart)))
            try {repo.cart(action.copy(comment="Иное"),"edit",saved.cart.sessionId);fail()} catch(e: CatalogFailure) {assertEquals("conflict",e.kind)}
            try {WorkspaceRepository(db) {error("disk fault")}.cart(action.copy(comment="Не записывать",expectedRevision=saved.cartRevision),"fault",saved.cart.sessionId);fail()} catch(_: IllegalStateException) {}
            assertEquals(saved.cart,repo.snapshot().cart);assertNull(db.catalog().result("fault"))
            repo.park(saved.cartRevision,saved.cart.sessionId,"Стол 1","park")
            s=repo.snapshot();val parked=s.parked.single()
            assertEquals(line,parked.cart.lines.single())
            repo.resume(parked.id,parked.revision,s.cartRevision,s.cart.sessionId,"resume")
            val restored=WorkspaceRepository(db).snapshot()
            assertEquals(line,restored.cart.lines.single());assertEquals(line.discount,restored.sourceDiscounts["line"])
            repo.cart(CartAction.SetOptions("line","2","","",null,restored.cartRevision),"clear-discount",restored.cart.sessionId)
            assertFalse(repo.snapshot().sourceDiscounts.containsKey("line"))
            val legacy=CurrentCart(sessionId="legacy",lines=listOf(line.copy(comment="",discount=null,discountId="",discountEdited=false)))
            val raw=WorkspaceCodec.cart(legacy)
            assertFalse(raw.contains("discountEdited"));assertEquals(legacy,WorkspaceCodec.cart(raw))
        } finally {db.close()}
    }
}
