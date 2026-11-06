package com.mendelev.mpos

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.backup.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.NativeRestoreValidator
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.Base64
import java.util.UUID

class WebCoreRestoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(): WebBackupDocument {
        val bitmap=Bitmap.createBitmap(16,12,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.GREEN)}
        val png=try {ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()} finally {bitmap.recycle()}
        val j=JSONObject("""{
          "version":1,
          "products":[
            {"id":"bean","name":"Зёрна","category":"Сырьё","type":"simple","price":2,"cost":2,"stock":9.0004,"stockUnit":"kg","stockDisplayUnit":"g","minStock":0.0004,"purchaseUnit":"box","purchaseContentUnit":"g","purchasePackSize":500,"allowNegativeStock":false,"noStockTracking":false},
            {"id":"milk","name":"Молоко","category":"Сырьё","type":"simple","price":1,"cost":1,"stock":5,"stockUnit":"l","stockDisplayUnit":"ml","allowNegativeStock":true,"noStockTracking":true},
            {"id":"base","name":"Основа","category":"Сырьё","type":"composite","price":2,"recipeUnit":"l","recipeYield":0.1,"components":[{"productId":"bean","qty":0.02,"displayUnit":"g"}]},
            {"id":"brew","name":"Кофе","category":"Напитки","type":"composite","price":3,"recipeUnit":"piece","recipeYield":1,"recipeYieldManual":true,"components":[{"productId":"base","qty":1,"displayUnit":"ml"}],
              "modifierGroups":[{"id":"g","name":"Добавка","min":0,"max":1,"options":[{"id":"m","productId":"milk","posName":"Молоко","qty":0.01,"priceDelta":0.5}]}],
              "sku":"BREW-01","internalNote":null,"description":"Описание","sortOrder":1.5,"tileSymbol":"★","availableOnline":false,"localImageId":"photo"}
          ],
          "employees":[{"id":"anna","name":"Анна","phone":"+375291234567","role":"employee"}],"shifts":[],"orders":[],
          "suppliers":[{"id":"supplier","name":"Поставщик","productIds":["bean","milk"]}],
          "layout":{"categoryOrder":["Напитки","Сырьё"],"categoryColors":{"Напитки":"#DBEAFE"},"categorySymbols":{"Напитки":"☕"},"tiles":[{"type":"category","id":"Напитки","col":2,"row":1},{"type":"product","id":"brew","col":4,"row":2}]},
          "posNavigation":{"version":1,"categories":[{"category":"Напитки","items":[{"type":"folder","id":"folder","name":"Кофе"},{"type":"product","id":"brew","parentId":"folder"}]}]},
          "inventoryConfig":{"enabled":true,"frequency":"monthly","productIds":["bean"],"lastCompletedAt":null},
          "inventoryDraft":{"id":"count","type":"adhoc","startedAt":1,"scheduledDate":1,"items":[{"productId":"bean","name":"Зёрна","unit":"kg","expected":10,"actual":9.0004,"difference":-0.9996,"fixedAt":1}]},
          "receivingDraft":{"version":1,"lines":[{"productId":"bean","qtyInput":"2.5","unit":"kg","contentUnit":"kg","packSize":"","priceMode":"total","priceInput":"","totalInput":"5"}]}
        }""")
        j.put("productImages",JSONObject().put("photo",Base64.getEncoder().encodeToString(png)))
        return WebBackupReader.read(j.toString().byteInputStream())
    }
    @Test fun fullCatalogAndDirectoriesSurviveDeferredDraftsAndNativeBackupRoundtrip()=runBlocking {
        val root=File(context.cacheDir,"web-core-${UUID.randomUUID()}").apply {mkdirs()}
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"candidate.db").absolutePath).build()
        var media: File?=null
        try {
            val source=source();val permit=AdminForms(this).permit()
            val photos=permit.write {WebPhotoPreparation(context).prepare(source)};media=photos.mediaRoot.parentFile
            permit.write {WebCandidateDatabase(context,db).install(source,photos.files)}
            WebCandidateValidation.initial(db,source)
            val bean=WebIdentity.mapped("product","bean");val milk=WebIdentity.mapped("product","milk");val brew=WebIdentity.mapped("product","brew")
            assertEquals("9.0004",db.catalog().balance(bean)!!.quantity)
            assertEquals("0.0004",db.catalog().product(bean)!!.minimumStock);assertEquals("0.5",db.catalog().product(bean)!!.packSize)
            assertEquals("g",db.catalog().product(bean)!!.displayUnit);assertTrue(db.catalog().product(milk)!!.noStockTracking);assertTrue(db.catalog().product(milk)!!.allowNegativeStock)
            val snapshot=WorkspaceRepository(db).snapshot()
            assertEquals(listOf(7,14),snapshot.home.tiles.map {it.slot});assertEquals("★",snapshot.products.single {it.id==brew}.symbol)
            assertEquals("☕",snapshot.categories.single {it.name=="Напитки"}.symbol)
            assertTrue(snapshot.layout.single {it.id==brew}.folderId.isNotBlank())
            val p=snapshot.products.single {it.id==brew};val option=p.groups.single().options.single()
            val cart=CartEngine.apply(CurrentCart(),CartAction.Add(brew,p.revision,setOf(option.id),null,"line"),snapshot.products,snapshot.graph,snapshot.balances)
            assertEquals(350L,SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity)}).total)
            assertEquals(0,CartEngine.reserved(cart).getValue(bean).compareTo(java.math.BigDecimal("0.02")))
            assertFalse(milk in CartEngine.reserved(cart))
            assertEquals(0,RecipeCalculator(cart.graph).cost(CartEngine.requests(cart)).compareTo(java.math.BigDecimal("0.05")))
            val employee=EmployeeCodec.decode(db.workspace().document(EmployeeCodec.KEY)!!.payload).single();assertEquals("Анна",employee.name);assertEquals("+375291234567",employee.phone)
            assertEquals(setOf(bean,milk),SupplierCodec.decode(db.workspace().document(SupplierCodec.KEY)!!.payload).single().productIds)
            val metadata=db.workspace().document(WebProductMetadataDocuments.PREFIX+brew)!!.payload
            val saved=WebProductMetadataDocuments.read(metadata,brew)
            assertTrue(saved.payload.contains("BREW-01"));assertTrue(saved.payload.contains("\"internalNote\":null"))
            assertNull(InventoryCodec.decode(db.workspace().document(InventoryCodec.KEY)!!.payload).draft)
            assertNull(db.workspace().document(ReceivingCodec.KEY));assertTrue(db.catalog().movements().isEmpty())
            val stage=File(root,"snapshot").apply {mkdir()}
            val files=NativeSnapshotDatabase.capture(db,ProductPhotoStore(photos.mediaRoot),stage)
            val archive=File(root,"copy.mposnative");NativeBackupArchive.write(archive,files)
            val extracted=File(root,"extract").apply {mkdir()};val restored=archive.inputStream().use {NativeBackupArchive.read(it,extracted)}
            NativeRestoreValidator.validate(context,restored,extracted)
            val clone=Room.databaseBuilder(context,CatalogDatabase::class.java,File(root,"clone.db").absolutePath).createFromFile(restored.database).build()
            try {
                assertEquals(db.catalog().allProducts().toSet(),clone.catalog().allProducts().toSet())
                assertEquals(db.catalog().allComponents(),clone.catalog().allComponents())
                assertEquals(db.catalog().allModifierGroups(),clone.catalog().allModifierGroups());assertEquals(db.catalog().allModifierOptions(),clone.catalog().allModifierOptions())
                assertEquals(db.workspace().documents(),clone.workspace().documents())
                assertEquals(snapshot,WorkspaceRepository(clone).snapshot())
                val photo=db.catalog().product(brew)!!.photoId!!
                assertArrayEquals(ProductPhotoStore(photos.mediaRoot).file(photo).readBytes(),restored.photos.getValue(photo).readBytes())
                assertArrayEquals(source.original,WebSourceDocuments.bytes(clone.workspace().documents().associate {it.id to it.payload}))
            } finally {clone.close()}
            // A pre-sidecar native copy still derives its tile symbol from its unchanged original.
            db.workspace().productMetadataDocuments().forEach {db.workspace().delete(it.id)}
            assertEquals("★",WorkspaceRepository(db).snapshot().products.single {it.id==brew}.symbol)
        } finally {db.close();root.deleteRecursively();media?.deleteRecursively()}
    }
}
