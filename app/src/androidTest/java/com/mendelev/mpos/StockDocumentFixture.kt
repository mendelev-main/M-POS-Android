package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.domain.sale.*

suspend fun stockDocumentFixture(db: CatalogDatabase) {
    val d=standaloneInvoice(db,"stock-doc");val owner=ReceivingRepository(db).snapshot().ownerId
    ReceivingRepository(db).commit(ReceivingCommand("save-invoice",owner,d.key,0,d))
    ReceivingPostingRepository(db).commit(ReceivingPostCommand("post-invoice",owner,1,d))
    val work=WorkspaceRepository(db);work.initialize()
    work.cart(CartAction.Add("p-stock-doc",2,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
    val shifts=ShiftRepository(db);shifts.initializeCashierFixture();val initial=shifts.snapshot();shifts.open("open",initial.revision,null,owner)
    val s=shifts.snapshot();val w=work.snapshot()
    val saved=CheckoutRepository(db).save(0,CheckoutDraft("sale",w.cart.sessionId,w.cartRevision,s.active!!.id,s.revision,owner,parts=listOf(PaymentInput("card",500))))
    val receipt=SaleRepository(db).commit(saved);val refunds=RefundRepository(db);refunds.commit("refund",refunds.preview(receipt,owner))
    val inv=InventoryRepository(db,generation="runtime");val snap=inv.snapshot();val p=snap.products.single {it.id=="p-stock-doc"}
    inv.commit(InventoryCommand("config",owner,0,InventoryAction.Configure(InventoryConfig(true,"monthly",setOf(p.id)))))
    inv.commit(InventoryCommand("start",owner,1,InventoryAction.Start("inventory","adhoc")))
    inv.commit(InventoryCommand("count",owner,2,InventoryAction.Count("inventory",p,"0.0004","runtime")))
    inv.commit(InventoryCommand("inventory-fix",owner,3,InventoryAction.Fix("inventory",p.id,"runtime")))
    inv.commit(InventoryCommand("finish",owner,4,InventoryAction.Finish("inventory",false)))
}
