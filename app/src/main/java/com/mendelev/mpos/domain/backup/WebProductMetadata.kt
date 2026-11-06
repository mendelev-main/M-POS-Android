package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.CategoryAppearance
import com.mendelev.mpos.domain.catalog.CatalogFailure

/** Import sidecar, not active catalog fields or authority to enqueue transport. No raw payload in logs. */
data class WebProductMetadata internal constructor(val payload: String,val presentFields: Set<String>,
    val retainedOnlyFields: Set<String>,val sortOrder: String?,val tileSymbol: String?) {
    override fun toString()="WebProductMetadata(fields=${presentFields.size}, retained=${retainedOnlyFields.size})"
}
object WebProductMetadataCodec {
    const val MAX_BYTES=256*1024
    val fields=setOf("sku","internalNote","tileSymbol","sortOrder","recipeYieldManual","description","availableOnline","availableInOnlineMenu","imageUploadPending","unit")
    private val booleans=setOf("recipeYieldManual","availableOnline","availableInOnlineMenu","imageUploadPending")
    fun project(product: Map<String,BackupJson>): WebProductMetadata=build(product.filterKeys {it in fields})
    fun decode(payload: String): WebProductMetadata {
        if(payload.toByteArray(Charsets.UTF_8).size>MAX_BYTES) backupBad("metadataLimit")
        val root=(BackupJsonParser(payload).parse() as? BackupJson.Obj)?.fields ?: backupBad("metadata")
        if(root.keys!=setOf("version","values") || (root["version"] as? BackupJson.Num)?.raw!="1") backupBad("metadataVersion")
        val values=(root["values"] as? BackupJson.Obj)?.fields ?: backupBad("metadata")
        if(values.keys.any {it !in fields}) backupBad("metadata")
        return build(values)
    }
    private fun build(values: Map<String,BackupJson>): WebProductMetadata {
        var order: String?=null;var symbol: String?=null
        values.forEach {(key,value)->
            if(value===BackupJson.Nil) return@forEach
            when {
                key in booleans->if(value !is BackupJson.Bool) backupBad("metadata")
                key=="sortOrder"->order=WebValues.number(value,signed=true)
                else->{val text=(value as? BackupJson.Str)?.value ?: backupBad("metadata")
                    if(text.toByteArray(Charsets.UTF_8).size>64*1024) backupBad("metadataLimit")
                    if(key=="tileSymbol") symbol=try {CategoryAppearance.symbol(text)} catch(_:CatalogFailure) {backupBad("metadataSymbol")}
                }
            }
        }
        val encoded=values.toSortedMap().entries.joinToString(",") {(k,v)->quote(k)+":"+when(v) {
            BackupJson.Nil->"null";is BackupJson.Str->quote(v.value);is BackupJson.Num->v.raw
            is BackupJson.Bool->v.value.toString();else->backupBad("metadata")
        }}
        val payload="{\"version\":1,\"values\":{$encoded}}"
        if(payload.toByteArray(Charsets.UTF_8).size>MAX_BYTES) backupBad("metadataLimit")
        // sortOrder is consumed by navigation. Other recognized fields still need a native retention contract.
        return WebProductMetadata(payload,values.keys.toSet(),values.keys-setOf("sortOrder"),order,symbol)
    }
    private fun quote(text: String): String=buildString {
        append('"')
        for(c in text) when(c) {
            '"'->append("\\\"");'\\'->append("\\\\");'\b'->append("\\b");'\u000c'->append("\\f")
            '\n'->append("\\n");'\r'->append("\\r");'\t'->append("\\t")
            else->if(c<' ') {append("\\u");append(c.code.toString(16).padStart(4,'0'))} else append(c)
        }
        append('"')
    }
}
