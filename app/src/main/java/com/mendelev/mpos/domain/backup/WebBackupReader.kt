package com.mendelev.mpos.domain.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Sanitized summary only: never includes names, contacts, URLs, tokens, original objects or image bytes. */
data class WebBackupPreview(val version: Int,val bytes: Int,val sha256: String,val sections: Map<String,Int>,
    val imageCount: Int,val photoBytes: Long,val externalImages: Int,val inlineImages: Int,val openShifts: Int,val paymentDrafts: Int,
    val paidMinor: BigInteger,val unpaidMinor: BigInteger,val currentSessionPresent: Boolean,val deferredSections: List<String>,val unknownSections: Int)
class WebBackupDocument internal constructor(val root: BackupJson.Obj,val images: Map<String,ByteArray>,val preview: WebBackupPreview) {
    override fun toString()="WebBackupDocument(version=${preview.version}, bytes=${preview.bytes})"
}
object WebBackupReader {
    const val MAX_BYTES=32*1024*1024
    private val arrays=listOf("products","employees","shifts","orders","parked","receivings","suppliers","purchaseOrders","discounts","hallTables","bookings","inventoryHistory","deliveryRates","receiptLabelTemplates","operationalOutbox","webEvents")
    private val objects=listOf("layout","inventoryConfig","currentOrderSession","company","posNavigation","printer","telegram","network","webOrderAcceptances")
    private val nullable=listOf("receivingDraft","inventoryDraft")
    private val core=setOf("products","employees","shifts","orders")
    fun read(input: InputStream): WebBackupDocument {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {val n=input.read(buffer);if(n<0) break;if(n==0) {val b=input.read();if(b<0) break;if(out.size()==MAX_BYTES) backupBad("size");out.write(b)}
            else {if(n>MAX_BYTES-out.size()) backupBad("size");out.write(buffer,0,n)}}
        val bytes=out.toByteArray()
        val text=try {Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")} catch(_:Exception) {backupBad()}
        val root=BackupJsonParser(text).parse() as? BackupJson.Obj ?: backupBad()
        val f=root.fields
        val rawVersion=if(f["version"]==null) BigInteger.ONE else integer(f.getValue("version"))
        if(rawVersion<BigInteger.ONE || rawVersion>13.toBigInteger()) backupBad("version")
        val version=rawVersion.toInt()
        if(version !in 1..13) backupBad("version")
        val full=version==13
        fun array(key: String): List<BackupJson> {
            val v=f[key] ?: if(key in core || full && key!="receiptLabelTemplates") backupBad("section") else return emptyList()
            return (v as? BackupJson.Arr)?.values ?: backupBad("section")
        }
        val rows=arrays.associateWith(::array)
        objects.forEach {k->if(f[k]==null && full || f[k]!=null && f[k] !is BackupJson.Obj) backupBad("section")}
        nullable.forEach {k->if(f[k]==null && full || f[k]!=null && f[k] !is BackupJson.Obj && f[k]!==BackupJson.Nil) backupBad("section")}
        rows.filterKeys {it in core}.forEach {(_,list)->val ids=list.map {id(obj(it).fields["id"] ?: backupBad())};if(ids.toSet().size!=ids.size) backupBad("duplicate")}
        if(version>=12) {
            val p=obj(f["printerSettings"] ?: backupBad("section")).fields
            if(p["printers"] !is BackupJson.Arr || p["posNotifications"] !is BackupJson.Obj) backupBad("section")
        } else if(f["printerSettings"]!=null) obj(f.getValue("printerSettings"))
        if(full || f["theme"]!=null) {if((f["theme"] as? BackupJson.Str)?.value !in setOf("light","dark")) backupBad("section")}
        if(full && f["demandOverload"] !is BackupJson.Bool) backupBad("section")
        if(full) {val rev=integer(f["operationalRevision"] ?: backupBad());if(rev.signum()<0 || rev>MAX_SAFE) backupBad()}
        val imageFields=when(val v=f["productImages"]) {null->emptyMap();is BackupJson.Obj->v.fields;else->backupBad("photo")}
        if(imageFields.size>5000) backupBad("limit")
        var photoBytes=0L
        val images=imageFields.mapValues {(_,v)->
            val raw=(v as? BackupJson.Str)?.value ?: backupBad("photo")
            val clean=raw.filterNot {it in " \r\n\t"}
            if(clean.length>2_666_672) backupBad("photo")
            val decoded=try {Base64.getDecoder().decode(clean)} catch(_:Exception) {backupBad("photo")}
            if(decoded.isEmpty() || decoded.size>2_000_000) backupBad("photo")
            photoBytes+=decoded.size;if(photoBytes>20*1024*1024) backupBad("photo")
            decoded
        }
        if(f["imageCount"]!=null && integer(f.getValue("imageCount"))!=images.size.toBigInteger()) backupBad("photo")
        val allImages=images.toMutableMap()
        var external=0;var inlineCount=0
        rows.getValue("products").forEachIndexed {index,v->val p=obj(v).fields
            val photo=p["localImageId"]
            if(photo!=null && photo!==BackupJson.Nil) {val key=(photo as? BackupJson.Str)?.value ?: backupBad("photo");if(key.isNotBlank() && key !in images) backupBad("missingPhoto")}
            if(p["image"]!=null && p["image"]!==BackupJson.Nil && p["image"] !is BackupJson.Str) backupBad("photo")
            val inline=(p["image"] as? BackupJson.Str)?.value
            if(!inline.isNullOrBlank()) {
                if(inline.startsWith("data:")) {
                    val marker=inline.indexOf(";base64,")
                    if(marker<0 || inline.substring(5,marker) !in setOf("image/jpeg","image/png","image/webp")) backupBad("photo")
                    // Native exports may contain both representations. The explicit local reference wins.
                    if(photo==null || photo===BackupJson.Nil || (photo as? BackupJson.Str)?.value.isNullOrBlank()) {
                        val raw=inline.substring(marker+8).filterNot {it in " \r\n\t"}
                        if(raw.length>2_666_672) backupBad("photo")
                        val bytes=try {Base64.getDecoder().decode(raw)} catch(_:Exception) {backupBad("photo")}
                        if(bytes.isEmpty() || bytes.size>2_000_000) backupBad("photo")
                        photoBytes+=bytes.size;if(photoBytes>20*1024*1024) backupBad("photo")
                        if("inline:$index" in allImages) backupBad("photo")
                        allImages["inline:$index"]=bytes;inlineCount++
                    }
                } else external++ // Never fetch URLs from a backup.
            }
        }
        val open=rows.getValue("shifts").count {(obj(it).fields["status"] as? BackupJson.Str)?.value=="open"}
        if(full && (f["currentOrderSession"] as? BackupJson.Obj)?.fields?.get("items") !is BackupJson.Arr) backupBad("section")
        val sessions=listOfNotNull(f["currentOrderSession"])+rows.getValue("parked")
        var drafts=0;var paid=BigInteger.ZERO;var unpaid=BigInteger.ZERO
        sessions.forEach {s->val draft=obj(s).fields["paymentDraft"]
            if(draft!=null && draft!==BackupJson.Nil) {
                val d=obj(draft).fields;if(integer(d["version"] ?: backupBad())!=BigInteger.ONE) backupBad("payment")
                val total=integer(d["totalCents"] ?: backupBad());if(total.signum()<0 || total>MAX_SAFE) backupBad("payment")
                val parts=(d["parts"] as? BackupJson.Arr)?.values ?: backupBad("payment")
                if(parts.size !in 2..10) backupBad("payment")
                var sum=BigInteger.ZERO;var paidPart=false
                parts.forEach {part->val p=obj(part).fields;val method=(p["method"] as? BackupJson.Str)?.value
                    if(method !in setOf("cash","card")) backupBad("payment")
                    val amount=minor(p["amount"] ?: backupBad());sum+=amount
                    val isPaid=(p["paid"] as? BackupJson.Bool)?.value ?: backupBad("payment")
                    val given=p["cashGiven"]?.takeUnless {it===BackupJson.Nil}?.let(::minor)
                    val change=p["change"]?.takeUnless {it===BackupJson.Nil}?.let(::minor)
                    if(method=="card" && (given!=null || change!=null) || given!=null && given<amount || given!=null && change!=null && given-amount!=change) backupBad("payment")
                    if(isPaid) {paid+=amount;paidPart=true} else unpaid+=amount
                }
                if(sum!=total || !paidPart) backupBad("payment");drafts++
            }
        }
        val deferred=listOf("hallTables","bookings","operationalOutbox","webEvents","discounts","deliveryRates","receiptLabelTemplates").filter {rows.getValue(it).isNotEmpty()}+
            listOf("network","telegram","printer","printerSettings","webOrderAcceptances","company","theme").filter {f[it]!=null}+
            if(sessions.any {s->obj(s).fields.keys.any {it.startsWith("loyalty") || it in setOf("webOrderId","webOrderStatus")}}) listOf("sessionIntegrations") else emptyList()
        val known=(arrays+objects+nullable+listOf("version","exportedAt","printerSettings","productImages","imageCount","theme","demandOverload","operationalRevision")).toSet()
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        return WebBackupDocument(root,allImages.toMap(),WebBackupPreview(version,bytes.size,sha,rows.mapValues {it.value.size},allImages.size,photoBytes,external,inlineCount,open,drafts,paid,unpaid,f["currentOrderSession"] is BackupJson.Obj,deferred,f.keys.count {it !in known}))
    }
    private val MAX_SAFE=BigInteger("9007199254740991")
    private fun obj(value: BackupJson)=value as? BackupJson.Obj ?: backupBad()
    private fun id(value: BackupJson): String=when(value) {
        is BackupJson.Str->value.value.takeIf {it.isNotBlank() && it.length<=512} ?: backupBad()
        is BackupJson.Num->{val n=integer(value);if(n.abs()>MAX_SAFE || n==BigInteger.ZERO) backupBad();n.toString()}
        else->backupBad()
    }
    private fun integer(value: BackupJson): BigInteger=try {(value as? BackupJson.Num)?.decimal()?.toBigIntegerExact() ?: backupBad()} catch(e:BackupFailure) {throw e} catch(_:Exception) {backupBad()}
    private fun minor(value: BackupJson): BigInteger {
        val amount=try {when(value) {is BackupJson.Num->value.decimal();is BackupJson.Str->{if(value.value.length>128) backupBad("payment");BigDecimal(value.value)};else->backupBad("payment")}.also {if(it.precision()>128 || it.scale().toLong() !in -128L..128L) backupBad("payment")}.movePointRight(2).toBigIntegerExact()} catch(e: BackupFailure) {throw e} catch(_:Exception) {backupBad("payment")}
        if(amount.signum()<0 || amount>MAX_SAFE) backupBad("payment");return amount
    }
}
