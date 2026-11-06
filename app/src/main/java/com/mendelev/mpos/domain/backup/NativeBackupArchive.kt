package com.mendelev.mpos.domain.backup

import java.io.*
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Native format is distinct from Web JSON and Room's schema version. Hashes detect corruption, not authorship. */
class NativeBackupFiles(val database: File,val photos: Map<String,File>,val createdAt: Long)
object NativeBackupArchive {
    const val FORMAT_VERSION=1
    const val SCHEMA_VERSION=9
    const val MAX_DATABASE=256L*1024*1024
    const val MAX_TOTAL=512L*1024*1024
    const val MAX_PHOTO=600_000L
    private const val MAX_MANIFEST=1024L*1024
    private val photoId=Regex("[0-9a-f-]{36}\\.jpg")
    private val hashPattern=Regex("[0-9a-f]{64}")
    private class Entry(val name: String,val size: Long,val hash: String)
    fun sha256(file: File): String=file.inputStream().use {input->
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(32768)
        while(true) {val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}
        digest.digest().joinToString("") {"%02x".format(it)}
    }
    fun write(output: File,files: NativeBackupFiles,check: ()->Unit={}) {
        if(files.photos.size>5000 || files.createdAt<0) backupBad("limit")
        val sources=linkedMapOf("database.sqlite" to files.database)
        files.photos.toSortedMap().forEach {(id,file)->if(!photoId.matches(id)) backupBad("photo");sources["photos/$id"]=file}
        val entries=sources.map {(name,file)->
            check();val max=if(name=="database.sqlite") MAX_DATABASE else MAX_PHOTO
            if(!file.isFile || file.length() !in 1..max) backupBad("size")
            Entry(name,file.length(),sha256(file))
        }
        if(entries.sumOf {it.size}>MAX_TOTAL) backupBad("limit")
        val rows=entries.joinToString(",") {"{\"name\":\"${it.name}\",\"size\":${it.size},\"sha256\":\"${it.hash}\"}"}
        val manifest="{\"format\":\"mpos-native\",\"version\":$FORMAT_VERSION,\"schema\":$SCHEMA_VERSION,\"createdAt\":${files.createdAt},\"entries\":[$rows]}".toByteArray(Charsets.UTF_8)
        if(manifest.size>MAX_MANIFEST) backupBad("limit")
        FileOutputStream(output).use {raw->
            // finish without closing raw before fsync.
            val zip=ZipOutputStream(BufferedOutputStream(raw))
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest);zip.closeEntry()
            for(e in entries) {
                check();zip.putNextEntry(ZipEntry(e.name));sources.getValue(e.name).inputStream().use {input->
                    val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(32768);var total=0L
                    while(true) {check();val n=input.read(buffer);if(n<0) break;total+=n;if(total>e.size) backupBad("size");zip.write(buffer,0,n);digest.update(buffer,0,n)}
                    if(total!=e.size || digest.digest().joinToString("") {"%02x".format(it)}!=e.hash) backupBad("changed")
                };zip.closeEntry()
            }
            zip.finish();zip.flush();raw.fd.sync();zip.close()
        }
    }
    /** Only an empty app-owned staging directory. Never accepts a path supplied by the archive. */
    fun read(input: InputStream,directory: File,check: ()->Unit={}): NativeBackupFiles {
        if(!directory.isDirectory || directory.list()?.isNotEmpty()!=false) backupBad("staging")
        var expanded=0L
        ZipInputStream(BufferedInputStream(input)).use {zip->
            val first=zip.nextEntry ?: backupBad()
            if(first.name!="manifest.json" || first.isDirectory) backupBad()
            val bytes=ByteArrayOutputStream();copy(zip,bytes,MAX_MANIFEST,check);zip.closeEntry()
            val text=try {Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()} catch(_:Exception) {backupBad()}
            val root=BackupJsonParser(text).parse() as? BackupJson.Obj ?: backupBad()
            if(root.fields.keys!=setOf("format","version","schema","createdAt","entries")) backupBad()
            fun string(j: BackupJson?)=(j as? BackupJson.Str)?.value ?: backupBad()
            fun long(j: BackupJson?)=(j as? BackupJson.Num)?.raw?.toLongOrNull() ?: backupBad()
            if(string(root.fields["format"])!="mpos-native") backupBad("version")
            if(long(root.fields["version"])!=FORMAT_VERSION.toLong() || long(root.fields["schema"])!=SCHEMA_VERSION.toLong()) backupBad("version")
            val createdAt=long(root.fields["createdAt"]);if(createdAt<0) backupBad()
            val records=(root.fields["entries"] as? BackupJson.Arr)?.values ?: backupBad()
            if(records.size !in 1..5001) backupBad("limit")
            val expected=linkedMapOf<String,Entry>()
            for(record in records) {
                val r=record as? BackupJson.Obj ?: backupBad()
                if(r.fields.keys!=setOf("name","size","sha256")) backupBad()
                val name=string(r.fields["name"]);val size=long(r.fields["size"]);val hash=string(r.fields["sha256"])
                val valid=name=="database.sqlite" || (name.startsWith("photos/") && photoId.matches(name.removePrefix("photos/")))
                if(!valid || !hashPattern.matches(hash) || expected.containsKey(name)) backupBad()
                if(size !in 1..(if(name=="database.sqlite") MAX_DATABASE else MAX_PHOTO)) backupBad("size")
                expanded+=size;if(expanded>MAX_TOTAL) backupBad("limit")
                expected[name]=Entry(name,size,hash)
            }
            if("database.sqlite" !in expected) backupBad()
            val seen=mutableSetOf<String>();val photos=linkedMapOf<String,File>()
            while(true) {
                check();val entry=zip.nextEntry ?: break
                val e=expected[entry.name] ?: backupBad()
                if(entry.isDirectory || !seen.add(e.name) || (entry.size>=0 && entry.size!=e.size)) backupBad()
                // Own flat filenames, no extraction of arbitrary ZIP paths or symlinks.
                val file=File(directory,if(e.name=="database.sqlite") "database.sqlite" else e.name.removePrefix("photos/"))
                FileOutputStream(file).use {out->if(copy(zip,out,e.size,check)!=e.size) backupBad("size");out.fd.sync()}
                zip.closeEntry();if(sha256(file)!=e.hash) backupBad("checksum")
                if(e.name!="database.sqlite") photos[e.name.removePrefix("photos/")]=file
            }
            if(seen!=expected.keys) backupBad()
            return NativeBackupFiles(File(directory,"database.sqlite"),photos,createdAt)
        }
    }
    private fun copy(input: InputStream,out: OutputStream,max: Long,check: ()->Unit): Long {
        var total=0L;val buffer=ByteArray(32768)
        while(true) {check();val n=input.read(buffer);if(n<0) break;total+=n;if(total>max) backupBad("size");out.write(buffer,0,n)}
        return total
    }
}
