package com.mendelev.mpos.domain.backup

/** Chunked JSON documents keep each SQLite cursor row below 256KiB; manifest binds all parts. */
object WebLegacyHistoryDocuments {
    const val KEY="web-legacy-history"
    const val PREFIX="$KEY:"
    private const val CHARS=48*1024
    private const val MAX_PART_BYTES=256*1024
    private fun part(i: Int)=PREFIX+i.toString().padStart(4,'0')
    fun owns(id: String)=id==KEY || id.startsWith(PREFIX)
    fun encode(archive: WebLegacyHistoryArchive): Map<String,String> {
        val valid=WebLegacyHistoryArchiveCodec.decode(archive.payload)
        val rows=linkedMapOf<String,String>();val names=mutableListOf<BackupJson>()
        var start=0;var index=0
        while(start<valid.payload.length) {
            var end=minOf(valid.payload.length,start+CHARS)
            if(end<valid.payload.length && valid.payload[end-1].isHighSurrogate())end--
            val text=valid.payload.substring(start,end);val id=part(index++)
            val payload=WebLegacyHistoryArchiveCodec.write(BackupJson.Obj(mapOf("version" to BackupJson.Num("1"),"content" to BackupJson.Str(text))))
            if(payload.toByteArray(Charsets.UTF_8).size>MAX_PART_BYTES || index>512) backupBad("legacyHistoryLimit")
            rows[id]=payload;names.add(BackupJson.Str(id));start=end
        }
        rows[KEY]=WebLegacyHistoryArchiveCodec.write(BackupJson.Obj(mapOf("version" to BackupJson.Num("1"),"sourceSha256" to BackupJson.Str(valid.sourceSha256),
            "sourceVersion" to BackupJson.Num(valid.sourceVersion.toString()),"sha256" to BackupJson.Str(WebLegacyHistoryArchiveCodec.digest(valid.payload)),
            "bytes" to BackupJson.Num(valid.payload.toByteArray(Charsets.UTF_8).size.toString()),"parts" to BackupJson.Arr(names))))
        return rows.toMap()
    }
    fun decode(documents: Map<String,String>): WebLegacyHistoryArchive? {
        val rows=documents.filterKeys(::owns)
        if(rows.isEmpty())return null
        val manifest=rows[KEY] ?: backupBad("legacyHistoryParts")
        if(manifest.toByteArray(Charsets.UTF_8).size>64*1024)backupBad("legacyHistoryLimit")
        val root=(BackupJsonParser(manifest).parse() as? BackupJson.Obj)?.fields ?: backupBad("legacyHistoryParts")
        if(root.keys!=setOf("version","sourceSha256","sourceVersion","sha256","bytes","parts") || (root["version"] as? BackupJson.Num)?.raw!="1")backupBad("legacyHistoryVersion")
        val names=(root["parts"] as? BackupJson.Arr)?.values?.map {(it as? BackupJson.Str)?.value ?: backupBad("legacyHistoryParts")} ?: backupBad("legacyHistoryParts")
        if(names.size !in 1..512 || names!=names.indices.map(::part) || rows.keys!=names.toSet()+KEY)backupBad("legacyHistoryParts")
        val body=StringBuilder();var bytes=0L
        for(id in names) {
            val raw=rows.getValue(id)
            if(raw.toByteArray(Charsets.UTF_8).size>MAX_PART_BYTES)backupBad("legacyHistoryLimit")
            val p=(BackupJsonParser(raw).parse() as? BackupJson.Obj)?.fields ?: backupBad("legacyHistoryParts")
            if(p.keys!=setOf("version","content") || (p["version"] as? BackupJson.Num)?.raw!="1")backupBad("legacyHistoryParts")
            val content=(p["content"] as? BackupJson.Str)?.value ?: backupBad("legacyHistoryParts")
            bytes+=content.toByteArray(Charsets.UTF_8).size;if(bytes>WebLegacyHistoryArchiveCodec.MAX_BYTES)backupBad("legacyHistoryLimit")
            body.append(content)
        }
        val payload=body.toString()
        if((root["bytes"] as? BackupJson.Num)?.raw!=bytes.toString() || (root["sha256"] as? BackupJson.Str)?.value!=WebLegacyHistoryArchiveCodec.digest(payload))backupBad("legacyHistoryHash")
        val result=WebLegacyHistoryArchiveCodec.decode(payload)
        if((root["sourceSha256"] as? BackupJson.Str)?.value!=result.sourceSha256 || (root["sourceVersion"] as? BackupJson.Num)?.raw!=result.sourceVersion.toString())backupBad("legacyHistorySource")
        return result
    }
}
