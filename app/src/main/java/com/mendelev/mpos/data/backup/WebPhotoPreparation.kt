package com.mendelev.mpos.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.data.recovery.NativeGenerationStore
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal data class WebPreparedPhotos(val id: String,val sourceSha256: String,val files: Map<String,String>,val mediaRoot: File)
/** Asset-only publication, never a dataset selector or a complete Room import. Original keys are opaque. */
internal class WebPhotoPreparation(context: Context,private val checkpoint: (String)->Unit={}) {
    private val root=File(context.noBackupFilesDir,"web-conversion-photos")
    private suspend fun permit(): AdminAuthorization {
        val p=currentCoroutineContext()[AdminAuthorization] ?: throw CatalogFailure("access","Откройте подготовку из административной сессии")
        if(p.session!==Administration.session) throw CatalogFailure("access","Сессия завершена")
        p.requireCurrent();currentCoroutineContext().ensureActive();return p
    }
    suspend fun prepare(document: WebBackupDocument): WebPreparedPhotos {
        val p=permit();val catalog=WebCatalogMapping.map(document)
        val keys=catalog.products.mapNotNull {it.photoKey}.distinct()
        val id=UUID.randomUUID().toString();root.mkdirs()
        val stage=File(root,"$id.stage");if(!stage.mkdir()) bad()
        try {
            val media=File(stage,"media");val photos=ProductPhotoStore(media)
            val records=JSONArray()
            for(key in keys) {
                p.requireCurrent();currentCoroutineContext().ensureActive()
                if(key.length>512) bad()
                val bytes=document.images[key] ?: bad()
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                if(bytes.size !in 1..2_000_000 || bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth.toLong()*bounds.outHeight>16_000_000 || bounds.outMimeType !in setOf("image/jpeg","image/png","image/webp")) bad()
                val photo=photos.prepare {bytes.inputStream()};val file=photos.file(photo)
                records.put(JSONObject().put("key",key).put("photo",photo).put("size",file.length()).put("sha256",NativeBackupArchive.sha256(file)))
                checkpoint("photoPrepared")
            }
            File(media,"product-photos").mkdirs()
            val manifest=JSONObject().put("version",1).put("id",id).put("source",document.preview.sha256).put("photos",records).toString().toByteArray()
            if(manifest.size>1024*1024) bad()
            FileOutputStream(File(stage,"manifest.json")).use {it.write(manifest);it.fd.sync()}
            NativeGenerationStore.sync(media);NativeGenerationStore.sync(stage)
            checkpoint("manifestWritten")
            validate(stage,id,document.preview.sha256)
            return p.session.authorized(p) {
                currentCoroutineContext().ensureActive();checkpoint("beforePublish")
                val published=File(root,id);if(!stage.renameTo(published)) bad()
                NativeGenerationStore.sync(root);NativeGenerationStore.sync(root.parentFile!!);checkpoint("published")
                validate(published,id,document.preview.sha256)
            }
        } finally {stage.deleteRecursively()}
    }
    suspend fun open(id: String,sourceSha256: String): WebPreparedPhotos {
        permit();if(!NativeGenerationStore.UUID_PATTERN.matches(id) || !NativeGenerationStore.HASH.matches(sourceSha256)) bad()
        return validate(File(root,id),id,sourceSha256).also {permit()}
    }
    private fun validate(directory: File,id: String,source: String): WebPreparedPhotos {
        val file=File(directory,"manifest.json");if(!file.isFile || file.length() !in 1..1024*1024L) bad()
        val m=(BackupJsonParser(file.readText()).parse() as? BackupJson.Obj)?.fields ?: bad()
        if(m.keys!=setOf("version","id","source","photos") || (m["version"] as? BackupJson.Num)?.raw!="1" ||
            (m["id"] as? BackupJson.Str)?.value!=id || (m["source"] as? BackupJson.Str)?.value!=source) bad()
        val rows=(m["photos"] as? BackupJson.Arr)?.values ?: bad();if(rows.size>5000) bad()
        val media=File(directory,"media");val result=linkedMapOf<String,String>();val ids=mutableSetOf<String>()
        for(row in rows) {
            val f=(row as? BackupJson.Obj)?.fields ?: bad();if(f.keys!=setOf("key","photo","size","sha256")) bad()
            fun text(key: String)=(f[key] as? BackupJson.Str)?.value ?: bad()
            val key=text("key");val photo=text("photo");val hash=text("sha256")
            if(key.length>512 || key in result || !photo.matches(Regex("[0-9a-f-]{36}\\.jpg")) || !ids.add(photo) || !NativeGenerationStore.HASH.matches(hash)) bad()
            val size=try {(f["size"] as? BackupJson.Num)?.decimal()?.longValueExact() ?: bad()} catch(_:ArithmeticException) {bad()}
            val image=File(File(media,"product-photos"),photo)
            if(size !in 1..600_000L || !image.isFile || image.length()!=size || NativeBackupArchive.sha256(image)!=hash) bad()
            val b=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(image.absolutePath,b)
            if(b.outWidth !in 1..1200 || b.outHeight !in 1..1200 || b.outMimeType!="image/jpeg") bad()
            result[key]=photo
        }
        val photos=File(media,"product-photos")
        if(!photos.isDirectory || photos.listFiles()?.map {it.name}?.toSet()!=ids) bad()
        return WebPreparedPhotos(id,source,result.toMap(),media)
    }
    private fun bad(): Nothing=throw BackupFailure("webPhotos","Не удалось подготовить или проверить фотографии Web-копии")
}
