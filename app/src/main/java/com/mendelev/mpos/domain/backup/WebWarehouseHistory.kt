package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.CatalogRules

/** These are source snapshots, never ReceivingPost/InventoryFix commands or new stock movements. */
data class WebReceivingLine(val position: Int,val productId: String?,val name: String?,val quantity: String?,val unit: String?,
    val unitCost: String?,val totalCost: String?,val invoiceUnitPrice: String?,val invoiceQuantity: String?,val invoiceUnit: String?,val packSize: String?,val contentUnit: String?,val deferredFields: Set<String>)
data class WebReceivingHistory(val id: String,val sourceId: String?,val timestamp: Long?,val orderId: String?,val supplierId: String?,val supplierName: String?,
    val totalCost: String?,val type: String?,val invoiceNumber: String?,val invoiceDate: String?,val adminDeleted: Boolean,val shortage: Boolean?,
    val lines: List<WebReceivingLine>,val coverage: Set<String>,val deferredFields: Set<String>)
data class WebPurchaseLine(val position: Int,val productId: String?,val name: String?,val quantity: String?,val expected: String?,val requested: String?,
    val requestedUnit: String?,val stockUnit: String?,val packSize: String?,val contentUnit: String?,val deferredFields: Set<String>)
data class WebPurchaseHistory(val id: String,val sourceId: String?,val status: String?,val timestamp: Long?,val receivedAt: Long?,val deletedAt: Long?,
    val supplierId: String?,val supplierName: String?,val requestVersion: Long?,val lines: List<WebPurchaseLine>,val receivedLines: List<WebReceivingLine>?,val coverage: Set<String>,val deferredFields: Set<String>)
data class WebInventoryLine(val position: Int,val productId: String?,val name: String?,val unit: String?,val expected: String?,val actual: String?,
    val difference: String?,val fixedAt: Long?,val requiresRecount: Boolean,val deferredFields: Set<String>)
data class WebInventoryHistory(val id: String,val sourceId: String?,val state: String,val type: String?,val startedAt: Long?,val scheduledDate: Long?,
    val completedAt: Long?,val cancelledAt: Long?,val estimatedLoss: String?,val lines: List<WebInventoryLine>,val unfixedLines: List<WebInventoryLine>,val coverage: Set<String>,val deferredFields: Set<String>)
sealed interface WebDraftScalar {
    data object Missing: WebDraftScalar
    data object Nil: WebDraftScalar
    data class Text(val value: String): WebDraftScalar
    data class Number(val raw: String): WebDraftScalar
}
data class WebReceivingDraftLine(val position: Int,val productId: String?,val quantity: WebDraftScalar,val price: WebDraftScalar,val total: WebDraftScalar,
    val packSize: WebDraftScalar,val unit: String?,val contentUnit: String?,val priceMode: String?,val deferredFields: Set<String>)
data class WebReceivingDraft(val location: String,val kind: String,val parentOrderId: String?,val orderId: String?,val supplierId: String?,
    val invoiceNumber: String?,val invoiceDate: String?,val lines: List<WebReceivingDraftLine>,val coverage: Set<String>,val deferredFields: Set<String>)
data class WebWarehouseHistoryFacts(val receivings: List<WebReceivingHistory>,val purchases: List<WebPurchaseHistory>,val inventories: List<WebInventoryHistory>,
    val inventoryDraft: WebInventoryHistory?,val receivingDrafts: List<WebReceivingDraft>,val linkCoverage: Set<String>) {
    override fun toString()="WebWarehouseHistoryFacts(receivings=${receivings.size}, purchases=${purchases.size}, inventories=${inventories.size})"
}

