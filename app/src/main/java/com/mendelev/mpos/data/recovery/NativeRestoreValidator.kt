package com.mendelev.mpos.data.recovery

import android.content.Context
import androidx.room.Room
import com.mendelev.mpos.data.backup.NativeSnapshotDatabase
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Validates an independent copy. Never initializes, reprices, repairs or writes into the supplied snapshot. */
internal object NativeRestoreValidator {
    suspend fun validate(context: Context,files: NativeBackupFiles,directory: File) {
        NativeSnapshotDatabase.validate(files)
        val candidateName=File(directory,"validate-${UUID.randomUUID()}.db").absolutePath
        val db=Room.databaseBuilder(context,CatalogDatabase::class.java,candidateName).build()
        try {
            val target=db.openHelper.writableDatabase
            // READONLY SQLite sees committed WAL. Never copy a live main-file without its WAL.
            android.database.sqlite.SQLiteDatabase.openDatabase(files.database.absolutePath,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY or android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS).use {source->
                for(table in NativeSnapshotDatabase.tables) require(NativeSchema.equal(target,source,table)) {"schema"}
                target.execSQL("PRAGMA foreign_keys=OFF")
                target.beginTransaction()
                try {
                    for(t in NativeSnapshotDatabase.tables) target.execSQL("DELETE FROM "+quote(t))
                    var rowCount=0L
                    for(t in NativeSnapshotDatabase.tables) {
                        val columns=target.query("SELECT * FROM "+quote(t)+" LIMIT 0").use {it.columnNames}
                        val types=target.query("PRAGMA table_info("+quote(t)+")").use {c->buildMap {while(c.moveToNext()) put(c.getString(c.getColumnIndexOrThrow("name")),c.getString(c.getColumnIndexOrThrow("type")))}}
                        source.rawQuery("SELECT "+columns.joinToString(",",transform=::quote)+" FROM "+quote(t),null).use {rows->
                            val sql="INSERT INTO "+quote(t)+" ("+columns.joinToString(",",transform=::quote)+") VALUES ("+List(columns.size){"?"}.joinToString(",")+")"
                            target.compileStatement(sql).use {statement->while(rows.moveToNext()) {
                                currentCoroutineContext().ensureActive();require(++rowCount<=2_000_000);statement.clearBindings()
                                for(i in columns.indices) {
                                    val type=rows.getType(i)
                                    if(type!=android.database.Cursor.FIELD_TYPE_NULL) require(type==when(types[columns[i]]?.uppercase(java.util.Locale.ROOT)) {"INTEGER"->android.database.Cursor.FIELD_TYPE_INTEGER;"TEXT"->android.database.Cursor.FIELD_TYPE_STRING;"BLOB"->android.database.Cursor.FIELD_TYPE_BLOB;else->error("unexpected affinity")})
                                    if(t=="products" && columns[i] in setOf("noStockTracking","allowNegativeStock")) require(rows.getLong(i) in 0L..1L)
                                    when(type) {
                                    android.database.Cursor.FIELD_TYPE_NULL->statement.bindNull(i+1)
                                    android.database.Cursor.FIELD_TYPE_INTEGER->statement.bindLong(i+1,rows.getLong(i))
                                    android.database.Cursor.FIELD_TYPE_STRING->statement.bindString(i+1,rows.getString(i))
                                    android.database.Cursor.FIELD_TYPE_BLOB->statement.bindBlob(i+1,rows.getBlob(i))
                                    else->error("unexpected float")
                                }}
                                statement.executeInsert()
                            }}
                        }
                    }
                    target.query("PRAGMA foreign_key_check").use {require(!it.moveToFirst())}
                    target.setTransactionSuccessful()
                } finally {target.endTransaction()}
            }
            domain(db)
        } catch(e:kotlinx.coroutines.CancellationException) {throw e}
        catch(_:Exception) {throw BackupFailure("restoreValidation","Копия не прошла проверку структуры и связей. Ничего не применено")}
        finally {db.close();context.deleteDatabase(candidateName)}
    }
    private fun quote(s: String)="\""+s.replace("\"","\"\"")+"\""
    private fun strict(raw: String) {
        fun visit(j: BackupJson) {when(j) {
            is BackupJson.Obj->j.fields.values.forEach(::visit)
            is BackupJson.Arr->j.values.forEach(::visit)
            is BackupJson.Num->require(j.raw.toLongOrNull()!=null)
            else->Unit
        }}
        visit(BackupJsonParser(raw).parse())
    }
    private fun number(j: JSONObject,key: String,nullable: Boolean=false) {
        val value=j.get(key);require(nullable && value===JSONObject.NULL || value is Number && value.toString().toLongOrNull()!=null)
    }
    private fun checkoutShape(j: JSONObject) {
        listOf("version","cartRevision","shiftRevision").forEach {number(j,it)}
        val parts=j.getJSONArray("parts");for(i in 0 until parts.length()) {val p=parts.getJSONObject(i);number(p,"amount");number(p,"given",true)}
        if(!j.isNull("delivery")) j.getJSONObject("delivery").let {number(it,"fee");number(it,"revision")}
    }
    private fun receiptShape(j: JSONObject) {
        listOf("version","gross","subtotal","discount","total","cash","card","deliveryCashOut","expectedCashAfter").forEach {number(j,it)}
        checkoutShape(j.getJSONObject("checkout"))
        val lines=j.getJSONArray("lines");for(i in 0 until lines.length()) listOf("gross","discount","net").forEach {number(lines.getJSONObject(i),it)}
        val parts=j.getJSONArray("payments");for(i in 0 until parts.length()) {val p=parts.getJSONObject(i);number(p,"amount");number(p,"given",true);number(p,"change",true)}
    }
    private suspend fun domain(db: CatalogDatabase) {
        val c=db.catalog();val products=c.allProducts();val ids=products.map {it.id}.toSet();val balances=c.allBalances().associateBy {it.productId}
        val config=c.config();require(config!=null || products.isEmpty() && db.workspace().documents().isEmpty() && db.shifts().history().isEmpty())
        if(config==null) {for(t in NativeSnapshotDatabase.tables.filter {it!="room_master_table"}) db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM "+t).use {require(it.moveToFirst() && it.getLong(0)==0L)};return}
        config?.let {require(it.currency=="BYN" && it.registerId.isNotBlank() && it.ownerId.isNotBlank())}
        for(cat in c.categorySnapshot()) {require(cat.revision>=1 && CatalogRules.name(cat.name)==cat.name && CatalogRules.key(cat.name)==cat.normalizedKey);CategoryAppearance.color(cat.color);CategoryAppearance.symbol(cat.symbol)}
        val components=c.allComponents();val groups=c.allModifierGroups();val options=c.allModifierOptions()
        for(p in products) {
            currentCoroutineContext().ensureActive();require(p.revision>=1 && p.currency=="BYN")
            val b=balances[p.id];require((p.type=="simple")== (b!=null));b?.let {require(it.revision>=0)}
            val modifiers=groups.filter {it.ownerId==p.id}.map {g->ModifierGroupInput(g.id,g.name,g.minimum.toString(),g.maximum.toString(),options.filter {it.ownerId==p.id && it.groupId==g.id}.map {ModifierOptionInput(it.id,it.productId,it.posName,it.quantity,it.priceDelta)})}
            ProductInput(p.id,p.revision,p.name,p.categoryId,p.price,p.cost,b?.quantity ?: "0",b?.revision,false,"restore-validate",p.type,p.unit,p.displayUnit,p.noStockTracking,p.allowNegativeStock,p.minimumStock,p.purchaseLabel,p.packSize,p.recipeYield,components.filter {it.ownerId==p.id}.map {IngredientInput(it.productId,it.quantity,it.unit)},modifiers,p.photoId).validated()
            ModifierRules.validate(p.id,modifiers,ids)
        }
        val graph=recipeSnapshot(products,components);val recipes=RecipeCalculator(graph)
        graph.filterIsInstance<RecipeProduct.Composite>().forEach {val request=listOf(RecipeRequest(it.id,BigDecimal.ONE,it.unit));recipes.consumption(request);recipes.cost(request)}
        for(m in c.movements()) {require(m.delta.toBigDecimal().scale()<=12 && m.commandId.isNotBlank());m.unit?.let(::quantityUnit)}
        c.draft()?.let {strict(it.payload);com.mendelev.mpos.feature.products.ProductDraft.parse(it.payload)}
        val docs=db.workspace().documents().associateBy {it.id}
        val known=setOf("current","layout","home","orders","checkout","split-progress",EmployeeCodec.KEY,SupplierCodec.KEY,PurchaseDraftCodec.KEY,PurchaseOrderCodec.KEY,ReceivingCodec.KEY,PostedReceivingCodec.KEY,InventoryCodec.KEY,ShiftCashCodec.KEY)
        for(d in docs.values) {currentCoroutineContext().ensureActive();require(d.revision>=0 && (d.id in known || d.id.startsWith("parked:")));strict(d.payload)}
        docs[EmployeeCodec.KEY]?.let {EmployeeCodec.decode(it.payload)}
        val suppliers=docs[SupplierCodec.KEY]?.let {SupplierCodec.decode(it.payload)}.orEmpty()
        require(suppliers.all {s->s.productIds.all {it in ids}})
        docs[PurchaseDraftCodec.KEY]?.let {PurchaseDraftCodec.decode(it.payload)}
        docs[PurchaseOrderCodec.KEY]?.let {PurchaseOrderCodec.decode(it.payload)}
        docs[ReceivingCodec.KEY]?.let {ReceivingCodec.decode(it.payload)}
        docs[PostedReceivingCodec.KEY]?.let {PostedReceivingCodec.decode(it.payload)}
        docs[InventoryCodec.KEY]?.let {InventoryCodec.decode(it.payload)}
        docs["checkout"]?.let {val j=JSONObject(it.payload);number(j,"version");if(j.has("discarded")) require(j.get("discarded") is Boolean);if(!j.optBoolean("discarded",false)) checkoutShape(j)}
        docs["split-progress"]?.let {
            val j=JSONObject(it.payload);number(j,"version");require(j.get("completed") is Boolean)
            if(!j.getBoolean("completed")) {
                number(j,"checkoutRevision");checkoutShape(j.getJSONObject("checkout"))
                val paid=j.getJSONArray("paid");for(i in 0 until paid.length()) require(paid.get(i) is Number && paid.get(i).toString().toLongOrNull()!=null)
                if(j.getInt("version")==2) {number(j,"nextPartId");val ids=j.getJSONArray("partIds");for(i in 0 until ids.length()) require(ids.get(i) is Number && ids.get(i).toString().toLongOrNull()!=null)}
            }
        }
        val manual=ShiftCashCodec.all(db)
        for(m in manual) {require(c.result(m.command.commandId)?.let {it.resultId==m.id && it.fingerprint==m.command.fingerprint()}==true)}
        if("current" in docs) {
            val raw=docs.getValue("current").payload
            if(WorkspaceCodec.cartVersion(raw)==1) {
                // Pre-payment native carts migrate on first workspace initialization, exactly as before restore.
                require(docs.keys.none {it in setOf("checkout","split-progress") || it.startsWith("parked:")})
                val cart=WorkspaceCodec.cart(raw);RecipeCalculator(cart.graph).consumption(CartEngine.requests(cart))
                WorkspaceCodec.layout(docs.getValue("layout").payload);WorkspaceCodec.home(docs.getValue("home").payload);WorkspaceCodec.registry(docs.getValue("orders").payload)
            } else WorkspaceRepository(db).snapshot()
        } else require(docs.keys.none {it in setOf("layout","home","orders","checkout","split-progress") || it.startsWith("parked:")})
        if(db.shifts().register()!=null) ShiftRepository(db).snapshot() else require(db.shifts().history().isEmpty())
        for(s in db.shifts().history()) {require(s.openingCash>=0 && s.openedAt>=0 && s.registerId==config?.registerId);if(s.closedAt!=null) require(s.closedAt>=s.openedAt && s.countedCash!=null && s.expectedCash!=null && s.difference==Math.subtractExact(s.countedCash,s.expectedCash))}
        val receiptRows=db.sales().history();val receiptById=receiptRows.associateBy {it.id}
        for(r in receiptRows) {
            currentCoroutineContext().ensureActive();strict(r.payload);receiptShape(JSONObject(r.payload));val saved=ReceiptCodec.decode(r.payload)
            val q=SaleMoney.quote(saved.cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,saved.checkout.discounts[it.id])},saved.cart.details.type,saved.checkout.delivery)
            require(q==saved.quote && r.total==q.total && r.cash==saved.payment.cash && r.card==saved.payment.card && r.deliveryCashOut==saved.payment.deliveryCashOut)
            require(r.sessionId==saved.cart.sessionId && r.sessionId==saved.checkout.sessionId && r.commandId==saved.checkout.commandId && r.shiftId==saved.checkout.shiftId)
            val opening=Math.addExact(Math.subtractExact(saved.payment.expectedCashAfter,saved.payment.cash),saved.payment.deliveryCashOut)
            require(SaleMoney.payments(q,saved.checkout.parts,opening)==saved.payment)
            require(c.result(r.commandId)?.resultId==r.id)
            val consumption=RecipeCalculator(saved.cart.graph).consumption(CartEngine.requests(saved.cart)).mapValues {CatalogRules.canonical(it.value)}
            require(consumption==saved.consumption)
        }
        for(r in db.refunds().history()) {
            strict(r.payload);val sale=receiptById[r.receiptId] ?: error("receipt")
            require(r.receiptHash==CatalogRules.fingerprint("refund.receipt.v1",sale.payload) && r.total==sale.total && r.cash==sale.cash && r.card==sale.card && r.deliveryCashCredit==sale.deliveryCashOut && c.result(r.commandId)?.resultId==r.id)
            val j=JSONObject(r.payload);number(j,"cashDelta");number(j,"expectedCashAfter");receiptShape(j.getJSONObject("receipt"));require(j.getInt("version")==1 && j.getString("currency")=="BYN" && j.getLong("cashDelta")==Math.subtractExact(r.deliveryCashCredit,r.cash) && j.getLong("expectedCashAfter")>=0)
            val saved=ReceiptCodec.decode(j.getJSONObject("receipt").toString());val actual=ReceiptCodec.decode(sale.payload)
            require(saved.copy(products=emptyList())==actual.copy(products=emptyList()))
        }
        val progress=SplitPaymentRepository(db).load()
        val checkout=CheckoutRepository(db).load()
        if(progress!=null) {
            require(checkout==progress.checkout);val d=progress.checkout.draft;val w=WorkspaceRepository(db).snapshot();val s=ShiftRepository(db).snapshot()
            require(w.cart.sessionId==d.sessionId && w.cartRevision==d.cartRevision && s.active?.id==d.shiftId && s.active?.actorId==d.actorId && s.revision==d.shiftRevision)
            require(w.cart.lines.none {CartEngine.stale(it,w.products)} && receiptRows.none {it.sessionId==d.sessionId || it.commandId==d.commandId})
            val q=SaleMoney.quote(w.cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,d.discounts[it.id])},w.cart.details.type,d.delivery)
            require(q.total==progress.editable().total)
            SaleMoney.payments(q,d.parts.map {if(it.method=="cash" && it.given==null) it.copy(given=it.amount) else it},s.currentCash)
            for(index in progress.paid) {
                val part=d.parts[index];val id=progress.partIds[index]
                val command=if(progress.format==1) CatalogRules.fingerprint("split.part.command.v1",d.commandId,index) else CatalogRules.fingerprint("split.part.command.v2",d.commandId,id)
                val fp=if(progress.format==1) CatalogRules.fingerprint("split.part.v1",progress.checkout.revision,CheckoutCodec.encode(d),index) else CatalogRules.fingerprint("split.part.v2",CheckoutCodec.encode(d.copy(parts=emptyList())),id,part.method,part.amount,part.given)
                require(c.result(command)?.let {it.fingerprint==fp && it.resultId==command}==true)
            }
        }
    }
}
