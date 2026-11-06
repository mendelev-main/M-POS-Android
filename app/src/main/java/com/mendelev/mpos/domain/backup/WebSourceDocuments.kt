package com.mendelev.mpos.domain.backup

import java.security.MessageDigest
import java.util.Base64

/** Exact original bytes, including BOM/whitespace/images. Not a normalized re-export. */
object WebSourceDocuments {
    const val KEY="web-original-source"
    fun owns(id: String)=id==KEY || id.startsWith("$KEY:")
    private fun part(i: Int)="$KEY:"+i.toString().padStart(4,'0')
    fun encode(document: WebBackupDocument): Map<String,String> {
        val bytes=document.original ?: backupBad("webOriginalMissing")
        if(hash(bytes)!=document.preview.sha256)backupBad("webOriginalHash")
        val rows=linkedMapOf<String,String>()
        for(i in 0 until (bytes.size+49151)/49152) {
            val b=bytes.copyOfRange(i*49152,minOf(bytes.size,(i+1)*49152))
            rows[part(i)]=write(mapOf("version" to BackupJson.Num("1"),"base64" to BackupJson.Str(Base64.getEncoder().encodeToString(b))))
        }
        rows[KEY]=write(mapOf("version" to BackupJson.Num("1"),"bytes" to BackupJson.Num(bytes.size.toString()),"sha256" to BackupJson.Str(hash(bytes)),"parts" to BackupJson.Num(rows.size.toString())))
        return rows
    }
    fun decode(documents: Map<String,String>): WebBackupDocument?=bytes(documents)?.let {WebBackupReader.read(it.inputStream())}
    fun bytes(documents: Map<String,String>): ByteArray? {
        val rows=documents.filterKeys(::owns);if(rows.isEmpty())return null
        fun obj(raw: String?)=(BackupJsonParser(raw ?: backupBad("webOriginalParts")).parse() as? BackupJson.Obj)?.fields ?: backupBad("webOriginalParts")
        val m=obj(rows[KEY]);if(m.keys!=setOf("version","bytes","sha256","parts") || (m["version"] as? BackupJson.Num)?.raw!="1")backupBad("webOriginalVersion")
        fun integer(k: String)=(m[k] as? BackupJson.Num)?.raw?.toIntOrNull() ?: backupBad("webOriginalParts")
        val size=integer("bytes");val count=integer("parts")
        if(size !in 1..WebBackupReader.MAX_BYTES || count!=(size+49151)/49152 || rows.keys!=(0 until count).map(::part).toSet()+KEY)backupBad("webOriginalParts")
        val out=java.io.ByteArrayOutputStream(size)
        repeat(count) {i->
            val raw=rows.getValue(part(i));if(raw.length>66000)backupBad("webOriginalLimit")
            val p=obj(raw);if(p.keys!=setOf("version","base64") || (p["version"] as? BackupJson.Num)?.raw!="1")backupBad("webOriginalParts")
            val encoded=(p["base64"] as? BackupJson.Str)?.value ?: backupBad("webOriginalParts")
            val b=try {Base64.getDecoder().decode(encoded)} catch(_:IllegalArgumentException) {backupBad("webOriginalParts")}
            if(b.size!=minOf(49152,size-i*49152) || Base64.getEncoder().encodeToString(b)!=encoded)backupBad("webOriginalParts")
            out.write(b)
        }
        return out.toByteArray().also {if((m["sha256"] as? BackupJson.Str)?.value!=hash(it))backupBad("webOriginalHash")}
    }
    private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun write(fields: Map<String,BackupJson>)=WebLegacyHistoryArchiveCodec.write(BackupJson.Obj(fields))
}
