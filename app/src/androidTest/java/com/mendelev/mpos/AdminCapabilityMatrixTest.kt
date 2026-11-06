package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.staff.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Calls the real repositories without UI, with an owner ID that is valid but grants no authority. */
class AdminCapabilityMatrixTest {
    @Test fun everyAdministrativeCommandFamilyRefusesLockedRuntimeWithoutChangingAnyTable()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val seed=seedReceiving(db,"capabilities")
            val catalog=CatalogRepository(db)
            catalog.saveCategory("category",null,"Категория","category-create")
            catalog.saveDraft(DraftEntity(payload="{\"sessionId\":\"kept\"}"))
            val workspace=WorkspaceRepository(db);workspace.initialize()
            val purchase=PurchaseDraftRepository(db);val ps=purchase.snapshot()
            val receiving=ReceivingRepository(db);val rs=receiving.snapshot()
            val invoice=ReceivingRules.open("invoice",seed.order,rs.products).let {d->d.copy(lines=d.lines.map {it.copy(total="40")})}
            receiving.commit(ReceivingCommand("invoice-save",seed.owner,invoice.key,0,invoice))
            val employees=EmployeeRepository(db)
            employees.commit(EmployeeCommand("employee-create",seed.owner,EmployeeAction.Save("cashier",null,"Кассир","","employee")))
            val inventory=InventoryRepository(db);val inv=inventory.snapshot();val product=inv.products.single {it.id==seed.productId}
            val calls=mutableListOf<Pair<String,suspend ()->Unit>>()
            fun check(name: String,block: suspend ()->Unit) {calls.add(name to block)}
            val p=db.catalog().product(seed.productId)!!;val b=db.catalog().balance(p.id)!!
            check("catalog.product") {catalog.saveProduct(ProductInput(p.id,p.revision,p.name,null,p.price,p.cost,b.quantity,b.revision,true,"denied-product",unit=p.unit))}
            check("catalog.category.save") {catalog.saveCategory("category",1,"Другое","denied-category")}
            check("catalog.category.order") {catalog.reorderCategories(listOf(CategoryOrderEntry("category",1)),"denied-order")}
            check("catalog.category.delete") {catalog.deleteCategory("category",1,"denied-category-delete")}
            check("catalog.product.delete") {catalog.deleteProduct(p.id,p.revision,"denied-product-delete")}
            check("catalog.draft.save") {catalog.saveDraft(DraftEntity(payload="{}"))}
            check("catalog.draft.discard") {catalog.discardDraft()}
            for((i,a) in listOf(HomeAction.Add("product",p.id),HomeAction.Move("product",p.id,8),HomeAction.Remove("product",p.id),HomeAction.Reset).withIndex())
                check("home.$i") {workspace.home(a,workspace.snapshot().homeRevision,"denied-home-$i")}
            for((i,a) in listOf(LayoutAction.SaveFolder("folder","category","Папка"),LayoutAction.DeleteFolder("folder"),LayoutAction.MoveProduct(p.id,"folder"),LayoutAction.ResetProduct(p.id),LayoutAction.Reorder("product",p.id,0),LayoutAction.Step("product",p.id,1)).withIndex())
                check("layout.$i") {workspace.layout(a,workspace.snapshot().layoutRevision,"denied-layout-$i")}
            for((i,a) in listOf(EmployeeAction.Save("cashier",1,"Другое","","admin"),EmployeeAction.Delete("cashier",1)).withIndex())
                check("employee.$i") {employees.commit(EmployeeCommand("denied-employee-$i",seed.owner,a))}
            for((i,a) in listOf(SupplierAction.Save(seed.order.source.supplierId,1,"Другое",setOf(p.id)),SupplierAction.Delete(seed.order.source.supplierId,1)).withIndex())
                check("supplier.$i") {SupplierRepository(db).commit(SupplierCommand("denied-supplier-$i",seed.owner,a))}
            for((i,a) in listOf(PurchaseAction.Save(seed.order.source),PurchaseAction.Place("new-order",seed.order.source),PurchaseAction.Clear).withIndex())
                check("purchase.draft.$i") {purchase.commit(PurchaseCommand("denied-purchase-$i",seed.owner,ps.revision,a))}
            for((i,a) in listOf(PurchaseOrderAction.Edit(seed.order.source),PurchaseOrderAction.Cancel).withIndex())
                check("purchase.order.$i") {PurchaseOrderRepository(db).commit(PurchaseOrderCommand("denied-order-$i",seed.owner,seed.order.id,seed.order.revision,a))}
            check("receiving.save") {receiving.commit(ReceivingCommand("denied-invoice",seed.owner,invoice.key,1,invoice))}
            check("receiving.clear") {receiving.commit(ReceivingCommand("denied-invoice-clear",seed.owner,invoice.key,1,null))}
            check("receiving.post") {ReceivingPostingRepository(db).commit(ReceivingPostCommand("denied-post",seed.owner,1,invoice))}
            for((i,a) in listOf(InventoryAction.Configure(InventoryConfig()),InventoryAction.Start("count","adhoc"),InventoryAction.Count("count",product,"9","generation"),InventoryAction.Fix("count",p.id,"generation"),InventoryAction.Finish("count",false),InventoryAction.Finish("count",true)).withIndex())
                check("inventory.$i") {inventory.commit(InventoryCommand("denied-inventory-$i",seed.owner,inv.revision,a))}
            val before=tables(db)
            Administration.session.logout()
            for((name,call) in calls) {
                try {call();fail("$name accepted without administration")}
                catch(e: CatalogFailure) {assertEquals(name,"access",e.kind)}
                assertEquals("$name changed persisted state",before,tables(db))
            }
            assertFalse(Administration.session.state.value.active)
        } finally {db.close()}
    }
    private fun tables(db: CatalogDatabase): Map<String,List<List<String?>>> {
        val sql=db.openHelper.readableDatabase
        val names=sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use {c->buildList {while(c.moveToNext()) add(c.getString(0))}}
        return names.associateWith {name->sql.query("SELECT * FROM \"$name\" ORDER BY rowid").use {c->buildList {while(c.moveToNext()) add((0 until c.columnCount).map {if(c.isNull(it)) null else c.getString(it)})}}}
    }
}
