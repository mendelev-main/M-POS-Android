package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.sale.DeliveryCharge
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OrderSettingsStorageTest {
    @Test fun newMetadataRoundTripsRetriesAndFailuresDoNotOverwriteOrder(): Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,CatalogDatabase::class.java).build()
        try {
            val repo=WorkspaceRepository(db);repo.initialize();val before=repo.snapshot()
            val details=OrderDetails(type="delivery",address="Улица 1",phone="123",customerName="Анна",deliveryTariff=DeliveryCharge("city","По городу",500,1),deliveryEdited=true)
            val action=CartAction.SetDetails(details,before.cartRevision)
            repo.cart(action,"select",before.cart.sessionId);repo.cart(action,"select",before.cart.sessionId)
            val saved=WorkspaceRepository(db).snapshot()
            assertEquals(before.cartRevision+1,saved.cartRevision);assertEquals(details,saved.cart.details);assertEquals(details.deliveryTariff,saved.sourceDelivery)
            assertEquals(saved.cart,WorkspaceCodec.cart(WorkspaceCodec.cart(saved.cart)))
            try {repo.cart(action.copy(details=details.copy(customerName="Борис")),"select",before.cart.sessionId);fail()} catch(e: CatalogFailure) {assertEquals("conflict",e.kind)}
            try {WorkspaceRepository(db) {error("storage fault")}.cart(CartAction.SetDetails(details.copy(label="Новая подпись"),saved.cartRevision),"fault",saved.cart.sessionId);fail()} catch(_: IllegalStateException) {}
            assertEquals(saved.cart,repo.snapshot().cart);assertNull(db.catalog().result("fault"))
            val legacy=CurrentCart(sessionId="legacy")
            val raw=WorkspaceCodec.cart(legacy)
            assertFalse(raw.contains("customerName"));assertFalse(raw.contains("deliveryEdited"))
            assertEquals(legacy,WorkspaceCodec.cart(raw))
        } finally {db.close()}
    }
}
