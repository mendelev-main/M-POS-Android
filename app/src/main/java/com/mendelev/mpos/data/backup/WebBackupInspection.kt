package com.mendelev.mpos.data.backup

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface BackupInspectionAccess { suspend fun inspect(uri: String): WebBackupPreview }
/** Only sanitized counts escape this adapter. No Room, file publication, URL fetch, or restore operations. */
class WebBackupInspection(private val resolver: ContentResolver): BackupInspectionAccess {
    override suspend fun inspect(uri: String): WebBackupPreview {
        val permit=currentCoroutineContext()[AdminAuthorization] ?: throw CatalogFailure("access","Откройте проверку из административной сессии")
        if(permit.session!==Administration.session) throw CatalogFailure("access","Сессия завершена")
        permit.requireCurrent();currentCoroutineContext().ensureActive()
        val document=resolver.openInputStream(Uri.parse(uri))?.use {WebBackupReader.read(it)} ?: throw BackupFailure("read","Не удалось прочитать файл")
        for(bytes in document.images.values) {
            currentCoroutineContext().ensureActive();permit.requireCurrent()
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth.toLong()*bounds.outHeight>16_000_000 ||
                bounds.outMimeType !in setOf("image/jpeg","image/png","image/webp")) throw BackupFailure("photo","Фотографии повреждены или не поддерживаются")
        }
        currentCoroutineContext().ensureActive();permit.requireCurrent()
        return document.preview
    }
}
