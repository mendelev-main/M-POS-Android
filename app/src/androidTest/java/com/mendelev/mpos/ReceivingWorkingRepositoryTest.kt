package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Own temporary Room file; never uses the user's database. */
class ReceivingWorkingRepositoryTest {
    @Test fun partialInvoiceSurvivesReopenAndReplayButNeverPosts()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="receiving-working-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            val seed=seedReceiving(db,"working")
            val before=db.catalog().product(seed.productId)!! to db.catalog().balance(seed.productId)!!
            val repo=ReceivingRepository(db);val s=repo.snapshot()
            val draft=ReceivingRules.open("working-invoice",seed.order,s.products).copy(working=true,invoiceDate="2026-",
                lines=ReceivingRules.open("working-invoice",seed.order,s.products).lines.map {it.copy(unit="box",packSize="0,",quantity="3")})
            val c=ReceivingCommand("autosave-invoice",s.ownerId,seed.order.id,0,draft)
            repo.commit(c);repo.commit(c)
            assertEquals(1L,repo.snapshot().records.single().revision)
            db.close();db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
            assertEquals(draft,ReceivingRepository(db).snapshot().records.single().draft)
            assertEquals(before,db.catalog().product(seed.productId)!! to db.catalog().balance(seed.productId)!!)
            try {ReceivingPostingRepository(db).commit(ReceivingPostCommand("invalid-post",s.ownerId,1,draft));fail()} catch(_:CatalogFailure) {}
            assertEquals("pending",PurchaseOrderStore(db).all().single().status)
            assertTrue(ReceivingPostingRepository(db).history().isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
