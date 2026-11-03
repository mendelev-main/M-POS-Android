package com.mendelev.mpos.domain.backup

import org.json.JSONArray
import org.json.JSONObject

/** Imported source evidence. Never a native confirmation command or a bank SDK acknowledgement. */
data class WebImportedPaidRecord(val origin: String,val location: String,val sourceId: String?,val bindingSha256: String,
    val candidateShiftId: String?,val candidateActorId: String?,val total: Long,val parts: List<WebPaymentPart>,val blockers: Set<String>) {
    val paid: Long get()=parts.filter {it.paid}.sumOf {it.amount}
    val remaining: Long get()=parts.filterNot {it.paid}.sumOf {it.amount}
}
class WebImportedPaymentEvidence internal constructor(val payload: String,val sourceSha256: String,val sourceVersion: Int,val records: List<WebImportedPaidRecord>) {
    override fun toString()="WebImportedPaymentEvidence(records=${records.size})"
}
object WebImportedPaymentEvidenceCodec {
    const val MAX_BYTES=8*1024*1024
    private val hash=Regex("[0-9a-f]{64}")
    private const val MAX_SAFE=9007199254740991L
    private fun json(value: Any?)=value ?: JSONObject.NULL
    /** Versioned digest of the adapter inputs/output, not data-class toString or a current-catalog reprice. */
    private fun binding(cart: WebBoundRecipeCart): String {
        fun objects(rows: List<JSONObject>)=JSONArray(rows)
        val j=JSONObject().put("version",1).put("origin",cart.origin).put("total",json(cart.operation.savedTotal)).put("compared",json(cart.operation.comparedTotal)).put("delivery",json(cart.operation.delivery))
            .put("lines",objects(cart.lines.map {l->val s=l.source
                JSONObject().put("sourceId",json(s.sourceLineId)).put("product",json(s.sourceProductId)).put("name",json(s.name)).put("category",json(s.category)).put("comment",json(s.comment))
                    .put("qty",json(s.quantity)).put("price",json(s.price)).put("basePrice",json(s.basePrice)).put("manual",json(s.manualPrice)).put("unit",json(s.unit))
                    .put("basis",json(l.basis)).put("physicalUnit",json(l.physicalUnit)).put("physicalPerSold",json(l.physicalPerSoldUnit)).put("cost",json(l.costMinor))
                    .put("discount",s.discount?.let {JSONObject().put("id",it.sourceId).put("name",json(it.name)).put("type",it.type).put("value",it.value)} ?: JSONObject.NULL)
                    .put("modifiers",objects(s.modifiers.map {m->JSONObject().put("product",json(m.sourceProductId)).put("group",json(m.groupId)).put("option",json(m.optionId)).put("groupName",json(m.groupName)).put("name",json(m.name)).put("qty",json(m.quantity)).put("delta",json(m.priceDelta))}))
                    .put("requests",l.requestsPerSoldUnit?.let {r->objects(r.map {JSONObject().put("product",it.productId).put("qty",it.quantity.toPlainString()).put("unit",it.unit.name)})} ?: JSONObject.NULL)
            })).put("graph",cart.graph?.let {g->objects(g.map {p->val r=JSONObject().put("id",p.id).put("unit",p.unit.name)
                when(p) {
                    is com.mendelev.mpos.domain.catalog.RecipeProduct.Simple->r.put("type","simple").put("cost",p.cost.toPlainString()).put("trackingOff",p.noStockTracking).put("negative",p.allowNegativeStock)
                    is com.mendelev.mpos.domain.catalog.RecipeProduct.Composite->r.put("type","composite").put("yield",p.yield.toPlainString()).put("components",objects(p.components.map {JSONObject().put("id",it.productId).put("qty",it.quantity.toPlainString()).put("unit",it.unit.name)}))
                };r
            })} ?: JSONObject.NULL)
        return WebLegacyHistoryArchiveCodec.digest(WebLegacyHistoryArchiveCodec.write(BackupJsonParser(j.toString()).parse()))
    }
    fun encode(document: WebBackupDocument): WebImportedPaymentEvidence {
        val recipe=WebRecipeBinding.bind(document);val cash=WebCashReconciliation.compare(document)
        val records=recipe.carts.mapNotNull {cart->val op=cart.operation;val payment=op.payment ?: return@mapNotNull null
            val blockers=(cart.blockers+recipe.blockers+cash.blockers+cash.shifts.flatMap {it.blockers}+"unadoptedSourceEvidence").toSortedSet()
            JSONObject().put("origin",op.origin).put("location",op.location).put("sourceId",json(op.sourceId)).put("bindingSha256",binding(cart))
                .put("candidateShiftId",json(op.candidateShiftId)).put("candidateActorId",json(op.candidateActorId)).put("total",payment.total)
                .put("parts",JSONArray(payment.parts.map {p->JSONObject().put("position",p.position).put("method",p.method).put("amount",p.amount).put("paid",p.paid).put("given",json(p.given)).put("change",json(p.change))}))
                .put("blockers",JSONArray(blockers.toList()))
        }
        val raw=JSONObject().put("version",1).put("purpose","unadopted-source-evidence").put("currency","BYN").put("sourceVersion",document.preview.version).put("sourceSha256",document.preview.sha256).put("records",JSONArray(records)).toString()
        return decode(WebLegacyHistoryArchiveCodec.write(BackupJsonParser(raw).parse()))
    }
    /** Revalidate against the complete source before any future adoption; digest possession alone grants no authority. */
    fun requireSource(evidence: WebImportedPaymentEvidence,document: WebBackupDocument) {
        if(encode(document).payload!=evidence.payload)backupBad("importedPaymentSource")
    }
    fun decode(payload: String): WebImportedPaymentEvidence {
        if(payload.toByteArray(Charsets.UTF_8).size>MAX_BYTES)backupBad("importedPaymentLimit")
        fun obj(v: BackupJson?)=(v as? BackupJson.Obj)?.fields ?: backupBad("importedPaymentShape")
        fun text(v: BackupJson?)=(v as? BackupJson.Str)?.value ?: backupBad("importedPaymentShape")
        fun optional(v: BackupJson?)=if(v===BackupJson.Nil)null else text(v)
        fun integer(v: BackupJson?): Long=(v as? BackupJson.Num)?.raw?.toLongOrNull()?.takeIf {it in 0..MAX_SAFE} ?: backupBad("importedPaymentMoney")
        fun maybe(v: BackupJson?)=if(v===BackupJson.Nil)null else integer(v)
        fun rows(v: BackupJson?)=(v as? BackupJson.Arr)?.values ?: backupBad("importedPaymentShape")
        val root=obj(BackupJsonParser(payload).parse())
        if(root.keys!=setOf("version","purpose","currency","sourceVersion","sourceSha256","records") || integer(root["version"])!=1L || text(root["purpose"])!="unadopted-source-evidence" || text(root["currency"])!="BYN")backupBad("importedPaymentVersion")
        val source=text(root["sourceSha256"]);if(!hash.matches(source))backupBad("importedPaymentSource")
        val sourceVersion=integer(root["sourceVersion"]);if(sourceVersion !in 1L..13L)backupBad("importedPaymentVersion");val version=sourceVersion.toInt()
        val rawRecords=rows(root["records"]);if(rawRecords.size>1000)backupBad("importedPaymentLimit")
        val records=rawRecords.map {v->val r=obj(v)
            if(r.keys!=setOf("origin","location","sourceId","bindingSha256","candidateShiftId","candidateActorId","total","parts","blockers"))backupBad("importedPaymentFields")
            val origin=text(r["origin"]);val location=text(r["location"]);val id=optional(r["sourceId"])
            if(location!="current" && !location.matches(Regex("parked:(0|[1-9][0-9]{0,5})")))backupBad("importedPaymentIdentity")
            if(id!=null && (id.isBlank() || id.length>512))backupBad("importedPaymentIdentity")
            if(origin!="web-open-"+WebLegacyHistoryArchiveCodec.digest(source+"\n"+location+"\n"+(id ?: "")))backupBad("importedPaymentIdentity")
            val digest=text(r["bindingSha256"]);if(!hash.matches(digest))backupBad("importedPaymentBinding")
            val shift=optional(r["candidateShiftId"]);val actor=optional(r["candidateActorId"])
            if(listOfNotNull(shift,actor).any {!it.matches(Regex("web-[0-9a-f]{64}"))})backupBad("importedPaymentIdentity")
            val total=integer(r["total"]);val parts=rows(r["parts"]).mapIndexed {i,pv->val p=obj(pv)
                if(p.keys!=setOf("position","method","amount","paid","given","change") || integer(p["position"])!=i.toLong())backupBad("importedPaymentPart")
                val method=text(p["method"]);val amount=integer(p["amount"]);val paid=(p["paid"] as? BackupJson.Bool)?.value ?: backupBad("importedPaymentPart")
                val given=maybe(p["given"]);val change=maybe(p["change"])
                if(method !in setOf("cash","card") || method=="card" && (given!=null || change!=null) || given!=null && given<amount || given!=null && change!=null && given-amount!=change)backupBad("importedPaymentPart")
                WebPaymentPart(i,method,amount,paid,given,change)
            }
            if(parts.size !in 2..10 || parts.none {it.paid} || parts.sumOf {it.amount}!=total)backupBad("importedPaymentPart")
            val blockers=rows(r["blockers"]).map(::text)
            if(blockers.distinct().size!=blockers.size || blockers.size>500 || blockers.any {!it.matches(Regex("[A-Za-z][A-Za-z0-9:]{0,150}"))} || !blockers.containsAll(setOf("unadoptedSourceEvidence","nativeCartEncodingPending","nativePaidProvenancePending")))backupBad("importedPaymentBlockers")
            if(parts.all {it.paid} && "allPartsPaidReceiptReconciliation" !in blockers || parts.any {it.paid && it.method=="cash" && (it.given==null || it.change==null)} && "paidCashTenderUnknown" !in blockers)backupBad("importedPaymentBlockers")
            WebImportedPaidRecord(origin,location,id,digest,shift,actor,total,parts,blockers.toSet())
        }
        if(records.map {it.origin}.distinct().size!=records.size || records.map {it.location}.distinct().size!=records.size)backupBad("importedPaymentIdentity")
        return WebImportedPaymentEvidence(payload,source,version,records)
    }
}
