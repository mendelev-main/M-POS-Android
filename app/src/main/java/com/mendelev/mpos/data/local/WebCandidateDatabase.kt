package com.mendelev.mpos.data.local

import android.content.Context
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.PaymentInput
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.domain.warehouse.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Populates an EMPTY candidate. No sale, stock, receiving or cash command is replayed. */
internal class WebCandidateDatabase(private val context: Context,private val db: CatalogDatabase,private val afterWrite: ()->Unit={}) {
    suspend fun install(source: WebBackupDocument,photos: Map<String,String>) {
        val permit=currentCoroutineContext()[AdminAuthorization] ?: throw CatalogFailure("access","Требуется администратор")
        if(permit.session!==Administration.session)throw CatalogFailure("access","Сессия завершена")
        permit.requireCurrent()
        val name=db.openHelper.databaseName ?: fail("Нужна отдельная база")
        if(context.getDatabasePath(name).canonicalFile==com.mendelev.mpos.data.recovery.AppDataset.paths(context).database.canonicalFile)fail("Рабочая база не является кандидатом")
        val warehouse=WebWarehouseHistory.map(source)
        if(warehouse.inventoryDraft!=null)fail("Перенос незавершённой инвентаризации пока не поддерживается. Файл не будет применён")
        if(warehouse.receivingDrafts.isNotEmpty())fail("Перенос незавершённой приёмки пока не поддерживается. Файл не будет применён")
        if(warehouse.purchases.any {it.status=="pending"})fail("Перенос ожидаемых закупок пока не поддерживается. Файл не будет применён")
        val catalog=WebCatalogMapping.map(source);val staff=WebStaffMapping.map(source);val layout=WebLayoutMapping.map(source)
        val binding=WebRecipeBinding.bind(source);val cash=WebCashReconciliation.compare(source)
        val evidence=WebImportedPaymentEvidenceCodec.encode(source)
        val deferred=setOf("metadataRetentionRequired","productMetadataNotMapped","photoTranscodePending","externalPhotoNeedsExplicitSkip","compositeLegacyStockFields")
        catalog.products.forEach {p->if((p.blockers-deferred).isNotEmpty())fail("Товар не прошёл проверку: "+(p.blockers-deferred).sorted().joinToString())}
        if(layout.blockers.isNotEmpty())fail("Раскладка не прошла проверку")
        if(binding.shortages?.isNotEmpty()==true || binding.aggregateConsumption==null && binding.carts.isNotEmpty())fail("Не сошлись резервы открытых заказов")
        val resolved=setOf("operationalCartMappingPending","cashHistoryReconciliationPending","sourceQuoteNotPersisted","nativeCartEncodingPending","sourceRecipeAtExportOnly","shiftBindingInferred","nativePaidProvenancePending","unadoptedSourceEvidence")+deferred
        binding.carts.forEach {if((it.blockers-resolved).isNotEmpty())fail("Открытый заказ требует сверки: "+(it.blockers-resolved).sorted().joinToString())}
        if(cash.unassignedOrigins.isNotEmpty() || (cash.blockers-setOf("nativePaidProvenancePending")).isNotEmpty())fail("Не удалось связать оплаты со сменами")
        cash.shifts.forEach {if((it.blockers-resolved-setOf("expectedCashNeedsHistory","sourceRefundCompensationDifference")).isNotEmpty())fail("Не сошлись кассовые факты смены: "+it.blockers.sorted().joinToString())}
        // Every raw section survives byte-for-byte; legacy facts have a separate validated archive.
        val retained=WebSourceDocuments.encode(source)+WebLegacyHistoryDocuments.encode(WebLegacyHistoryArchiveCodec.encode(source))+WebImportedPaymentDocuments.encode(evidence)
        db.adminTransaction {
            permit.requireCurrent();currentCoroutineContext().ensureActive()
            if(db.catalog().config()!=null || db.catalog().allProducts().isNotEmpty() || db.workspace().documents().isNotEmpty())fail("Кандидат уже содержит данные")
            suspend fun wrote() {afterWrite();permit.requireCurrent();currentCoroutineContext().ensureActive()}
            val config=ConfigEntity(registerId=CatalogRules.fingerprint("web.native.register.v1",source.preview.sha256),ownerId=CatalogRules.fingerprint("web.native.owner.v1",source.preview.sha256))
            db.catalog().config(config);wrote()
            db.workspace().put(WorkspaceDocument(BASIS,1,JSONObject().put("version",1).put("products",org.json.JSONArray(catalog.products.filter {it.type=="composite"}.map {it.id}.sorted())).toString()));wrote()
            layout.categories.filter {it.id.isNotEmpty()}.forEachIndexed {i,c->db.catalog().insertCategory(CategoryEntity(c.id,c.name,CatalogRules.key(c.name),i,1,c.color,c.symbol));wrote()}
            val products=catalog.products.map {p->ProductEntity(p.id,p.categoryId,p.name,CatalogRules.key(p.name),p.price,p.cost ?: "0",1,"BYN",p.unit ?: fail("Нет единицы"),p.type,p.displayUnit ?: fail("Нет единицы отображения"),p.noStockTracking,p.allowNegativeStock,p.minimumStock,p.purchaseUnit.orEmpty(),p.packSize,p.recipeYield,p.photoKey?.let {photos[it] ?: fail("Нет фото")})}
            products.forEach {db.catalog().insertProduct(it);wrote()}
            catalog.products.forEach {p->
                if(p.type=="simple") {db.catalog().insertBalance(BalanceEntity(p.id,p.stock ?: fail("Нет остатка"),1));wrote()}
                db.catalog().insertComponents(p.components.mapIndexed {i,c->RecipeComponentEntity(p.id,i,c.productId,c.quantity,c.unit ?: fail("Нет единицы ингредиента"))});wrote()
                db.catalog().insertModifierGroups(p.modifierGroups.mapIndexed {i,g->ModifierGroupEntity(p.id,g.id,g.name,g.minimum.toInt(),g.maximum.toInt(),i)});wrote()
                db.catalog().insertModifierOptions(p.modifierGroups.flatMap {g->g.options.mapIndexed {i,o->ModifierOptionEntity(p.id,o.id,g.id,o.productId,o.posName,o.quantity,o.priceDelta,i)}});wrote()
            }
            db.workspace().put(WorkspaceDocument(WebOrderDefaults.KEY,1,WebOrderDefaults.encode(source)));wrote()
            for((id,payload) in retained) {db.workspace().put(WorkspaceDocument(id,1,payload));wrote()}
            db.workspace().put(WorkspaceDocument(EmployeeCodec.KEY,1,EmployeeCodec.encode(staff.employees)));wrote()
            val suppliers=WebValues.rows(source.root.fields["suppliers"]).map {value->val fields=WebValues.obj(value)
                val ids=WebValues.rows(fields["productIds"]).map {WebIdentity.mapped("product",WebIdentity.source(it))}.toSet()
                if(ids.any {id->products.none {it.id==id && it.type=="simple"}})fail("Не найдены товары поставщика")
                Supplier(WebIdentity.mapped("supplier",WebIdentity.source(fields["id"])),WebValues.text(fields["name"]),1,ids)
            }
            val supplierPayload=SupplierCodec.encode(suppliers);SupplierCodec.decode(supplierPayload)
            db.workspace().put(WorkspaceDocument(SupplierCodec.KEY,1,supplierPayload));wrote()
            val inventoryFields=source.root.fields["inventoryConfig"]?.let(WebValues::obj).orEmpty()
            val lastInventory=WebValues.time(inventoryFields["lastCompletedAt"])?.let {java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.of("Europe/Minsk")).toLocalDate().toString()}
            val inventory=InventoryConfig(WebValues.bool(inventoryFields["enabled"]),WebValues.text(inventoryFields["frequency"],"monthly"),WebValues.rows(inventoryFields["productIds"]).map {WebIdentity.mapped("product",WebIdentity.source(it))}.toSet(),lastInventory)
            InventoryRules.validate(inventory)
            if(inventory.productIds.any {id->products.none {it.id==id && it.type=="simple"}})fail("Не найдены товары плановой инвентаризации")
            db.workspace().put(WorkspaceDocument(InventoryCodec.KEY,1,InventoryCodec.encode(InventoryDocument(config=inventory))));wrote()
            val baseline=JSONObject().put("version",1).put("source",source.preview.sha256).put("zonePolicy","Europe/Minsk")
            val cashRows=JSONObject()
            for(s in staff.shifts) {
                val compared=cash.shifts.single {it.sourceShiftId==s.sourceId}
                val actual=compared.cashFromRecordedFacts ?: fail("Нет подтверждённого остатка кассы")
                val opening=s.openingCash ?: fail("Нет начального остатка")
                val closed=s.status=="closed";val counted=if(closed)s.countedCash ?: fail("Нет пересчёта кассы") else null
                db.shifts().insert(ShiftEntity(s.id,config.registerId,s.actorId ?: fail("Нет автора смены"),s.actorName ?: fail("Нет исторического имени кассира"),s.openedAt ?: fail("Нет даты смены"),"Europe/Minsk",opening,
                    s.sourcePreviousId?.let {WebIdentity.mapped("shift",it)},if(closed)s.closedAt ?: fail("Нет даты закрытия") else null,counted,if(closed)actual else null,if(closed)Math.subtractExact(counted!!,actual) else null,if(closed)null else 1));wrote()
                cashRows.put(s.id,Math.subtractExact(actual,opening))
            }
            baseline.put("cashDeltas",cashRows)
            db.workspace().put(WorkspaceDocument(BASELINE,1,baseline.toString()));wrote()
            val last=staff.shifts.singleOrNull {it.status=="open"} ?: staff.shifts.maxByOrNull {it.closedAt ?: 0L}
            db.shifts().initialize(ShiftRegisterEntity(revision=1,lastShiftId=last?.id));wrote()
            val current=binding.carts.singleOrNull {it.location=="current"}
            val adopted=binding.carts.associate {it.origin to WebCartAdoption.map(it,products,source)}
            val cart=current?.let {adopted.getValue(it.origin).cart} ?: CurrentCart(sessionId=CatalogRules.fingerprint("web.native.empty-session.v1",source.preview.sha256))
            db.workspace().put(WorkspaceDocument("current",1,WorkspaceCodec.cart(cart)));wrote()
            db.workspace().put(WorkspaceDocument("layout",1,WorkspaceCodec.layout(layout.items)));wrote()
            db.workspace().put(WorkspaceDocument("home",1,WorkspaceCodec.home(HomeLayout(true,layout.home.map {HomeTile(it.type,it.id,it.nativeSlot ?: fail("Нет позиции"))}))));wrote()
            db.workspace().put(WorkspaceDocument("orders",1,"{\"version\":1}"));wrote()
            for(c in binding.carts.filter {it.location!="current"}) {
                val parked=ParkedOrder(c.origin,1,adopted.getValue(c.origin).cart,c.operation.sourceTimestamp ?: c.operation.createdAt ?: fail("Нет даты заказа"))
                db.workspace().put(WorkspaceDocument("parked:${c.origin}",1,WorkspaceCodec.parked(parked)));wrote()
            }
            current?.let {c->
                val a=adopted.getValue(c.origin);val op=c.operation
                val draft=CheckoutDraft(CatalogRules.fingerprint("web.native.checkout.v1",op.origin),op.origin,1,op.candidateShiftId ?: fail("Нет смены заказа"),1,op.candidateActorId ?: fail("Нет кассира"),a.discounts,a.delivery,op.payment?.parts.orEmpty().map {PaymentInput(it.method,it.amount,it.given)})
                db.workspace().put(WorkspaceDocument("checkout",1,CheckoutCodec.encode(draft)));wrote()
                op.payment?.let {payment->
                    val paid=payment.parts.filter {it.paid}.map {it.position}.toSet()
                    val progress=SplitProgress(1,SavedCheckout(1,draft),paid,format=3,importedOrigin=op.origin,importedParts=paid.associate {it.toLong()+1 to it})
                    db.workspace().put(WorkspaceDocument("split-progress",1,SplitPaymentRepository(db).payload(progress)));wrote()
                    WebPaidProvenance.validate(db,progress)
                }
            }
        }
    }
    companion object {const val BASELINE="web-import-baseline";const val BASIS="web-catalog-basis"}
    private fun fail(message: String): Nothing=throw BackupFailure("webCandidate",message)
}
