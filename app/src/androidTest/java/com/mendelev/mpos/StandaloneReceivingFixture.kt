package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import com.mendelev.mpos.domain.warehouse.*

suspend fun standaloneInvoice(db: CatalogDatabase,key: String="standalone"): ReceivingDraft {
    val catalog=CatalogRepository(db);catalog.initialize()
    catalog.saveProduct(ProductInput("p-$key",null,"Молоко $key",null,"5","2","10",null,true,"product-$key",unit="l"))
    val owner=ReceivingRepository(db).snapshot().ownerId
    SupplierRepository(db).commit(SupplierCommand("supplier-$key",owner,SupplierAction.Save("s-$key",null,"Поставка $key",setOf("p-$key"))))
    val s=ReceivingRepository(db).snapshot()
    return ReceivingDraft("invoice-$key",null,"TTN-$key","2026-10-27",listOf(ReceivingLine(s.products.single {it.id=="p-$key"},"10","l","","l","40")),s.suppliers.single {it.id=="s-$key"})
}
