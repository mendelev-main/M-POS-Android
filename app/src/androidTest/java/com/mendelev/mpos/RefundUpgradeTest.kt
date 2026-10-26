package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.refund.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Isolated persisted fixture: the real updater still uses the guarded main database. */
class RefundUpgradeTest {
    @Test fun persistedRefundAndReplay(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val verify=InstrumentationRegistry.getArguments().getString("saleUpgradeMode")=="verify"
        val name="refund-upgrade.db";if(!verify) context.deleteDatabase(name)
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        try {
            if(!verify) {
                val c=CatalogRepository(db);c.initialize()
                c.saveProduct(ProductInput("ingredient",null,"Ингредиент",null,"1","1","10",null,true,"ingredient",unit="kg"))
                c.saveProduct(ProductInput("dish",null,"Историческое блюдо",null,"20","0","0",null,false,"dish",type="composite",
                    components=listOf(IngredientInput("ingredient","0.0004","kg"))))
                val work=WorkspaceRepository(db);work.initialize();val shifts=ShiftRepository(db);shifts.initialize()
                val initial=shifts.snapshot();shifts.open("open",initial.revision,null,initial.ownerId)
                work.cart(CartAction.Add("dish",1,emptySet(),null,"line"),"add",work.snapshot().cart.sessionId)
                var w=work.snapshot()
                work.cart(CartAction.SetDetails(OrderDetails(type="delivery"),w.cartRevision),"details",w.cart.sessionId)
                w=work.snapshot();val active=shifts.snapshot()
                val saved=CheckoutRepository(db).save(0,CheckoutDraft("upgrade-sale",w.cart.sessionId,w.cartRevision,active.active!!.id,
                    active.revision,active.ownerId,delivery=DeliveryCharge("courier","Курьер",500,1),parts=listOf(PaymentInput("cash",2500,3000))))
                val receiptId=SaleRepository(db).commit(saved)
                val repo=RefundRepository(db);val preview=repo.preview(receiptId,active.ownerId)
                // Test-only metadata needed to reconstruct the exact submitted command after restart.
                db.workspace().put(WorkspaceDocument("refund-upgrade-preview",preview.revision,"{\"version\":1}"))
                repo.commit("upgrade-refund",preview)
                val ingredient=db.catalog().product("ingredient")!!
                db.catalog().updateProduct(ingredient.copy(noStockTracking=true,revision=2))
                val dish=db.catalog().product("dish")!!
                db.catalog().updateProduct(dish.copy(name="Новое имя",price="99",revision=2))
                db.catalog().clearRecipe("dish")
                db.catalog().insertComponents(listOf(RecipeComponentEntity("dish",0,"ingredient","5","kg")))
                val done=shifts.snapshot()
                shifts.close("close",done.active!!.id,done.revision,ShiftRules.token(done),0,done.ownerId)
            }
            val repo=RefundRepository(db);val refund=repo.outcome("upgrade-refund")!!
            val receipt=db.sales().receipt(refund.receiptId)!!;val historical=ReceiptCodec.decode(receipt.payload)
            assertEquals("Историческое блюдо",historical.cart.lines.single().name)
            assertEquals(2500L,refund.total);assertEquals(2500L,refund.cash);assertEquals(0L,refund.card);assertEquals(500L,refund.deliveryCashCredit)
            assertEquals(2500L,historical.quote.total);assertEquals(500L,historical.payment.parts.single().change)
            assertEquals(mapOf("ingredient" to "0.0004"),historical.consumption)
            assertEquals("10",db.catalog().balance("ingredient")!!.quantity);assertEquals(3L,db.catalog().balance("ingredient")!!.revision)
            assertEquals(3,db.catalog().movementCount("ingredient"));assertEquals("5",db.catalog().recipe("dish").single().quantity)
            assertEquals(1,db.sales().history().size);assertEquals(1,db.refunds().history().size)
            assertEquals(receipt.id,db.catalog().result("upgrade-sale")!!.resultId)
            assertEquals(refund.id,db.catalog().result("upgrade-refund")!!.resultId)
            assertEquals(CatalogRules.fingerprint("refund.receipt.v1",receipt.payload),refund.receiptHash)
            val state=ShiftRepository(db).snapshot();assertNull(state.active);assertEquals(0L,state.currentCash)
            assertEquals(0L,state.last!!.expectedCash)
            val payload=JSONObject(refund.payload);assertEquals(receipt.payload, payload.getJSONObject("receipt").toString())
            val array=payload.getJSONArray("stock")
            val stock=(0 until array.length()).map {array.getJSONObject(it).let {s->RestockChange(s.getString("productId"),s.getString("delta"),
                s.getString("before"),s.getString("after"),s.getLong("expectedRevision"),s.getLong("nextRevision"))}}
            val preview=RefundPreview(receipt.id,refund.shiftId,db.workspace().document("refund-upgrade-preview")!!.revision,refund.actorId,
                payload.getString("previewToken"),RefundAmounts(refund.total,refund.cash,refund.card,refund.deliveryCashCredit,
                    payload.getLong("cashDelta"),payload.getLong("expectedCashAfter")),stock)
            assertEquals(refund.id,repo.commit("upgrade-refund",preview))
            assertEquals(1,db.refunds().history().size);assertEquals(3,db.catalog().movementCount("ingredient"))
            assertEquals(state,ShiftRepository(db).snapshot())
            assertEquals(refund,repo.outcome("upgrade-refund"));assertEquals(receipt,db.sales().receipt(receipt.id))
        } finally {db.close()}
    }
}
