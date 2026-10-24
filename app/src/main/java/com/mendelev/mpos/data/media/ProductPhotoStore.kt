package com.mendelev.mpos.data.media

import android.graphics.*
import android.system.Os
import android.system.OsConstants
import com.mendelev.mpos.domain.catalog.CatalogFailure
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/** Immutable app-private files. A prepared photo is not active until Room commits its ID. */
class ProductPhotoStore(private val root: File, private val checkpoint: (String) -> Unit = {}) {
    private val photos=File(root,"product-photos")
    private val staging=File(root,"product-photo-staging")
    private val lock=Any()
    fun file(id: String): File {
        if(!Regex("[0-9a-f-]{36}\\.jpg").matches(id)) throw CatalogFailure("photo","Некорректная ссылка на фото")
        return File(photos,id)
    }
    fun requirePrepared(id: String) = synchronized(lock) {
        if(!file(id).isFile || file(id).length() !in 1..600_000L) throw CatalogFailure("photo","Фото недоступно. Выберите его ещё раз")
    }
    fun prepare(open: () -> InputStream): String = synchronized(lock) {
        if(!staging.isDirectory && !staging.mkdirs()) throw CatalogFailure("photo","Не удалось подготовить каталог фото")
        if(!photos.isDirectory && !photos.mkdirs()) throw CatalogFailure("photo","Не удалось открыть каталог фото")
        val source=File.createTempFile("import-",".tmp",staging)
        var pending: File?=null
        try {
            open().use { input -> FileOutputStream(source).use { output ->
                val buffer=ByteArray(16*1024);var total=0L
                while(true) {
                    val count=input.read(buffer);if(count<0) break
                    total+=count
                    if(total>16*1024*1024) throw CatalogFailure("photo","Фото больше 16 МБ. Выберите изображение меньшего размера")
                    output.write(buffer,0,count)
                }
                output.fd.sync()
            } }
            checkpoint("copied")
            val decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder,info,_ ->
                if(info.mimeType !in setOf("image/jpeg","image/png","image/webp","image/heif","image/heic") ||
                    info.size.width.toLong()*info.size.height>40_000_000L)
                    throw CatalogFailure("photo","Поддерживаются фото JPEG, PNG, WebP и HEIF до 40 мегапикселей")
                val edge=maxOf(info.size.width,info.size.height)
                if(edge>1200) decoder.setTargetSize(maxOf(1,(info.size.width.toLong()*1200/edge).toInt()),maxOf(1,(info.size.height.toLong()*1200/edge).toInt()))
                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val flattened=Bitmap.createBitmap(decoded.width,decoded.height,Bitmap.Config.ARGB_8888)
            try { Canvas(flattened).apply { drawColor(Color.WHITE);drawBitmap(decoded,0f,0f,null) } } finally { decoded.recycle() }
            val bytes=try {
                checkpoint("decoded")
                var result: ByteArray?=null
                for(quality in listOf(82,70,55)) {
                    val output=java.io.ByteArrayOutputStream()
                    if(!flattened.compress(Bitmap.CompressFormat.JPEG,quality,output)) throw CatalogFailure("photo","Не удалось подготовить фото")
                    if(output.size()<=600_000) { result=output.toByteArray();break }
                }
                result ?: throw CatalogFailure("photo","Не удалось уменьшить фото. Выберите другое изображение")
            } finally { flattened.recycle() }
            val id="${UUID.randomUUID()}.jpg"
            pending=File(staging,"$id.pending")
            FileOutputStream(pending).use { it.write(bytes);it.fd.sync() }
            checkpoint("beforePublish")
            if(!pending.renameTo(file(id))) throw CatalogFailure("photo","Не удалось сохранить файл фото")
            val directory=Os.open(photos.absolutePath,OsConstants.O_RDONLY or OsConstants.O_DIRECTORY,0)
            try { Os.fsync(directory) } finally { Os.close(directory) }
            checkpoint("published")
            id
        } finally { source.delete();pending?.delete() }
    }
    fun read(id: String, edge: Int = 256): Bitmap? = synchronized(lock) {
        runCatching {
            val source=file(id)
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(source.absolutePath,bounds)
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>edge*2) sample*=2
            BitmapFactory.decodeFile(source.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample })
        }.getOrNull()
    }
    /** Only the owning namespace; recent preparations are protected even before their draft is committed. */
    fun cleanup(referenced: Set<String>, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val cutoff=now-24*60*60*1000L
        staging.listFiles()?.filter { it.lastModified()<cutoff }?.forEach { it.delete() }
        photos.listFiles()?.filter { it.name.matches(Regex("[0-9a-f-]{36}\\.jpg")) && it.name !in referenced && it.lastModified()<cutoff }
            ?.forEach { it.delete() }
    }
}
