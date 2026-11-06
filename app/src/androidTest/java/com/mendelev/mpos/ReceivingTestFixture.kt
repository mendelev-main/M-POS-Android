package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.warehouse.*

data class ReceivingSeed(val owner: String,val order: PurchaseOrder,val productId: String)
suspend fun seedReceiving(db: CatalogDatabase,key: String="fixture",unknown: Boolean=false): ReceivingSeed {
    val p="receiving-product-$key";val s="receiving-supplier-$key";val order="receiving-order-$key"
    val catalog=CatalogRepository(db);catalog.initialize()
    catalog.saveProduct(ProductInput(p,null,"Молоко $key",null,"5","2","10",null,true,"receiving-product-create-$key",unit="l",purchaseLabel="bottle",packSize="1"))
    val repo=PurchaseDraftRepository(db);val first=repo.snapshot()
    if(first.draft!=null) repo.commit(PurchaseCommand("receiving-clear-synthetic-$key",first.ownerId,first.revision,PurchaseAction.Clear))
    SupplierRepository(db).commit(SupplierCommand("receiving-supplier-create-$key",first.ownerId,SupplierAction.Save(s,null,"Поставка $key",setOf(p))))
    val snap=repo.snapshot();val d=PurchaseDraft("receiving-request-$key",s,1,"Поставка $key",listOf(snap.products.single {it.id==p}.blankLine().copy(quantity="10",packSize=if(unknown) "" else "1000",contentUnit="ml")))
    repo.commit(PurchaseCommand("receiving-request-save-$key",first.ownerId,snap.revision,PurchaseAction.Save(d)))
    repo.commit(PurchaseCommand("receiving-request-place-$key",first.ownerId,repo.snapshot().revision,PurchaseAction.Place(order,d)))
    return ReceivingSeed(first.ownerId,repo.snapshot().orders.single {it.id==order},p)
}
