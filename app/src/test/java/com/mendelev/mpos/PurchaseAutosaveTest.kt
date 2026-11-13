package com.mendelev.mpos

import androidx.lifecycle.ViewModelStore
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.purchasing.PurchaseViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test

/** Executes real ViewModel/codec on JVM with fake persistence; no Android or application data. */
@OptIn(ExperimentalCoroutinesApi::class,DelicateCoroutinesApi::class,ExperimentalStdlibApi::class)
class PurchaseAutosaveTest {
    private class Fake: PurchaseAccess {
        var snapshot=PurchaseSnapshot(0,"owner",null,listOf(Supplier("s","Поставка",1,setOf("p"))),listOf(PurchaseProduct("p","Молоко",1,"l","","10","bottle","1")))
        val commands=mutableListOf<PurchaseCommand>()
        val outcomes=mutableMapOf<String,Pair<String,String>>()
        var gate: CompletableDeferred<Unit>?=null
        var loseReply=false;var failOutcome=false
        var failedOutcomeGate: CompletableDeferred<Unit>?=null
        var failedOutcomeEntered: CompletableDeferred<Unit>?=null
        override suspend fun snapshot()=snapshot
        override suspend fun outcome(command: PurchaseCommand): String? {
            if(failOutcome) {failedOutcomeEntered?.complete(Unit);failedOutcomeGate?.await();error("fixture unavailable")}
            return outcomes[command.commandId]?.let {require(it.first==command.fingerprint());it.second}
        }
        override suspend fun commit(command: PurchaseCommand): String {
            gate?.await();command.validate()
            outcomes[command.commandId]?.let {require(it.first==command.fingerprint());return it.second}
            require(command.revision==snapshot.revision) {"fixture revision conflict"}
            val d=when(val action=command.action) {is PurchaseAction.Working->action.draft;is PurchaseAction.Save->action.draft;is PurchaseAction.Place->null;PurchaseAction.Clear->null}
            val order=(command.action as? PurchaseAction.Place)?.let {PurchaseOrderRules.create(it.orderId,it.draft,1)}
            val encoded=if(command.action is PurchaseAction.Working) PurchaseDraftCodec.encodeWorking(d) else PurchaseDraftCodec.encode(d)
            snapshot=snapshot.copy(revision=snapshot.revision+1,draft=PurchaseDraftCodec.decode(encoded),orders=snapshot.orders+listOfNotNull(order))
            val id=order?.id ?: d?.id ?: "purchase-draft"
            commands+=command;outcomes[command.commandId]=command.fingerprint() to id
            if(loseReply) {loseReply=false;failOutcome=true;error("fixture committed; acknowledgement lost")}
            return id
        }
    }
    private suspend fun saved(model: PurchaseViewModel)=withTimeout(5000) {model.state.first {it.snapshot!=null && !it.pending && !it.saving && !it.dirty && it.autoError==null}}
    private fun fixture(block: suspend (Fake,PurchaseViewModel,CoroutineDispatcher)->Unit)=runBlocking {
        val main=newSingleThreadContext("purchase-autosave-test")
        Dispatchers.setMain(main);val store=ViewModelStore()
        try {
            val fake=Fake();val model=withContext(main) {PurchaseViewModel(fake).also {store.put("fixture",it)}}
            saved(model);block(fake,model,main)
        } finally {withContext(main) {store.clear()};Dispatchers.resetMain();main.close()}
    }
    @Test fun latestInputWinsWhileWriteIsSlowAndDoesNotBlockFurtherEditing()=fixture {fake,model,main->
        fake.gate=CompletableDeferred()
        withContext(main) {model.supplier("s")}
        withTimeout(5000) {model.state.first {it.saving}}
        assertFalse(model.state.value.pending);assertTrue(model.state.value.canEdit)
        withContext(main) {model.line("p") {it.copy(quantity="2",packSize="0")};model.line("p") {it.copy(quantity="3",packSize="0,")}}
        assertEquals("3",model.state.value.form!!.lines.single().quantity)
        fake.gate!!.complete(Unit);saved(model)
        assertEquals("0,",fake.snapshot.draft!!.lines.single().packSize)
        assertEquals(model.state.value.form,PurchaseDraftCodec.decode(PurchaseDraftCodec.encodeWorking(fake.snapshot.draft)))
        assertTrue(fake.snapshot.orders.isEmpty())
    }
    @Test fun touchingProductIncludesOneAndEditingPackIncludesWithoutReplacingPositiveQuantity()=fixture {fake,model,main->
        withContext(main) {model.supplier("s");model.activate("p")};saved(model)
        assertEquals("1",fake.snapshot.draft!!.lines.single().quantity)
        assertTrue(PurchaseDraftRules.included(fake.snapshot.draft!!.lines.single()))
        withContext(main) {model.activate("p");model.line("p") {it.copy(quantity="3")};model.activate("p")};saved(model)
        assertEquals(1,fake.snapshot.draft!!.lines.size)
        assertEquals("3",fake.snapshot.draft!!.lines.single().quantity)
        withContext(main) {model.line("p") {it.copy(quantity="0")}};saved(model)
        assertFalse(PurchaseDraftRules.included(fake.snapshot.draft!!.lines.single()))
        withContext(main) {model.line("p") {it.copy(packSize="500",contentUnit="ml")}};saved(model)
        val line=fake.snapshot.draft!!.lines.single()
        assertEquals("1",line.quantity);assertEquals("500",line.packSize);assertEquals("ml",line.contentUnit)
        assertEquals(0,java.math.BigDecimal("0.5").compareTo(PurchaseDraftRules.expected(line)!!))
        assertTrue(fake.snapshot.orders.isEmpty())
    }
    @Test fun formingWaitsForAutosaveAndNeverResurrectsOrDuplicatesOldRequest()=fixture {fake,model,main->
        fake.gate=CompletableDeferred()
        withContext(main) {model.supplier("s");model.note("Доставить утром");model.line("p") {it.copy(quantity="24",packSize="500",contentUnit="ml")};model.saveAndPlace();model.saveAndPlace()}
        fake.gate!!.complete(Unit)
        withTimeout(5000) {model.state.first {!it.pending && it.lastPlacedId!=null}}
        saved(model)
        assertEquals(1,fake.snapshot.orders.size)
        assertEquals("24",fake.snapshot.orders.single().source.lines.single().quantity)
        val order=fake.snapshot.orders.single()
        assertEquals("Доставить утром",PurchaseOrderCodec.decode(PurchaseOrderCodec.encode(listOf(order))).single().source.note)
        assertNull(fake.snapshot.draft);assertNull(model.state.value.form)
        assertEquals(1,fake.commands.count {it.action is PurchaseAction.Place})
    }
    @Test fun lostAcknowledgementReusesOriginalCommandBeforeSavingNewerInput()=fixture {fake,model,main->
        fake.loseReply=true
        withContext(main) {model.supplier("s")}
        withTimeout(5000) {model.state.first {it.autoError!=null && !it.saving}}
        assertFalse(model.state.value.pending)
        val first=fake.commands.single()
        // Retry arrives while a newer autosave is still resolving the old command.
        val gate=CompletableDeferred<Unit>();fake.failedOutcomeGate=gate
        val entered=CompletableDeferred<Unit>();fake.failedOutcomeEntered=entered
        withContext(main) {model.line("p") {it.copy(quantity="2")}}
        withTimeout(5000) {entered.await()}
        fake.failOutcome=false
        withContext(main) {model.retryAutosave()}
        gate.complete(Unit)
        saved(model)
        assertEquals(1,fake.commands.count {it.commandId==first.commandId})
        assertEquals("2",fake.snapshot.draft!!.lines.single().quantity)
    }
    @Test fun clearingSupplierIsAutomaticallyPersistedAndWorkingFormatDoesNotRelaxPlaceValidation()=fixture {fake,model,main->
        withContext(main) {model.supplier("s")};saved(model)
        withContext(main) {model.supplier("")};saved(model)
        assertNull(fake.snapshot.draft)
        val invalid=PurchaseDraft("d","s",1,"Поставка",listOf(PurchaseLine("p",1,"Молоко","l","3","bottle","0")))
        assertEquals(invalid,PurchaseDraftCodec.decode(PurchaseDraftCodec.encodeWorking(invalid)))
        try {PurchaseOrderRules.create("o",invalid,1);fail("invalid pack must not become an order")} catch(_:Exception) {}
        assertNotEquals(PurchaseCommand("c","owner",1,PurchaseAction.Working(invalid)).fingerprint(),PurchaseCommand("c","owner",1,PurchaseAction.Working(null)).fingerprint())
    }
}
