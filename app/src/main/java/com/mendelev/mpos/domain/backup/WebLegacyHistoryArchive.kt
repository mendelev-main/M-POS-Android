package com.mendelev.mpos.domain.backup

import java.security.MessageDigest
import java.math.BigInteger

/** Frozen, allowlisted source history; never executable native commands or a complete Web import. */
class WebLegacyHistoryArchive internal constructor(val payload: String,val sourceSha256: String,val sourceVersion: Int,
    val receipts: List<WebHistoricalReceipt>,val warehouse: WebWarehouseHistoryFacts,val omitted: Map<String,Int>) {
    override fun toString()="WebLegacyHistoryArchive(receipts=${receipts.size}, omitted=${omitted.values.sum()})"
}
object WebLegacyHistoryArchiveCodec {
    const val MAX_BYTES=16*1024*1024
    private val hash=Regex("[0-9a-f]{64}")
    private fun keys(s: String)=s.split(' ').toSet()
    private val receivingLine=keys("productId productName qty stockUnit unitCost totalCost invoiceQty invoiceUnit invoiceUnitPrice packSize contentUnit orderedQty")
    private val purchaseLine=keys("productId productName qty requestedQty requestedUnit stockUnit contentUnit expectedQty packSize timestamp")
    private val inventoryLine=keys("productId name unit expected actual difference fixedAt cost lossAmount")
    private val invoiceLine=keys("productId qtyInput unit packSize contentUnit priceInput totalInput priceMode")
    private val payment=keys("method amount cashGiven change paid")
    private fun filter(document: WebBackupDocument): Pair<BackupJson.Obj,Map<String,Int>> {
        // Validate the original before excluding unsupported fields. Exclusion must not repair malformed money.
        WebReceiptHistory.map(document);WebWarehouseHistory.map(document);WebStaffMapping.map(document)
        val omitted=linkedMapOf<String,Int>()
        fun pick(value: BackupJson,allowed: Set<String>,path: String,nested: Map<String,(BackupJson)->BackupJson> = emptyMap()): BackupJson.Obj {
            val p=(value as? BackupJson.Obj)?.fields ?: backupBad("legacyHistory")
            p["currency"]?.takeUnless {it===BackupJson.Nil}?.let {if((it as? BackupJson.Str)?.value!="BYN") backupBad("legacyHistoryCurrency")}
            val result=linkedMapOf<String,BackupJson>()
            p.forEach {(key,v)->
                if(key !in allowed && key !in nested) {val name="$path.$key";omitted[name]=(omitted[name] ?: 0)+1}
                else result[key]=if(v===BackupJson.Nil) v else nested[key]?.invoke(v) ?: v.also {if(it is BackupJson.Obj || it is BackupJson.Arr) backupBad("legacyHistoryShape")}
            }
            return BackupJson.Obj(result)
        }
        fun array(v: BackupJson,path: String,row: (BackupJson)->BackupJson): BackupJson.Arr {
            if(v !is BackupJson.Arr) backupBad("legacyHistoryShape")
            return BackupJson.Arr(v.values.map(row))
        }
        fun invoice(v: BackupJson,path: String)=pick(v,keys("version orderId supplierId invoiceNumber invoiceDate"),path,mapOf(
            "lines" to {a->array(a,path) {pick(it,invoiceLine,"$path.lines")}}))
        fun stock(v: BackupJson)=pick(v,setOf("version"),"orders.stockConsumption",mapOf("items" to {a->array(a,"stock") {pick(it,keys("productId qty unit"),"orders.stockConsumption.items")}}))
        val data=linkedMapOf<String,BackupJson>()
        val source=document.root.fields
        fun section(name: String,row: (BackupJson)->BackupJson) {data[name]=BackupJson.Arr(WebValues.rows(source[name]).map(row))}
        section("orders") {pick(it,keys("id timestamp shiftId employeeId employeeName registerName receiptNumber receiptDisplayNumber total method cashGiven change deliveryFee orderType orderLabel orderComment returnedAt returnedShiftId returnAmount currency productDiscountTotal subtotalBeforeDiscounts loyaltyDiscount"),"orders",mapOf(
            "payments" to {a->array(a,"orders.payments") {p->pick(p,payment,"orders.payments")}},
            "stockConsumption" to ::stock,
            "items" to {a->array(a,"orders.items") {line->pick(line,keys("productId cartLineId name category qty price basePrice manualPrice cost unit stockUnit comment discountId discountName discountType discountValue"),"orders.items",mapOf(
                "selectedModifiers" to {m->array(m,"modifiers") {pick(it,keys("groupId groupName optionId productId name qty priceDelta"),"orders.items.selectedModifiers")}}))}}
        ))}
        section("receivings") {pick(it,keys("id type purchaseOrderId supplierId supplierName timestamp totalCost shortage adminDeleted deletedAt deletedByEmployeeId deletedByEmployeeName invoiceNumber invoiceDate currency")+receivingLine,"receivings",mapOf("items" to {a->array(a,"receivings.items") {pick(it,receivingLine,"receivings.items")}}))}
        section("purchaseOrders") {pick(it,keys("id status timestamp receivedAt deletedAt updatedAt deletedByEmployeeId deletedByEmployeeName supplierId supplierName requestVersion shortage receivingIncomplete"),"purchaseOrders",mapOf(
            "items" to {a->array(a,"purchase.items") {pick(it,purchaseLine,"purchaseOrders.items")}},
            "receivedItems" to {a->array(a,"received.items") {pick(it,receivingLine,"purchaseOrders.receivedItems")}},
            "receivingDraftV2" to {v->invoice(v,"purchaseOrders.receivingDraftV2")},
            "receivingDraft" to {v->val rows=(v as? BackupJson.Obj)?.fields ?: backupBad("legacyHistoryShape");BackupJson.Obj(rows.mapValues {(_,r)->pick(r,keys("qty totalCost"),"purchaseOrders.receivingDraft")})}
        ))}
        fun inventory(v: BackupJson)=pick(v,keys("id type status startedAt scheduledDate completedAt cancelledAt estimatedLoss"),"inventory",mapOf(
            "items" to {a->array(a,"inventory.items") {pick(it,inventoryLine,"inventory.items")}},
            "unfixedItems" to {a->array(a,"inventory.unfixedItems") {pick(it,inventoryLine,"inventory.unfixedItems")}}))
        section("inventoryHistory",::inventory)
        data["inventoryDraft"]=source["inventoryDraft"]?.takeUnless {it===BackupJson.Nil}?.let(::inventory) ?: BackupJson.Nil
        data["receivingDraft"]=source["receivingDraft"]?.takeUnless {it===BackupJson.Nil}?.let {invoice(it,"receivingDraft")} ?: BackupJson.Nil
        section("employees") {pick(it,keys("id name phone role"),"employees")}
        section("shifts") {pick(it,keys("id employeeId employeeName employeePhone status openedAt closedAt openingCash countedCash expectedCash difference openingSourceShiftId"),"shifts",mapOf("cashMovements" to {a->array(a,"shifts.cashMovements") {pick(it,keys("id type subtype amount timestamp note"),"shifts.cashMovements")}}))}
        return BackupJson.Obj(data) to omitted.toMap()
    }
    fun encode(document: WebBackupDocument): WebLegacyHistoryArchive {
        val (data,omitted)=filter(document)
        val root=BackupJson.Obj(mapOf("version" to BackupJson.Num("1"),"currency" to BackupJson.Str("BYN"),"sourceVersion" to BackupJson.Num(document.preview.version.toString()),
            "sourceSha256" to BackupJson.Str(document.preview.sha256),"data" to data,"omitted" to BackupJson.Obj(omitted.mapValues {BackupJson.Num(it.value.toString())})))
        return decode(write(root))
    }
    fun decode(payload: String): WebLegacyHistoryArchive {
        if(payload.toByteArray(Charsets.UTF_8).size>MAX_BYTES) backupBad("legacyHistoryLimit")
        val root=(BackupJsonParser(payload).parse() as? BackupJson.Obj)?.fields ?: backupBad("legacyHistory")
        if(root.keys!=setOf("version","currency","sourceVersion","sourceSha256","data","omitted") || (root["version"] as? BackupJson.Num)?.raw!="1" || (root["currency"] as? BackupJson.Str)?.value!="BYN") backupBad("legacyHistoryVersion")
        val version=(root["sourceVersion"] as? BackupJson.Num)?.raw?.toIntOrNull()?.takeIf {it in 1..13} ?: backupBad("legacyHistoryVersion")
        val sha=(root["sourceSha256"] as? BackupJson.Str)?.value?.takeIf {hash.matches(it)} ?: backupBad("legacyHistory")
        val data=root["data"] as? BackupJson.Obj ?: backupBad("legacyHistory")
        if(data.fields.keys!=setOf("orders","receivings","purchaseOrders","inventoryHistory","inventoryDraft","receivingDraft","employees","shifts")) backupBad("legacyHistory")
        val omitted=(root["omitted"] as? BackupJson.Obj)?.fields?.mapValues {(_,v)->(v as? BackupJson.Num)?.raw?.toIntOrNull()?.takeIf {it in 1..400_000} ?: backupBad("legacyHistory")} ?: backupBad("legacyHistory")
        // Re-encoding the filtered view must not exclude anything: no arbitrary secrets/runtime objects in the archive.
        val doc=view(data,sha,version)
        val checked=filter(doc);if(checked.second.isNotEmpty() || write(checked.first)!=write(data)) backupBad("legacyHistoryFields")
        val receipts=WebReceiptHistory.map(doc);val warehouse=WebWarehouseHistory.map(doc);WebStaffMapping.map(doc)
        return WebLegacyHistoryArchive(payload,sha,version,receipts,warehouse,omitted)
    }
    internal fun view(data: BackupJson.Obj,sha: String,version: Int)=WebBackupDocument(data,emptyMap(),WebBackupPreview(version,0,sha,emptyMap(),0,0,0,0,0,0,BigInteger.ZERO,BigInteger.ZERO,false,emptyList(),0))
    fun digest(text: String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
    internal fun write(v: BackupJson): String=when(v) {
        is BackupJson.Obj->v.fields.toSortedMap().entries.joinToString(",","{","}") {quote(it.key)+":"+write(it.value)}
        is BackupJson.Arr->v.values.joinToString(",","[","]",transform=::write)
        is BackupJson.Str->quote(v.value);is BackupJson.Num->v.raw;is BackupJson.Bool->v.value.toString();BackupJson.Nil->"null"
    }.also {if(it.toByteArray(Charsets.UTF_8).size>MAX_BYTES) backupBad("legacyHistoryLimit")}
    private fun quote(s: String)=buildString {append('"');for(c in s) when(c) {'"'->append("\\\"");'\\'->append("\\\\");else->if(c<' ') {append("\\u");append(c.code.toString(16).padStart(4,'0'))} else append(c)};append('"')}
}
