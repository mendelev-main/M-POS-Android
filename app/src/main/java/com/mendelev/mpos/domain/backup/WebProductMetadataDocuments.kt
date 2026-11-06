package com.mendelev.mpos.domain.backup

/** Immutable per-product retention. Escaped payload keeps source decimals out of integer-only native JSON. */
object WebProductMetadataDocuments {
    const val PREFIX="web-product-metadata:"
    fun owns(id: String)=id.startsWith(PREFIX)
    fun encode(source: WebBackupDocument): Map<String,String> = WebCatalogMapping.map(source).products.associate {p->
        PREFIX+p.id to WebLegacyHistoryArchiveCodec.write(BackupJson.Obj(mapOf(
            "version" to BackupJson.Num("1"),"sourceSha256" to BackupJson.Str(source.preview.sha256),
            "productId" to BackupJson.Str(p.id),"payload" to BackupJson.Str(p.metadata.payload))))
    }
    fun read(raw: String,productId: String): WebProductMetadata {
        val f=(BackupJsonParser(raw).parse() as? BackupJson.Obj)?.fields ?: backupBad("metadata")
        if(f.keys!=setOf("version","sourceSha256","productId","payload") || (f["version"] as? BackupJson.Num)?.raw!="1" ||
            (f["productId"] as? BackupJson.Str)?.value!=productId || (f["sourceSha256"] as? BackupJson.Str)?.value?.matches(Regex("[0-9a-f]{64}"))!=true)backupBad("metadata")
        return WebProductMetadataCodec.decode((f["payload"] as? BackupJson.Str)?.value ?: backupBad("metadata"))
    }
    fun validate(documents: Map<String,String>,source: WebBackupDocument?) {
        val rows=documents.filterKeys(::owns)
        if(rows.isEmpty())return // Earlier native copies retained only the complete source; do not invent sidecars.
        if(source==null || rows!=encode(source))backupBad("metadataSource")
        rows.forEach {(id,raw)->read(raw,id.removePrefix(PREFIX))}
    }
}