object WebWarehouseHistory {
    private fun text(v: BackupJson?)=v?.takeUnless {it===BackupJson.Nil}?.let {WebValues.text(it)}
    private fun ref(v: BackupJson?)=v?.takeUnless {it===BackupJson.Nil || it is BackupJson.Str && it.value.isEmpty()}?.let(WebIdentity::source)
    private fun decimal(v: BackupJson?,signed: Boolean=false)=v?.takeUnless {it===BackupJson.Nil}?.let {CatalogRules.canonical(WebValues.decimal(it,signed=signed))}
    private fun bool(v: BackupJson?): Boolean?=v?.takeUnless {it===BackupJson.Nil}?.let {WebValues.bool(it)}
    private fun scalar(v: BackupJson?): WebDraftScalar=when(v) {
        null->WebDraftScalar.Missing;BackupJson.Nil->WebDraftScalar.Nil;is BackupJson.Str->WebDraftScalar.Text(v.value)
        is BackupJson.Num->WebDraftScalar.Number(v.raw);else->backupBad("warehouseDraft")
    }
    fun map(document: WebBackupDocument): WebWarehouseHistoryFacts {
        val root=document.root.fields
        fun identity(kind: String,index: Int,p: Map<String,BackupJson>,coverage: MutableSet<String>): Pair<String,String?> {
            val id=ref(p["id"])
            if(id==null) coverage.add("sourceDocumentIdUnknown")
            val key=if(id==null) CatalogRules.fingerprint("anonymous",document.preview.sha256,index) else id
            return WebIdentity.mapped(kind,key) to id
        }
        fun receivingLines(rows: List<BackupJson>,coverage: MutableSet<String>): List<WebReceivingLine> =rows.mapIndexed {i,v->val p=WebValues.obj(v)
            val qty=decimal(p["qty"]);val total=decimal(p["totalCost"]);val unitCost=decimal(p["unitCost"])
            if(qty==null) coverage.add("receivedQuantityUnknown")
            if(total==null) coverage.add("lineCostUnknown")
            if(total!=null && total.toBigDecimal().movePointRight(2).stripTrailingZeros().scale()>0) coverage.add("sourceFractionalMoney")
            if(qty=="0" && total!=null && total!="0") coverage.add("zeroQuantityValueNeedsReconciliation")
            val unit=text(p["stockUnit"]);if(unit.isNullOrEmpty()) coverage.add("stockUnitUnknown")
            if(ref(p["productId"])==null) coverage.add("productReferenceUnknown")
            WebReceivingLine(i,ref(p["productId"]),text(p["productName"]),qty,unit,unitCost,total,decimal(p["invoiceUnitPrice"]),decimal(p["invoiceQty"]),text(p["invoiceUnit"]),
                decimal(p["packSize"]),text(p["contentUnit"]),p.keys-setOf("productId","productName","qty","stockUnit","unitCost","totalCost","invoiceUnitPrice","invoiceQty","invoiceUnit","packSize","contentUnit"))
        }
        val receivings=WebValues.rows(root["receivings"]).mapIndexed {i,v->val p=WebValues.obj(v);val c=linkedSetOf<String>();val (id,sid)=identity("receiving",i,p,c)
            val raw=p["items"]
            val rows=when(raw) {is BackupJson.Arr->raw.values;null,BackupJson.Nil->if(p["productId"]!=null) {c.add("legacyFlatReceiving");listOf(v)} else {c.add("receivingLinesUnknown");emptyList()};else->backupBad("warehouseReceiving")}
            val deleted=bool(p["adminDeleted"]) ?: false
            if(deleted) c.add("nonPostingDeletionMarker")
            val time=WebValues.time(p["timestamp"]);if(time==null)c.add("receivingTimeUnknown")
            val total=decimal(p["totalCost"]);if(total==null)c.add("documentCostUnknown")
            val currency=text(p["currency"]);if(currency!=null && currency!="BYN")backupBad("warehouseCurrency")
            if(currency==null)c.add("currencyFromImportProfile")
            val type=text(p["type"]);if(type !in setOf("purchase","purchaseOrder"))c.add("receivingTypeUnknown")
            val lines=receivingLines(rows,c)
            if(!deleted && total!=null && lines.isNotEmpty() && lines.all {it.totalCost!=null} && lines.fold(java.math.BigDecimal.ZERO) {sum,line->sum+line.totalCost!!.toBigDecimal()}.compareTo(total.toBigDecimal())!=0) c.add("documentLineTotalReconciliationPending")
            WebReceivingHistory(id,sid,time,ref(p["purchaseOrderId"]),ref(p["supplierId"]),text(p["supplierName"]),total,type,text(p["invoiceNumber"]),text(p["invoiceDate"]),
                deleted,bool(p["shortage"]),lines,c,p.keys-setOf("id","timestamp","purchaseOrderId","supplierId","supplierName","totalCost","invoiceNumber","invoiceDate","adminDeleted","shortage","items","type","currency"))
        }
        val drafts=mutableListOf<WebReceivingDraft>()
        fun draft(v: BackupJson,location: String,parent: String?,terminal: Boolean,legacy: Boolean=false): WebReceivingDraft {
            val p=WebValues.obj(v);val c=linkedSetOf<String>()
            if(terminal)c.add("draftForTerminalOrder")
            if(legacy)c.add("legacyReceivingDraftNeedsMapping") else if(WebValues.time(p["version"])!=1L) backupBad("warehouseDraftVersion")
            val order=if(legacy) parent else ref(p["orderId"])
            if(parent!=null && order!=null && parent!=order) backupBad("warehouseDraftOrder")
            if(parent!=null && order==null)c.add("draftOrderBindingUnknown")
            val source=if(legacy) p.entries.map {(key,value)->BackupJson.Obj(WebValues.obj(value)+("productId" to BackupJson.Str(key)))} else {
                if(p["lines"] !is BackupJson.Arr) backupBad("warehouseDraft")
                WebValues.rows(p["lines"])
            }
            val lines=source.mapIndexed {i,row->val f=WebValues.obj(row)
                WebReceivingDraftLine(i,ref(f["productId"]),scalar(f[if(legacy) "qty" else "qtyInput"]),scalar(f["priceInput"]),scalar(f[if(legacy) "totalCost" else "totalInput"]),
                    scalar(f["packSize"]),text(f["unit"]),text(f["contentUnit"]),text(f["priceMode"]),f.keys-setOf("productId","qtyInput","priceInput","totalInput","packSize","unit","contentUnit","priceMode","qty","totalCost"))
            }
            return WebReceivingDraft(location,if(legacy) "legacy" else "v1",parent,order,ref(p["supplierId"]),text(p["invoiceNumber"]),text(p["invoiceDate"]),lines,c,
                if(legacy) emptySet() else p.keys-setOf("version","orderId","supplierId","invoiceNumber","invoiceDate","lines"))
        }
        val purchases=WebValues.rows(root["purchaseOrders"]).mapIndexed {i,v->val p=WebValues.obj(v);val c=linkedSetOf<String>();val (id,sid)=identity("purchaseOrder",i,p,c)
            val status=text(p["status"]);if(status !in setOf("pending","received","deleted"))c.add("purchaseStatusUnknown")
            val lines=WebValues.rows(p["items"]).mapIndexed {j,row->val f=WebValues.obj(row)
                val expected=if("expectedQty" in f) decimal(f["expectedQty"]) else {c.add("expectedFromLegacyQuantity");decimal(f["qty"])}
                if(expected==null)c.add("expectedQuantityUnknown")
                WebPurchaseLine(j,ref(f["productId"]),text(f["productName"]),decimal(f["qty"]),expected,decimal(f["requestedQty"]),text(f["requestedUnit"]),text(f["stockUnit"]),decimal(f["packSize"]),text(f["contentUnit"]),
                    f.keys-setOf("productId","productName","qty","expectedQty","requestedQty","requestedUnit","stockUnit","packSize","contentUnit"))
            }
            if(lines.isEmpty())c.add("purchaseLinesUnknown")
            val received=p["receivedItems"]?.takeUnless {it===BackupJson.Nil}?.let {receivingLines(WebValues.rows(it),c)}
            if(status=="received" && received==null)c.add("receivedSnapshotUnknown")
            val receivedAt=WebValues.time(p["receivedAt"]);val deletedAt=WebValues.time(p["deletedAt"])
            if(status=="received" && receivedAt==null)c.add("receivedTimeUnknown")
            if(status=="pending" && (receivedAt!=null || deletedAt!=null))c.add("purchaseStatusTimeConflict")
            val terminal=status in setOf("received","deleted") || receivedAt!=null || deletedAt!=null
            p["receivingDraftV2"]?.takeUnless {it===BackupJson.Nil}?.let {drafts.add(draft(it,"purchase:$i:v2",sid,terminal))}
            p["receivingDraft"]?.takeUnless {it===BackupJson.Nil}?.let {drafts.add(draft(it,"purchase:$i:legacy",sid,terminal,true))}
            if(p["receivingDraftV2"]!=null && p["receivingDraft"]!=null)c.add("legacyDraftShadowedByV2")
            WebPurchaseHistory(id,sid,status,WebValues.time(p["timestamp"]),receivedAt,deletedAt,ref(p["supplierId"]),text(p["supplierName"]),WebValues.time(p["requestVersion"]),lines,received,c,
                p.keys-setOf("id","status","timestamp","receivedAt","deletedAt","supplierId","supplierName","requestVersion","items","receivedItems","receivingDraftV2","receivingDraft"))
        }
        root["receivingDraft"]?.takeUnless {it===BackupJson.Nil}?.let {drafts.add(draft(it,"standalone",null,false))}
        fun inventory(v: BackupJson,index: Int,isDraft: Boolean): WebInventoryHistory {
            val p=WebValues.obj(v);val c=linkedSetOf<String>();val (id,sid)=identity("inventory",index,p,c)
            fun rows(raw: BackupJson?): List<WebInventoryLine> = WebValues.rows(raw).mapIndexed {i,row->val f=WebValues.obj(row)
                val fixed=WebValues.time(f["fixedAt"])?.takeUnless {it==0L};val expected=decimal(f["expected"],true);val actual=decimal(f["actual"]);val difference=decimal(f["difference"],true)
                if(fixed!=null) {
                    c.add("fixedRowsAlreadyApplied")
                    if(actual==null || expected==null || difference==null)c.add("fixedFactsIncomplete")
                } else if(isDraft)c.add("unfixedRowsRequireRecount")
                if(f["_androidCountBaseline"]!=null)c.add("sourceRuntimeBaselineDiscarded")
                if(actual!=null && expected!=null && difference!=null && (actual.toBigDecimal()-expected.toBigDecimal()).compareTo(difference.toBigDecimal())!=0) c.add("differenceReconciliationPending")
                val unit=text(f["unit"]);if(unit.isNullOrEmpty())c.add("inventoryUnitUnknown")
                WebInventoryLine(i,ref(f["productId"]),text(f["name"]),unit,expected,actual,difference,fixed,isDraft && fixed==null,
                    f.keys-setOf("productId","name","unit","expected","actual","difference","fixedAt","_androidCountBaseline"))
            }
            if(p["items"] !is BackupJson.Arr)c.add("inventoryRowsUnknown")
            val items=rows(p["items"]);val unfixed=rows(p["unfixedItems"])
            val ids=(items+unfixed).mapNotNull {it.productId};if(ids.distinct().size!=ids.size)backupBad("warehouseInventory")
            val done=WebValues.time(p["completedAt"]);val cancelled=WebValues.time(p["cancelledAt"])
            if(isDraft && (done!=null || cancelled!=null))c.add("draftCarriesTerminalTimes")
            val state=if(isDraft) "draft" else if(text(p["status"])=="cancelled" || cancelled!=null) "cancelled" else if(done!=null) "completed" else "unknown"
            if(state=="unknown")c.add("inventoryStateUnknown")
            if(state=="completed" && items.isEmpty())c.add("completedInventoryEmpty")
            if(state=="completed" && items.any {it.fixedAt==null})c.add("completedInventoryHasUnfixedRows")
            return WebInventoryHistory(id,sid,state,text(p["type"]),WebValues.time(p["startedAt"]),WebValues.time(p["scheduledDate"]),done,cancelled,decimal(p["estimatedLoss"]),items,unfixed,c,
                p.keys-setOf("id","type","status","startedAt","scheduledDate","completedAt","cancelledAt","estimatedLoss","items","unfixedItems"))
        }
        val inventories=WebValues.rows(root["inventoryHistory"]).mapIndexed {i,v->inventory(v,i,false)}
        val current=root["inventoryDraft"]?.takeUnless {it===BackupJson.Nil}?.let {inventory(it,-1,true)}
        fun unique(ids: List<String>) {if(ids.distinct().size!=ids.size)backupBad("warehouseIdentity")}
        unique(receivings.map {it.id});unique(purchases.map {it.id});unique((inventories+listOfNotNull(current)).map {it.id})
        val links=linkedSetOf<String>();val orders=purchases.mapNotNull {it.sourceId}.toSet()
        if(receivings.any {it.orderId!=null && it.orderId !in orders})links.add("receivingOrderReferenceMissing")
        if(drafts.any {it.orderId!=null && it.orderId !in orders})links.add("draftOrderReferenceMissing")
        if(receivings.filter {!it.adminDeleted && it.orderId!=null}.groupBy {it.orderId}.any {it.value.size>1})links.add("multipleReceivingsPerOrder")
        for(order in purchases.filter {it.status=="received" && it.sourceId!=null}) {
            val records=receivings.filter {!it.adminDeleted && it.orderId==order.sourceId}
            if(records.isEmpty())links.add("receivedOrderWithoutReceivingHistory")
            if(records.size==1 && order.receivedLines!=null && records.single().lines!=order.receivedLines)links.add("receivedSnapshotMismatch")
        }
        val nonPending=purchases.filter {it.status!="pending" || it.receivedAt!=null || it.deletedAt!=null}.mapNotNull {it.sourceId}.toSet()
        if(drafts.any {it.orderId in nonPending})links.add("draftReferencesNonPendingOrder")
        return WebWarehouseHistoryFacts(receivings,purchases,inventories,current,drafts.toList(),links)
    }
}
