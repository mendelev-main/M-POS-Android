package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.domain.sale.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ShiftCashRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun memory()=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
    private suspend fun open(db: CatalogDatabase): ShiftRepository {val r=ShiftRepository(db);r.initialize();val s=r.snapshot();r.open("open",s.revision,null,s.ownerId);return r}
    private suspend fun command(r: ShiftRepository,id: String="deposit",type: String="deposit",amount: Long=10000): ShiftCashCommand {val s=r.snapshot();return ShiftCashCommand(id,s.active!!.id,s.revision,s.ownerId,type,amount,"Размен")}
    @Test fun manualCashCombinesWithRealSaleAndRefundWithoutDoubleCounting(): Unit=runBlocking {
        val db=memory()
        try {
            standaloneInvoice(db,"cash");val shifts=open(db);val cash=ShiftCashRepository(db);cash.commit(command(shifts))
            val work=WorkspaceRepository(db);work.initialize();work.cart(CartAction.Add("p-cash",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
            val s=shifts.snapshot();val w=work.snapshot();val saved=CheckoutRepository(db).save(0,CheckoutDraft("sale-cash",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,s.ownerId,parts=listOf(PaymentInput("cash",500,1000))))
            val receipt=SaleRepository(db).commit(saved);assertEquals(10500L,shifts.snapshot().currentCash)
            val refunds=RefundRepository(db);refunds.commit("refund",refunds.preview(receipt,s.ownerId));assertEquals(10000L,shifts.snapshot().currentCash)
            cash.commit(command(shifts,"withdraw","withdrawal",500));assertEquals(9500L,shifts.snapshot().currentCash);assertEquals(2,cash.history(s.active!!.id).size)
            val before=shifts.snapshot();shifts.close("close",before.active!!.id,before.revision,ShiftRules.token(before),9400,before.ownerId)
            val closed=shifts.snapshot();assertEquals(9500L,closed.last!!.expectedCash);assertEquals(-100L,closed.last!!.difference)
            shifts.open("next",closed.revision,closed.last!!.id,closed.ownerId);assertEquals(9400L,shifts.snapshot().currentCash)
        } finally {db.close()}
    }
    @Test fun eachFourWritesSqlAbortAndLostReplyRollBackOrReplayOnce(): Unit=runBlocking {
        val db=memory()
        try {
            val shifts=open(db);val c=command(shifts);val before=shifts.snapshot();val docs=db.workspace().documents()
            for(n in 1..4) {var writes=0;assertTrue(runCatching {ShiftCashRepository(db,afterWrite={if(++writes==n) error("fail")}).commit(c)}.isFailure);assertEquals(before,shifts.snapshot());assertEquals(docs,db.workspace().documents());assertNull(db.catalog().result(c.commandId))}
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_cash BEFORE INSERT ON audit_events WHEN NEW.action='shift.cash.deposit' BEGIN SELECT RAISE(ABORT,'test'); END")
            assertTrue(runCatching {ShiftCashRepository(db).commit(c)}.isFailure);assertEquals(before,shifts.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_cash")
            assertTrue(runCatching {ShiftCashRepository(db,afterCommit={error("reply")}).commit(c)}.isFailure)
            val cash=ShiftCashRepository(db);assertEquals(cash.outcome(c),cash.commit(c));assertEquals(10000L,shifts.snapshot().currentCash);assertEquals(1,cash.history(c.shiftId).size)
        } finally {db.close()}
    }
    @Test fun ownerGateStaleClosedInsufficientAndConcurrentWithdrawAreGuarded(): Unit=runBlocking {
        val db=memory()
        try {
            val shifts=open(db);val cash=ShiftCashRepository(db);val c=command(shifts)
            assertTrue(runCatching {cash.commit(c.copy(actorId="cashier"))}.isFailure)
            InstallationGate.prepare("cash") {};try {assertTrue(runCatching {cash.commit(c)}.isFailure)} finally {InstallationGate.release("cash")}
            cash.commit(c);assertTrue(runCatching {cash.commit(c.copy(commandId="stale"))}.isFailure)
            assertTrue(runCatching {cash.commit(command(shifts,"too-much","withdrawal",10001))}.isFailure)
            val withdraw=command(shifts,"w","withdrawal",8000)
            val race=coroutineScope {(1..2).map {i->async(Dispatchers.IO) {runCatching {cash.commit(withdraw.copy(commandId="w$i"))}}}.awaitAll()}
            assertEquals(1,race.count {it.isSuccess});assertEquals(2000L,shifts.snapshot().currentCash)
            val s=shifts.snapshot();shifts.close("close",s.active!!.id,s.revision,ShiftRules.token(s),2000,s.ownerId)
            assertEquals(cash.outcome(c),cash.commit(c));assertTrue(runCatching {cash.commit(withdraw.copy(commandId="closed",revision=shifts.snapshot().revision))}.isFailure)
        } finally {db.close()}
    }
    @Test fun cashMovementInvalidatesClosePreviewAndCorruptJournalIsNotReset(): Unit=runBlocking {
        val db=memory()
        try {
            val shifts=open(db);val before=shifts.snapshot();val c=command(shifts);ShiftCashRepository(db).commit(c)
            assertTrue(runCatching {shifts.close("stale-close",before.active!!.id,before.revision,ShiftRules.token(before),0,before.ownerId)}.isFailure)
            val doc=db.workspace().document(ShiftCashCodec.KEY)!!;db.workspace().put(doc.copy(payload="broken"))
            assertTrue(runCatching {shifts.snapshot()}.isFailure);assertEquals("broken",db.workspace().document(ShiftCashCodec.KEY)!!.payload)
        } finally {db.close()}
    }
    @Test fun unfinishedSplitBlocksManualCashWithoutChangingJournalOrRegister(): Unit=runBlocking {
        val db=memory()
        try {
            standaloneInvoice(db,"cash-split");val shifts=open(db)
            val work=WorkspaceRepository(db);work.initialize();work.cart(CartAction.Add("p-cash-split",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
            val s=shifts.snapshot();val w=work.snapshot()
            val saved=CheckoutRepository(db).save(0,CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,s.ownerId,parts=listOf(PaymentInput("card",250),PaymentInput("cash",250,250))))
            SplitPaymentRepository(db).confirm(saved,0,0)
            val before=shifts.snapshot();val docs=db.workspace().documents()
            assertTrue(runCatching {ShiftCashRepository(db).commit(command(shifts))}.isFailure)
            assertEquals(before,shifts.snapshot());assertEquals(docs,db.workspace().documents());assertNull(db.workspace().document(ShiftCashCodec.KEY))
        } finally {db.close()}
    }

}
