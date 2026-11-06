package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CheckoutRepositoryTest {
    @Test fun draftReopensConflictsRollsBackAndDiscardKeepsCart(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="checkout-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            WorkspaceRepository(db).initialize();val shifts=ShiftRepository(db);shifts.initializeCashierFixture()
            val initial=shifts.snapshot();shifts.open("open",initial.revision,null,initial.ownerId)
            val shift=shifts.snapshot()
            val cart=CurrentCart(lines=listOf(CartLine("line","product","Товар","1",false,"1","2","piece",emptyList(),emptyList(),emptyMap())),sessionId="session")
            db.workspace().put(WorkspaceDocument("current",3,WorkspaceCodec.cart(cart)))
            val draft=CheckoutDraft("sale-command","session",3,shift.active!!.id,shift.revision,shift.ownerId,parts=listOf(PaymentInput("cash",200,500)))
            val repo=CheckoutRepository(db)
            try {CheckoutRepository(db) {throw java.io.IOException("disk")}.save(0,draft);fail()} catch(_:java.io.IOException) {}
            assertNull(repo.load());assertEquals(0L,repo.revision())
            val attempts=coroutineScope {(1..2).map {async(Dispatchers.IO) {runCatching {repo.save(0,draft)}}}.awaitAll()}
            assertEquals(1,attempts.count {it.isSuccess});assertEquals(draft,repo.load()!!.draft)
            db.close();db=open()
            val reopened=CheckoutRepository(db);assertEquals(SavedCheckout(1,draft),reopened.load())
            db.workspace().put(WorkspaceDocument("current",4,WorkspaceCodec.cart(cart.copy(details=OrderDetails(label="Изменён")))))
            try {reopened.save(1,draft);fail()} catch(_:CatalogFailure) {}
            assertEquals(draft,reopened.load()!!.draft)
            reopened.discard(1);assertNull(reopened.load());assertEquals(2L,reopened.revision())
            assertEquals("Изменён",WorkspaceCodec.cart(db.workspace().document("current")!!.payload).details.label)
            try {reopened.save(0,draft.copy(cartRevision=4));fail()} catch(_:CatalogFailure) {}
            val fresh=draft.copy(cartRevision=4)
            InstallationGate.prepare("checkout-test") { Unit }
            try {reopened.save(2,fresh);fail()} catch(_:IllegalStateException) {} finally {InstallationGate.release("checkout-test")}
            assertEquals(2L,reopened.revision())
            reopened.save(2,fresh);assertEquals(3L,reopened.load()!!.revision)
            val deliveryCart=cart.copy(details=OrderDetails(type="delivery"))
            db.workspace().put(WorkspaceDocument("current",5,WorkspaceCodec.cart(deliveryCart)))
            try {reopened.save(3,fresh.copy(cartRevision=5));fail()} catch(_:CatalogFailure) {}
            val free=fresh.copy(cartRevision=5,delivery=DeliveryCharge("free","Бесплатно",0,1))
            reopened.save(3,free);assertEquals(free,reopened.load()!!.draft)
            val active=ShiftRepository(db).snapshot()
            ShiftRepository(db).close("close",active.active!!.id,active.revision,ShiftRules.token(active),0,active.ownerId)
            try {reopened.save(4,free);fail()} catch(_:CatalogFailure) {}
            assertEquals(free,reopened.load()!!.draft)
            assertNull(db.catalog().result(draft.commandId)) // Preparing inputs is not a sale.
            assertEquals(0,db.catalog().allBalances().size)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
