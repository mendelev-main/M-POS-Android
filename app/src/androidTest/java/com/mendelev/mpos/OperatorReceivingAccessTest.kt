package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Isolated DB, operational writes while administrative session is closed. No external effects. */
class OperatorReceivingAccessTest {
    private fun database()=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
    @Test fun employeeCreatesAndEditsPendingOrderButCannotCancelOrChangeSupplier()=runBlocking {
        val db=database()
        try {
            val seed=seedReceiving(db,"operator-orders")
            Administration.session.logout()
            val repo=PurchaseDraftRepository(db);val state=repo.snapshot()
            val draft=PurchaseDraftRules.revalidate(seed.order.source.copy(id="employee-draft"),state.suppliers,state.products)
            repo.commit(PurchaseCommand("employee-save",state.ownerId,state.revision,PurchaseAction.Save(draft)))
            val command=PurchaseCommand("employee-place",state.ownerId,repo.snapshot().revision,PurchaseAction.Place("employee-order",draft))
            assertEquals("employee-order",repo.commit(command));assertEquals("employee-order",repo.commit(command))
            val order=repo.snapshot().orders.single {it.id=="employee-order"}
            val current=repo.snapshot()
            val updated=PurchaseDraftRules.revalidate(draft.copy(lines=draft.lines.map {it.copy(quantity="12")}),current.suppliers,current.products)
            PurchaseOrderRepository(db).commit(PurchaseOrderCommand("employee-edit",state.ownerId,order.id,order.revision,PurchaseOrderAction.Edit(updated)))
            val changed=repo.snapshot().orders.single {it.id==order.id};assertEquals(2L,changed.requestVersion)
            try {PurchaseOrderRepository(db).commit(PurchaseOrderCommand("employee-cancel",state.ownerId,changed.id,changed.revision,PurchaseOrderAction.Cancel));fail()} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
            try {SupplierRepository(db).commit(SupplierCommand("employee-supplier",state.ownerId,SupplierAction.Delete(draft.supplierId,1)));fail()} catch(e:CatalogFailure) {assertEquals("access",e.kind)}
            assertEquals("pending",repo.snapshot().orders.single {it.id==changed.id}.status)
            assertEquals("10",db.catalog().balance(seed.productId)!!.quantity)
        } finally {db.close()}
    }
    @Test fun employeePostingRollsBackOnFailureThenCommitsAndReplaysOnce()=runBlocking {
        val db=database()
        try {
            val seed=seedReceiving(db,"operator-receiving",openShift=true);val drafts=ReceivingRepository(db);val state=drafts.snapshot()
            val invoice=ReceivingRules.open("employee-invoice",seed.order,state.products).copy(invoiceNumber="fixture",lines=ReceivingRules.open("employee-invoice",seed.order,state.products).lines.map {it.copy(total="40")})
            Administration.session.logout()
            drafts.commit(ReceivingCommand("employee-invoice-save",seed.owner,invoice.key,0,invoice))
            val command=ReceivingPostCommand("employee-post",seed.owner,1,invoice)
            val before=db.catalog().balance(seed.productId)!!
            try {ReceivingPostingRepository(db,afterWrite={throw IllegalStateException("fixture disk failure")}).commit(command);fail()} catch(_:IllegalStateException) {}
            assertEquals(before,db.catalog().balance(seed.productId));assertEquals("pending",PurchaseOrderStore(db).all().single().status)
            val repo=ReceivingPostingRepository(db);val id=repo.commit(command)
            assertEquals(id,repo.commit(command));assertEquals("20",db.catalog().balance(seed.productId)!!.quantity)
            assertEquals("3",db.catalog().product(seed.productId)!!.cost);assertEquals(2,db.catalog().movementCount(seed.productId))
            assertEquals("received",PurchaseOrderStore(db).all().single().status)
            assertFalse(Administration.session.state.value.active)
        } finally {db.close()}
    }
}
