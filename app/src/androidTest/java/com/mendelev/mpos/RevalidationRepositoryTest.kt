package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RevalidationRepositoryTest {
    private fun preview(s: WorkspaceSnapshot)=CartRevalidator.preview(s.cart,s.products,s.graph,s.balances,OrderReservations.total(s.parked.map {it.cart}))
    @Test fun confirmIsAtomicRejectsChangedPreviewAndReplaysAfterOwnerChanged() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            val input=ProductInput("p",null,"Чай",null,"2","1","10",null,true,"create")
            catalog.saveProduct(input);val repo=WorkspaceRepository(db);repo.initialize();var s=repo.snapshot()
            repo.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",s.cart.sessionId)
            catalog.saveProduct(input.copy(expectedRevision=1,stockEdited=false,price="3",commandId="price"))
            s=repo.snapshot();val plan=preview(s);val original=s
            try {WorkspaceRepository(db) {throw IllegalStateException("disk")}.revalidate(s.cartRevision,s.cart.sessionId,plan.token,"fault");fail()} catch(_:IllegalStateException) {}
            assertEquals(original,repo.snapshot());assertNull(db.catalog().result("fault"))
            catalog.saveProduct(input.copy(expectedRevision=2,stockEdited=false,price="4",commandId="price-again"))
            try {repo.revalidate(s.cartRevision,s.cart.sessionId,plan.token,"stale");fail()} catch(_:CatalogFailure) {}
            assertEquals(original.cart,repo.snapshot().cart)
            s=repo.snapshot();val token=preview(s).token
            repo.revalidate(s.cartRevision,s.cart.sessionId,token,"confirm")
            assertEquals("4",repo.snapshot().cart.lines.single().unitPrice)
            assertEquals(original.cart.sessionId,repo.snapshot().cart.sessionId)
            assertEquals("10",db.catalog().balance("p")!!.quantity);assertEquals(1,db.catalog().movementCount("p"))
            val now=repo.snapshot();repo.park(now.cartRevision,now.cart.sessionId,"Стол","park")
            repo.revalidate(s.cartRevision,s.cart.sessionId,token,"confirm")
            assertTrue(repo.snapshot().cart.lines.isEmpty());assertEquals("4",repo.snapshot().parked.single().cart.lines.single().unitPrice)
        } finally {db.close()}
    }
    @Test fun competingConfirmationsHaveOneWinnerAndCommittedSnapshotSurvivesReopen() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="revalidate-${java.util.UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            val c=CatalogRepository(db);c.initialize();val input=ProductInput("p",null,"Чай",null,"2","1","10",null,true,"create")
            c.saveProduct(input);var repo=WorkspaceRepository(db);repo.initialize();var s=repo.snapshot()
            repo.cart(CartAction.Add("p",1,emptySet(),null,"line"),"add",s.cart.sessionId)
            c.saveProduct(input.copy(expectedRevision=1,stockEdited=false,price="3",commandId="change"))
            s=repo.snapshot();val token=preview(s).token
            val results=(1..2).map {n->async(Dispatchers.IO) {try {repo.revalidate(s.cartRevision,s.cart.sessionId,token,"confirm-$n");true} catch(_:CatalogFailure) {false}}}.awaitAll()
            assertEquals(1,results.count {it});val committed=repo.snapshot().cart
            db.close();db=open();repo=WorkspaceRepository(db);repo.initialize()
            assertEquals(committed,repo.snapshot().cart);assertEquals("3",committed.lines.single().unitPrice)
        } finally {db.close();context.deleteDatabase(name)}
    }

}
