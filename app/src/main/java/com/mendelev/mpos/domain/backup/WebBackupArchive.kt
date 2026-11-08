package com.mendelev.mpos.domain.backup

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

internal data class WebArchiveParts(val json: ByteArray,val images: Map<String,ByteArray>)
/** Web Android ZIP: no disk extraction, bounded decoded sizes, exact names and duplicate protection. */
internal object WebBackupArchive {
    fun read(bytes: ByteArray): WebArchiveParts {
        var json: ByteArray?=null;val images=linkedMapOf<String,ByteArray>();val names=mutableSetOf<String>();var total=0L;var entries=0
        try {ZipInputStream(bytes.inputStream()).use {zip->
            while(true) {
                val entry=zip.nextEntry ?: break
                if(++entries>5001 || entry.isDirectory || !names.add(entry.name)) backupBad("archive")
                val image=Regex("images/([A-Za-z0-9_-]{1,128})\\.(?:jpg|jpeg|png|webp)").matchEntire(entry.name)
                val limit=when {entry.name=="mpos-backup-v1.json"->32*1024*1024;image!=null->2_000_000;else->backupBad("archive")}
                val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {val n=zip.read(buffer);if(n<0) break;if(n==0) continue;if(out.size()>limit-n) backupBad("size");total+=n;if(total>96L*1024*1024) backupBad("size");out.write(buffer,0,n)}
                val value=out.toByteArray();if(value.isEmpty()) backupBad("archive")
                if(image==null) json=value else {val key=image.groupValues[1];if(images.put(key,value)!=null) backupBad("duplicate")}
                zip.closeEntry()
            }
        }} catch(e:BackupFailure) {throw e} catch(_:Exception) {backupBad("archive")}
        return WebArchiveParts(json ?: backupBad("archive"),images)
    }
}
