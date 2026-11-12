package com.mendelev.mpos

import androidx.lifecycle.ViewModelStore
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.receiving.ReceivingViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class,DelicateCoroutinesApi::class,ExperimentalStdlibApi::class)
class ReceivingAutosaveTest {
    private class Fake: ReceivingAccess {
        val product=ReceivingProduct("p","Молоко","l",1,1,"10","2",false)
        val order=PurchaseOrderRules.create("o",PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","10","l"))),1)
        var data=ReceivingSnapshot("owner",listOf(order),emptyList(),listOf(product))
        val commands=mutableListOf<ReceivingCommand>()
        val outcomes=mutableMapOf<String,Pair<String,String>>()
        var gate: CompletableDeferred<Unit>?=null
        var loseReply=false;var failOutcome=false;var failWrite=false
        override suspend fun snapshot()=data
        override suspend fun outcome(c: ReceivingCommand): String? {
            if(failOutcome) error("fixture offline")
            return outcomes[c.commandId]?.let {require(it.first==c.fingerprint());it.second}
        }
        override suspend fun commit(c: ReceivingCommand): String {
            gate?.await();if(failWrite) error("fixture write failed")
            c.validate()
            outcomes[c.commandId]?.let {require(it.first==c.fingerprint());return it.second}
            require((data.records.find {it.orderId==c.orderId}?.revision ?: 0)==c.revision)
            val row=ReceivingRecord(c.orderId,c.revision+1,c.draft)
            data=data.copy(records=ReceivingCodec.decode(ReceivingCodec.encode(data.records.filterNot {it.orderId==c.orderId}+row)))
            val id=c.draft?.id ?: c.orderId;outcomes[c.commandId]=c.fingerprint() to id;commands+=c
            if(loseReply) {loseReply=false;failOutcome=true;error("fixture lost reply")}
            return id
        }
    }
    private suspend fun saved(m: ReceivingViewModel)=withTimeout(5000) {m.state.first {!it.pending && !it.saving && !it.dirty && it.autoError==null}}
    private fun fixture(seed: (Fake)->Unit={},block: suspend (Fake,ReceivingViewModel,CoroutineDispatcher)->Unit)=runBlocking {
        val main=newSingleThreadContext("receiving-autosave-test");Dispatchers.setMain(main);val store=ViewModelStore()
        try {
            val f=Fake().also(seed);val m=withContext(main) {ReceivingViewModel(f).also {store.put("fixture",it)}}
            withTimeout(5000) {m.state.first {it.snapshot!=null && !it.pending}}
            withContext(main) {m.open("o")};saved(m)
            block(f,m,main)
        } finally {withContext(main) {store.clear()};Dispatchers.resetMain();main.close()}
    }
    @Test fun partialInputSurvivesCodecAndExitButCannotBePosted()=fixture {f,m,main->
        withContext(main) {m.change {it.copy(invoiceDate="2026-")};m.line("p") {it.copy(unit="box",packSize="0,",quantity="3")}}
        saved(m)
        val saved=f.data.records.single().draft!!
        assertEquals("2026-",saved.invoiceDate);assertEquals("0,",saved.lines.single().packSize)
        try {ReceivingPostingRules.preview(saved,saved.order,f.data.products);fail()} catch(_:Exception) {}
        withContext(main) {m.saveAndExit()}
        withTimeout(5000) {m.state.first {it.form==null && !it.pending}}
        withContext(main) {m.open("o")};saved(m)
        assertEquals(saved,m.state.value.form)
        assertEquals("pending",f.data.orders.single().status);assertEquals("10",f.data.products.single().stock)
    }
    @Test fun slowWriteKeepsEditingAvailableAndLatestInputWins()=fixture {f,m,main->
        f.gate=CompletableDeferred()
        withContext(main) {m.line("p") {it.copy(total="40")}}
        withTimeout(5000) {m.state.first {it.saving}}
        assertFalse(m.state.value.pending)
        withContext(main) {m.line("p") {it.copy(quantity="7")};m.line("p") {it.copy(quantity="9")}}
        f.gate!!.complete(Unit);saved(m)
        assertEquals("9",f.data.records.single().draft!!.lines.single().quantity)
    }
    @Test fun lostReplyReplaysOriginalCommandBeforeSavingNewInput()=fixture {f,m,main->
        f.loseReply=true
        withContext(main) {m.line("p") {it.copy(total="40")}}
        withTimeout(5000) {m.state.first {!it.saving && it.autoError!=null}}
        val first=f.commands.last()
        withContext(main) {m.line("p") {it.copy(quantity="8")}}
        withTimeout(5000) {m.state.first {!it.saving}}
        f.failOutcome=false
        withContext(main) {m.retryAutosave()};saved(m)
        assertEquals(1,f.commands.count {it.commandId==first.commandId})
        assertEquals("8",f.data.records.single().draft!!.lines.single().quantity)
        assertNull(m.state.value.unresolved)
    }
    @Test fun nextWaitsForWriteAndRefreshesEconomicBaselineWithoutPosting()=fixture {f,m,main->
        f.gate=CompletableDeferred()
        withContext(main) {m.line("p") {it.copy(total="40")};m.next()}
        f.data=f.data.copy(products=listOf(f.product.copy(stock="20",stockRevision=2)))
        f.gate!!.complete(Unit)
        val ui=withTimeout(5000) {m.state.first {!it.pending && it.preview!=null}}
        assertEquals("20",ui.preview!!.lines.single().oldStock)
        assertEquals(4000L,ui.preview!!.totalMinor);assertFalse(ui.dirty)
        assertEquals("pending",f.data.orders.single().status)
    }
    @Test fun currentCostOverridesOldPurchasePriceAndManualAmountSurvivesExit()=fixture(seed={f->
        val draft=ReceivingDraft("previous",null,lines=listOf(ReceivingLine(f.product,"2","box","500","ml","6")),supplier=ReceivingSupplier("s","Поставка",1))
        f.data=f.data.copy(receipts=listOf(PostedReceiving("previous","previous-command","owner",0,1,draft,false)))
    }) {f,m,main->
        assertEquals("20.00",m.state.value.form!!.lines.single().total)
        assertEquals(setOf("p"),m.state.value.suggestedAmounts)
        withContext(main) {m.amount("p","20.00");m.line("p") {it.copy(quantity="3",unit="box",packSize="500",contentUnit="ml")}}
        saved(m);assertEquals("3.00",m.state.value.form!!.lines.single().total)
        assertEquals(setOf("p"),m.state.value.suggestedAmounts)
        withContext(main) {m.amount("p","10");m.line("p") {it.copy(quantity="4")}}
        saved(m);assertEquals("10",m.state.value.form!!.lines.single().total)
        assertTrue(m.state.value.suggestedAmounts.isEmpty())
        withContext(main) {m.saveAndExit()};withTimeout(5000) {m.state.first {it.form==null && !it.pending}}
        withContext(main) {m.open("o")};saved(m)
        assertEquals("10",m.state.value.form!!.lines.single().total)
        assertEquals("total",m.state.value.form!!.lines.single().priceMode)
        assertEquals("10",f.data.products.single().stock)
    }
    @Test fun failedSaveCannotCloseFormOrPretendItIsDurable()=fixture {f,m,main->
        f.failWrite=true
        withContext(main) {m.line("p") {it.copy(total="40")};m.saveAndExit()}
        withTimeout(5000) {m.state.first {!it.pending && !it.saving && it.autoError!=null}}
        assertNotNull(m.state.value.form);assertTrue(m.state.value.dirty)
        assertEquals("20.00",f.data.records.single().draft!!.lines.single().total)
        f.failWrite=false
        withContext(main) {m.retryAutosave()};saved(m)
        assertEquals("40",f.data.records.single().draft!!.lines.single().total)
    }
}
