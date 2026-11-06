package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.access.*
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.backup.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Candidate-only immutable source-paid evidence. Never native confirmations or a live append/charge API. */
internal class WebImportedPaymentStore(private val context: android.content.Context,private val db: CatalogDatabase,private val afterWrite: ()->Unit={}) {
    suspend fun install(document: WebBackupDocument) {
        val permit=currentCoroutineContext()[AdminAuthorization] ?: throw com.mendelev.mpos.domain.catalog.CatalogFailure("access","Откройте подготовку из административной сессии")
        if(permit.session!==Administration.session) throw com.mendelev.mpos.domain.catalog.CatalogFailure("access","Сессия завершена")
        permit.requireCurrent();currentCoroutineContext().ensureActive()
        val name=db.openHelper.databaseName
        if(name!=null && context.getDatabasePath(name).canonicalFile==com.mendelev.mpos.data.recovery.AppDataset.paths(context).database.canonicalFile)
            throw BackupFailure("importedPaymentTarget","Архив устанавливается только в отдельную подготовленную базу, без объединения рабочих историй")
        val archive=WebImportedPaymentEvidenceCodec.encode(document);val rows=WebImportedPaymentDocuments.encode(archive)
        InstallationGate.write {db.adminTransaction {
            permit.requireCurrent();currentCoroutineContext().ensureActive()
            if(db.catalog().config()==null)throw BackupFailure("importedPaymentTarget","Сначала подготовьте целевую базу")
            val existing=db.workspace().documents().filter {WebImportedPaymentDocuments.owns(it.id)}
            if(existing.isNotEmpty()) {
                val old=decode(existing) ?: throw BackupFailure("importedPayment","Архив повреждён")
                if(old.payload!=archive.payload)throw BackupFailure("importedPaymentConflict","Архив уже записан. Объединение или замена истории здесь не разрешены")
                return@adminTransaction
            }
            for((id,payload) in rows) {currentCoroutineContext().ensureActive();db.workspace().put(WorkspaceDocument(id,1,payload));afterWrite()}
        }}
    }
    suspend fun read(): WebImportedPaymentEvidence?=db.withTransaction {decode(db.workspace().documents().filter {WebImportedPaymentDocuments.owns(it.id)})}
    private fun decode(rows: List<WorkspaceDocument>): WebImportedPaymentEvidence? {
        if(rows.any {it.revision!=1L})throw BackupFailure("importedPaymentRevision","Версия архива повреждена")
        return WebImportedPaymentDocuments.decode(rows.associate {it.id to it.payload})
    }
}
