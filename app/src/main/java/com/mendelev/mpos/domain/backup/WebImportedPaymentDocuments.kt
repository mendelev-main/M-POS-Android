package com.mendelev.mpos.domain.backup

/** Source evidence is chunked; no SQLite cursor row carries a multi-megabyte cart/archive. */
object WebImportedPaymentDocuments {
    const val KEY="web-imported-payment-evidence"
    private const val PREFIX="$KEY:"
    fun owns(id: String)=id==KEY || id.startsWith(PREFIX)
    private fun part(i: Int)=PREFIX+i.toString().padStart(4,'0')
    fun encode(value: WebImportedPaymentEvidence): Map<String,String> {
        WebImportedPaymentEvidenceCodec.decode(value.payload)
        val rows=linkedMapOf<String,String>();var start=0;var index=0
        while(start<value.payload.length) {
            var end=minOf(value.payload.length,start+48*1024);if(end<value.payload.length && value.payload[end-1].isHighSurrogate())end--
            rows[part(index++)]=write(BackupJson.Obj(mapOf("version" to BackupJson.Num("1"),"content" to BackupJson.Str(value.payload.substring(start,end)))))
            start=end
        }
        rows[KEY]=write(BackupJson.Obj(mapOf("version" to BackupJson.Num("1"),"bytes" to BackupJson.Num(value.payload.toByteArray().size.toString()),
            "sha256" to BackupJson.Str(WebLegacyHistoryArchiveCodec.digest(value.payload)),"parts" to BackupJson.Arr((0 until index).map {BackupJson.Str(part(it))}))))
        return rows.toMap()
    }
    fun decode(documents: Map<String,String>): WebImportedPaymentEvidence? {
        val rows=documents.filterKeys(::owns);if(rows.isEmpty())return null
        fun obj(raw: String,max: Int): Map<String,BackupJson> {
            if(raw.toByteArray().size>max)backupBad("importedPaymentLimit")
            return (BackupJsonParser(raw).parse() as? BackupJson.Obj)?.fields ?: backupBad("importedPaymentParts")
        }
        val root=obj(rows[KEY] ?: backupBad("importedPaymentParts"),64*1024)
        if(root.keys!=setOf("version","bytes","sha256","parts") || (root["version"] as? BackupJson.Num)?.raw!="1")backupBad("importedPaymentParts")
        val names=(root["parts"] as? BackupJson.Arr)?.values?.map {(it as? BackupJson.Str)?.value ?: backupBad("importedPaymentParts")} ?: backupBad("importedPaymentParts")
        if(names.size !in 1..512 || names!=names.indices.map(::part) || rows.keys!=names.toSet()+KEY)backupBad("importedPaymentParts")
        val body=StringBuilder();var bytes=0L
        for(id in names) {
            val p=obj(rows.getValue(id),256*1024)
            if(p.keys!=setOf("version","content") || (p["version"] as? BackupJson.Num)?.raw!="1")backupBad("importedPaymentParts")
            val content=(p["content"] as? BackupJson.Str)?.value ?: backupBad("importedPaymentParts")
            bytes+=content.toByteArray().size;if(bytes>WebImportedPaymentEvidenceCodec.MAX_BYTES)backupBad("importedPaymentLimit")
            body.append(content)
        }
        val payload=body.toString()
        if((root["bytes"] as? BackupJson.Num)?.raw!=bytes.toString() || (root["sha256"] as? BackupJson.Str)?.value!=WebLegacyHistoryArchiveCodec.digest(payload))backupBad("importedPaymentHash")
        return WebImportedPaymentEvidenceCodec.decode(payload)
    }
    private fun write(j: BackupJson)=WebLegacyHistoryArchiveCodec.write(j)
}
