package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import org.json.JSONObject

/** Independent source-linked validation also runs on subsequent native backup restores. */
internal object WebCandidateValidation {
    suspend fun initial(db: CatalogDatabase,source: WebBackupDocument) {
        validate(db,source)
        require(db.workspace().productMetadataDocuments().associate {it.id to it.payload}==WebProductMetadataDocuments.encode(source))
        val catalog=WebCatalogMapping.map(source);val products=db.catalog().allProducts().associateBy {it.id}
        require(products.keys==catalog.products.map {it.id}.toSet())
        for(p in catalog.products) {
            val actual=products.getValue(p.id)
            require(actual.name==p.name && actual.type==p.type && actual.price==p.price && actual.unit==p.unit && actual.recipeYield==p.recipeYield && actual.revision==1L)
            if(p.type=="simple") {require(actual.cost==p.cost);require(db.catalog().balance(p.id)?.quantity==p.stock)}
            if(p.photoKey!=null)require(actual.photoId!=null)
        }
        require(db.workspace().document(SettingsCodec.KEY)?.payload==SettingsCodec.encode(WebSettingsMapping.catalog(source)))
        require(db.workspace().documentsByPrefix(WebReceiptIndex.PREFIX).size==WebValues.rows(source.root.fields["orders"]).size)
        val staff=WebStaffMapping.map(source)
        require(db.shifts().history().map {it.id}.toSet()==staff.shifts.map {it.id}.toSet())
        require(EmployeeCodec.decode(db.workspace().document(EmployeeCodec.KEY)!!.payload)==staff.employees)
        require(db.sales().history().isEmpty() && db.refunds().history().isEmpty() && db.catalog().movements().isEmpty())
    }

    suspend fun validate(db: CatalogDatabase, knownSource: WebBackupDocument?=null) {
        val rows=db.workspace().documents();val docs=rows.associate {it.id to it.payload}
        val original=knownSource?.also {WebSourceDocuments.requireSource(docs,it)} ?: WebSourceDocuments.decode(docs)?.withoutPhotoPayloads()
        val evidence=WebImportedPaymentDocuments.decode(docs)
        WebProductMetadataDocuments.validate(docs,original)
        if(original==null) {
            require(WebCandidateDatabase.BASELINE !in docs && WebCandidateDatabase.BASIS !in docs && WebOrderDefaults.KEY !in docs && (evidence==null || evidence.records.isEmpty()))
            return
        }
        val shiftSummaries=rows.filter {it.id.startsWith(WebShiftSummary.PREFIX)}.associate {it.id to it.payload}
        if(shiftSummaries.isNotEmpty()) require(shiftSummaries==WebShiftSummary.documents(original).associate {it.id to it.payload})
        val receiptIndex=rows.filter {it.id.startsWith(WebReceiptIndex.PREFIX)}.associate {it.id to it.payload}
        if(receiptIndex.isNotEmpty()) require(receiptIndex==WebReceiptIndex.documents(original).associate {it.id to it.payload})
        docs[WebSettingsMapping.DEVICE]?.let {require(WebSettingsMapping.decode(it).getString("source")==original.preview.sha256)}
        require(rows.filter {WebSourceDocuments.owns(it.id) || it.id==WebCandidateDatabase.BASELINE}.all {it.revision==1L})
        require(WebLegacyHistoryDocuments.decode(docs)?.payload==WebLegacyHistoryArchiveCodec.encode(original).payload)
        require(evidence!=null);WebImportedPaymentEvidenceCodec.requireSource(evidence,original)
        require(docs[WebOrderDefaults.KEY]==WebOrderDefaults.encode(original))
        val basis=JSONObject(docs.getValue(WebCandidateDatabase.BASIS))
        require(basis.keys().asSequence().toSet()==setOf("version","products") && basis.getInt("version")==1)
        val a=basis.getJSONArray("products")
        require((0 until a.length()).map {a.getString(it)}==WebCatalogMapping.map(original).products.filter {it.type=="composite"}.map {it.id}.sorted())
        val baseline=JSONObject(docs.getValue(WebCandidateDatabase.BASELINE))
        require(baseline.keys().asSequence().toSet()==setOf("version","source","zonePolicy","cashDeltas"))
        require(baseline.getInt("version")==1 && baseline.getString("source")==original.preview.sha256 && baseline.getString("zonePolicy")=="Europe/Minsk")
        val staff=WebStaffMapping.map(original);val cash=WebCashReconciliation.compare(original)
        val deltas=baseline.getJSONObject("cashDeltas")
        require(deltas.keys().asSequence().toSet()==staff.shifts.map {it.id}.toSet())
        for(s in staff.shifts) {
            val actual=db.shifts().shift(s.id) ?: error("missing source shift")
            require(actual.actorId==(s.actorId ?: if(s.status=="closed") WebIdentity.mapped("employee","archived-shift:"+s.sourceId) else null) && actual.actorName==(s.actorName ?: if(s.status=="closed") "Неизвестный кассир Web" else null) && actual.openedAt==s.openedAt && actual.openingCash==s.openingCash)
            val expected=Math.subtractExact(cash.shifts.single {it.sourceShiftId==s.sourceId}.cashFromRecordedFacts!!,s.openingCash!!)
            require(deltas.get(s.id).toString()==expected.toString())
        }
        val progress=SplitPaymentRepository(db).load(original)
        val receipts=db.sales().history()
        val bindings=WebRecipeBinding.bind(original).carts.associateBy {it.origin}
        // Imported cart references started at actual candidate revision 1. Later catalogue edits cannot rewrite them.
        val products=db.catalog().allProducts().map {it.copy(revision=1)}
        for(r in evidence.records) {
            val bound=bindings.getValue(r.origin)
            val adopted=WebCartAdoption.map(bound,products,original)
            val finished=receipts.filter {it.sessionId==r.origin}
            require(finished.size<=1)
            if(finished.isEmpty()) {
                require(progress?.importedOrigin==r.origin)
                require(WorkspaceCodec.cart(docs.getValue("current"))==adopted.cart)
                val d=progress!!.checkout.draft
                require(d.discounts==adopted.discounts && d.delivery==adopted.delivery)
                WebPaidProvenance.validate(db,progress,original)
            } else {
                require(progress?.importedOrigin!=r.origin)
                val receipt=ReceiptCodec.decode(finished.single().payload)
                require(receipt.cart==adopted.cart && receipt.checkout.discounts==adopted.discounts && receipt.checkout.delivery==adopted.delivery)
                // Paid source parts are immutable under SplitEditor. Native remainder may change count/method.
                val remaining=receipt.checkout.parts.toMutableList()
                for(part in r.parts.filter {it.paid}) {
                    val i=remaining.indexOfFirst {it.method==part.method && it.amount==part.amount && it.given==part.given}
                    require(i>=0);remaining.removeAt(i)
                }
            }
        }
    }
}
