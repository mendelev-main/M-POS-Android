package com.mendelev.mpos

import androidx.room.Room
import androidx.lifecycle.*
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.feature.sale.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SaleViewModelTest {
    @Test fun cardDeliveryCashAdmissionWriteFailureRetryAndMixedDiscountMapping(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build();val store=ViewModelStore()
        try {
            val c=CatalogRepository(db);c.initialize();c.saveProduct(ProductInput("p",null,"Блюдо",null,"20","1","10",null,true,"create"))
            val w=WorkspaceRepository(db);w.initialize();val shifts=ShiftRepository(db);shifts.initialize();val initial=shifts.snapshot();shifts.open("open",0,null,initial.ownerId)
            suspend fun add(command: String) {w.cart(CartAction.Add("p",1,emptySet(),null,"line-$command"),command,w.snapshot().cart.sessionId)}
            add("first");var current=w.snapshot();w.cart(CartAction.SetDetails(OrderDetails(type="delivery"),current.cartRevision),"delivery",current.cart.sessionId)
            val model=withContext(Dispatchers.Main) {ViewModelProvider(store,object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=SaleViewModel(db) as T
            })[SaleViewModel::class.java]}
            suspend fun ready(): SaleUi=withTimeout(20_000) {model.state.first {!it.loading && !it.pending}}
            ready()
            withContext(Dispatchers.Main) {model.edit {it.copy(mode="card",tariff="Курьер",fee="5")};model.prepare()}
            assertNotNull(ready().message);assertNull(model.state.value.preview);assertNull(CheckoutRepository(db).load())
            val active=shifts.snapshot();shifts.close("close",active.active!!.id,active.revision,ShiftRules.token(active),500,active.ownerId)
            val closed=shifts.snapshot();shifts.open("next",closed.revision,closed.last!!.id,closed.ownerId)
            withContext(Dispatchers.Main) {model.refresh()};ready()
            withContext(Dispatchers.Main) {model.edit {it.copy(mode="card",tariff="Курьер",fee="5")};model.prepare()}
            val prepared=ready();assertEquals(2500L,prepared.quote!!.total);assertEquals(0L,prepared.payment!!.expectedCashAfter)
            val command=prepared.preview!!.draft.commandId
            db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER refuse_sale BEFORE INSERT ON receipts BEGIN SELECT RAISE(ABORT, 'injected'); END")
            withContext(Dispatchers.Main) {model.commit()};val failed=ready();assertNull(failed.completed);assertNotNull(failed.message)
            assertEquals(command,CheckoutRepository(db).load()!!.draft.commandId);assertEquals("10",db.catalog().balance("p")!!.quantity)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refuse_sale")
            withContext(Dispatchers.Main) {model.prepare()};assertEquals(command,ready().preview!!.draft.commandId)
            withContext(Dispatchers.Main) {model.commit()};val completed=ready().completed!!
            assertEquals(2500L,completed.second.payment.card);assertEquals(0L,shifts.snapshot().currentCash)
            withContext(Dispatchers.Main) {model.finish {}};ready()
            add("second")
            withContext(Dispatchers.Main) {model.refresh()};ready()
            val line=w.snapshot().cart.lines.single().id
            withContext(Dispatchers.Main) {
                model.edit {it.copy(discounts=mapOf(line to DiscountField("percent","10")))}
                model.equal(3)
                model.edit {it.copy(parts=it.parts.mapIndexed {i,p->if(i==0) p.copy(method="card") else p})}
                model.prepare()
            }
            val mixed=ready();assertEquals(1800L,mixed.quote!!.total);assertEquals(600L,mixed.payment!!.card);assertEquals(1200L,mixed.payment.cash)
            withContext(Dispatchers.Main) {model.commit()};assertNotNull(ready().completed)
            assertEquals(1200L,shifts.snapshot().currentCash);assertEquals(2,db.sales().history().size);assertEquals("8",db.catalog().balance("p")!!.quantity)
        } finally {withContext(Dispatchers.Main) {store.clear()};db.close()}
    }
}
