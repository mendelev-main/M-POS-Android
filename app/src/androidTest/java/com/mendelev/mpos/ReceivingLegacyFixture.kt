package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.warehouse.*
import org.json.JSONObject

/** Synthetic pre-policy2 document and result, including the exact old fingerprint. */
suspend fun seedLegacyPostedReceiving(db: CatalogDatabase): PostedReceiving {
    val seed=seedReceiving(db,"legacy");val s=ReceivingRepository(db).snapshot()
    val d=ReceivingRules.open("legacy-invoice",seed.order,s.products).let {it.copy(lines=it.lines.map {l->l.copy(quantity="3",total="12")})}
    val r=PostedReceiving("legacy-post","legacy-command",seed.owner,1,1,d,false,costPolicy=1)
    val json=JSONObject(PostedReceivingCodec.encode(listOf(r))).put("version",1)
    val oldItem=json.getJSONArray("items").getJSONObject(0);oldItem.remove("costPolicy")
    val oldInvoice=oldItem.getJSONObject("invoice");oldInvoice.put("version",1);oldInvoice.getJSONArray("items").getJSONObject(0).getJSONObject("draft").let {oldDraft->oldDraft.remove("supplier");val lines=oldDraft.getJSONArray("lines");for(i in 0 until lines.length()) {lines.getJSONObject(i).remove("priceMode");lines.getJSONObject(i).remove("unitPrice")}}
    db.workspace().put(WorkspaceDocument(PostedReceivingCodec.KEY,1,json.toString()))
    val p=db.catalog().product(seed.productId)!!;val b=db.catalog().balance(p.id)!!
    db.catalog().updateProduct(p.copy(cost=r.result.lines.single().newCost,revision=p.revision+1))
    db.catalog().updateBalance(b.copy(quantity="13",revision=b.revision+1))
    db.catalog().movement(MovementEntity("legacy-movement",p.id,"3","receiving","legacy-stock-key",1,r.id))
    val orderDoc=db.workspace().document(PurchaseOrderCodec.KEY)!!
    db.workspace().put(orderDoc.copy(revision=orderDoc.revision+1,payload=PurchaseOrderCodec.encode(PurchaseOrderStore(db).all().map {if(it.id==seed.order.id) it.copy(status="received",revision=2,updatedAt=1,receivedAt=1,receivedBy=seed.owner,receivingId=r.id) else it})))
    val draftDoc=db.workspace().document(ReceivingCodec.KEY)
    val rows=draftDoc?.let {ReceivingCodec.decode(it.payload)}.orEmpty()
    db.workspace().put(WorkspaceDocument(ReceivingCodec.KEY,(draftDoc?.revision ?: 0)+1,ReceivingCodec.encode(rows+ReceivingRecord(seed.order.id,2,null))))
    db.catalog().result(ResultEntity(r.commandId,r.command().fingerprint(),r.id))
    db.catalog().audit(AuditEntity("legacy-audit",r.commandId,r.actorId,"receiving.post",r.id,1))
    return r
}
