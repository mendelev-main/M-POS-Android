package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*

/** Imported confirmations refer to immutable source positions, never fabricated native operation results. */
internal object WebPaidProvenance {
    suspend fun validate(db: CatalogDatabase,p: SplitProgress,knownSource: WebBackupDocument?=null) {
        val rows=db.workspace().documents()
        val documents=rows.associate {it.id to it.payload}
        val evidence=WebImportedPaymentDocuments.decode(documents) ?: invalid()
        val original=knownSource?.also {WebSourceDocuments.requireSource(documents,it)} ?: WebSourceDocuments.decode(documents) ?: invalid()
        WebImportedPaymentEvidenceCodec.requireSource(evidence,original)
        val source=evidence.records.singleOrNull {it.origin==p.importedOrigin} ?: invalid()
        val d=p.checkout.draft
        if(p.format!=3 || source.cartSnapshot==null || d.sessionId!=source.origin || d.shiftId!=source.candidateShiftId || d.actorId!=source.candidateActorId ||
            source.total!=p.editable().total || p.importedParts.values.toSet()!=source.parts.filter {it.paid}.map {it.position}.toSet() ||
            p.importedParts.size!=p.importedParts.values.toSet().size) invalid()
        for((id,position) in p.importedParts) {
            val index=p.partIds.indexOf(id)
            if(index !in p.paid)invalid()
            val original=source.parts.getOrNull(position) ?: invalid();val actual=d.parts[index]
            if(!original.paid || original.method!=actual.method || original.amount!=actual.amount || original.given!=actual.given)invalid()
        }
    }
    private fun invalid(): Nothing=throw BackupFailure("paymentProvenance","Оплаченные части не совпадают с исходной Web-копией")
}
