package com.mendelev.mpos

import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.products.*
import com.mendelev.mpos.feature.suppliers.*
import com.mendelev.mpos.feature.staff.*
import com.mendelev.mpos.feature.purchasing.*
import com.mendelev.mpos.feature.receiving.*
import com.mendelev.mpos.feature.inventory.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AdminFormViewModelTest {
    private suspend fun waitFor(condition: ()->Boolean)=withTimeout(20_000) {while(!condition()) delay(10)}
    private suspend fun relogin() {Administration.session.logout();assertTrue(Administration.session.login("fixture-admin".toCharArray()))}
    @Test fun everyAdministrativeEditorDropsTransientInputButKeepsDurableDraftsAndSnapshots(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build();val store=ViewModelStore()
        try {
            val catalog=CatalogRepository(db);catalog.initialize();catalog.saveProduct(ProductInput("p",null,"Товар",null,"2","1","10",null,true,"product"))
            val supplier=SupplierRepository(db);val owner=supplier.snapshot().ownerId
            supplier.commit(SupplierCommand("supplier",owner,SupplierAction.Save("s",null,"Поставщик",setOf("p"))))
            val inventory=InventoryRepository(db);inventory.snapshot()
            inventory.commit(InventoryCommand("config",owner,0,InventoryAction.Configure(InventoryConfig(true,"monthly",setOf("p")))))
            inventory.commit(InventoryCommand("start",owner,1,InventoryAction.Start("inventory","adhoc")))
            lateinit var products: CatalogViewModel;lateinit var suppliers: SupplierViewModel;lateinit var staff: EmployeeViewModel
            lateinit var purchase: PurchaseViewModel;lateinit var receiving: ReceivingViewModel;lateinit var counts: InventoryViewModel
            withContext(Dispatchers.Main) {
                val provider=ViewModelProvider(store,object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=when(modelClass) {
                        CatalogViewModel::class.java->CatalogViewModel(catalog)
                        SupplierViewModel::class.java->SupplierViewModel(supplier)
                        EmployeeViewModel::class.java->EmployeeViewModel(EmployeeRepository(db))
                        PurchaseViewModel::class.java->PurchaseViewModel(PurchaseDraftRepository(db))
                        ReceivingViewModel::class.java->ReceivingViewModel(ReceivingRepository(db))
                        else->InventoryViewModel(inventory)
                    } as T
                })
                products=provider[CatalogViewModel::class.java];suppliers=provider[SupplierViewModel::class.java];staff=provider[EmployeeViewModel::class.java]
                purchase=provider[PurchaseViewModel::class.java];receiving=provider[ReceivingViewModel::class.java];counts=provider[InventoryViewModel::class.java]
            }
            waitFor {!products.state.value.loading && !suppliers.state.value.pending && !staff.state.value.pending && !purchase.state.value.pending && !receiving.state.value.pending && !counts.state.value.pending}
            withContext(Dispatchers.Main) {products.edit()};waitFor {!products.state.value.busy && products.state.value.draft!=null}
            withContext(Dispatchers.Main) {products.change {it.copy(name="Сохранённый ввод товара")}}
            withTimeout(20_000) {while(db.catalog().draft()?.payload?.contains("Сохранённый ввод товара")!=true) delay(10)}
            withContext(Dispatchers.Main) {
                suppliers.edit("s");suppliers.change {it.copy(name="Несохранённый поставщик")}
                staff.edit();staff.change {it.copy(name="Несохранённый сотрудник")}
                purchase.supplier("s");receiving.standalone();counts.openCount("p")
            }
            waitFor {!receiving.state.value.pending && receiving.state.value.form!=null && !counts.state.value.pending && counts.state.value.countForm!=null}
            withContext(Dispatchers.Main) {receiving.change {it.copy(invoiceNumber="Не сохраняли")};counts.countRaw("3")}
            val documents=db.workspace().documents();val savedProduct=db.catalog().draft();val balance=db.catalog().balance("p")
            relogin()
            waitFor {products.state.value.draft==null && suppliers.state.value.form==null && staff.state.value.form==null && purchase.state.value.form==null && receiving.state.value.form==null && counts.state.value.countForm==null && !counts.state.value.canEdit}
            assertEquals(documents,db.workspace().documents());assertEquals(savedProduct,db.catalog().draft());assertEquals(balance,db.catalog().balance("p"))
            assertNotNull(receiving.state.value.snapshot);assertNotNull(counts.state.value.snapshot);assertNotNull(purchase.state.value.snapshot)
            withContext(Dispatchers.Main) {products.save();suppliers.save();staff.save();purchase.save();receiving.save();counts.saveCount()}
            assertEquals(documents,db.workspace().documents());assertEquals(balance,db.catalog().balance("p"))
            // Explicit fresh reentry can recover durable business drafts, never the transient invoice/count/name edits.
            withContext(Dispatchers.Main) {products.edit();receiving.refresh();counts.refresh()}
            waitFor {!products.state.value.busy && products.state.value.draft!=null && !receiving.state.value.pending && !counts.state.value.pending}
            assertEquals("Сохранённый ввод товара",products.state.value.draft!!.name)
            assertNull(receiving.state.value.form);assertNull(counts.state.value.countForm)
            withContext(Dispatchers.Main) {receiving.standalone()};waitFor {!receiving.state.value.pending}
            assertEquals("",receiving.state.value.form!!.invoiceNumber)
        } finally {withContext(Dispatchers.Main) {store.clear()};db.close()}
    }
    private class Uncertain: SupplierAccess {
        @Volatile var failOutcome=true
        @Volatile var committed=false
        @Volatile var commits=0
        var rows=emptyList<Supplier>()
        override suspend fun snapshot()=SupplierSnapshot("owner",rows,emptyList())
        override suspend fun outcome(command: SupplierCommand): String? {if(failOutcome) error("read unavailable");return if(committed) (command.action as SupplierAction.Save).id else null}
        override suspend fun commit(command: SupplierCommand): String {commits++;error("must not be retried under a new login")}
    }
    @Test fun unknownResultRemainsReadableAfterLogoutButMissingResultCannotResubmitUnderNewLogin(): Unit=runBlocking {
        for(known in listOf(false,true)) {
            val store=ViewModelStore();val access=Uncertain()
            try {
                val model=withContext(Dispatchers.Main) {ViewModelProvider(store,object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=SupplierViewModel(access) as T
                })[SupplierViewModel::class.java]}
                waitFor {!model.state.value.pending}
                withContext(Dispatchers.Main) {model.edit();model.change {it.copy(name="Поставщик")};model.save()}
                waitFor {!model.state.value.pending && model.state.value.unresolved!=null}
                val command=model.state.value.unresolved!!
                relogin();waitFor {model.state.value.form==null}
                access.committed=known;access.failOutcome=false
                if(known) access.rows=listOf(Supplier((command.action as SupplierAction.Save).id,"Поставщик",1,emptySet()))
                withContext(Dispatchers.Main) {model.retry()};waitFor {!model.state.value.pending && model.state.value.unresolved==null}
                assertEquals(0,access.commits);assertNull(model.state.value.form)
                assertEquals(if(known) 1 else 0,model.state.value.snapshot!!.suppliers.size)
                assertNotNull(command.commandId)
            } finally {withContext(Dispatchers.Main) {store.clear()}}
        }
    }
    @Test fun lockedProcessReadsPersistedProductDraftWithoutWritingOrOpeningEditor(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build();val store=ViewModelStore()
        try {
            val repo=CatalogRepository(db);repo.initialize();val draft=ProductDraft(name="Черновик до перезапуска",dirty=true)
            repo.saveDraft(DraftEntity(payload=org.json.JSONObject(draft.json()).apply {remove("sessionId")}.toString()));val original=db.catalog().draft();Administration.session.logout()
            val model=withContext(Dispatchers.Main) {ViewModelProvider(store,object: ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=CatalogViewModel(repo) as T
            })[CatalogViewModel::class.java]}
            waitFor {!model.state.value.loading};assertFalse(model.state.value.fatal);assertNull(model.state.value.draft);assertTrue(model.state.value.hasSavedDraft)
            assertEquals(original,db.catalog().draft());assertTrue(Administration.session.login("fixture-admin".toCharArray()))
            assertNull(model.state.value.draft)
            withContext(Dispatchers.Main) {model.edit()};waitFor {!model.state.value.busy && model.state.value.draft!=null}
            assertEquals(draft.id,model.state.value.draft!!.id);assertEquals(draft.name,model.state.value.draft!!.name)
            assertEquals(model.state.value.draft!!.sessionId,org.json.JSONObject(db.catalog().draft()!!.payload).getString("sessionId"))
        } finally {withContext(Dispatchers.Main) {store.clear()};db.close()}
    }
}
