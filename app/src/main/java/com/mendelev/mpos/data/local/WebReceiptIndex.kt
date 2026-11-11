package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import org.json.JSONObject

/** Historical documents only: never ReceiptEntity/RefundEntity inserts or stock/cash commands. */
data class WebReceiptRecord(val key: String,val payload: String) {
    override fun toString()="WebReceiptRecord(REDACTED)"
    val data: JSONObject by lazy {JSONObject(payload)}
    val id get()=data.getString("id")
    val raw: JSONObject by lazy {JSONObject(data.getString("rawJson"))}
    val time get()=data.optLong("createdAt")
    val returned get()=raw.opt("returnedAt")?.toString()?.toBigDecimalOrNull()?.signum()==1
    val label get()=raw.optString("receiptDisplayNumber").takeIf {it.isNotBlank()}?.let {"Чек $it"} ?: "Чек Web · "+data.getString("sourceId")
    fun type()=when(raw.optString("orderType")) {"На месте"->"here";"С собой","На вынос"->"takeaway";"Доставка"->"delivery";else->null}
    fun paymentLabel()=when(raw.optString("method")) {"cash"->"Наличные";"card"->"Карта";"split","mixed"->"Наличные + карта";else->"Оплата Web"}
    /** UI summary only. This object is never passed to checkout/refund persistence. */
    fun summary()=ReceiptEntity(id,id,id,"",0,time,data.optLong("total"),0,0,0,"")
}
internal object WebReceiptIndex {
    const val PREFIX="web-receipt:"
    fun documents(source: WebBackupDocument): List<WorkspaceDocument> {
        val raw=WebValues.rows(source.root.fields["orders"])
        return WebReceiptHistory.map(source).mapIndexed {i,r->
            val id="web:"+r.id
            val key=PREFIX+(Long.MAX_VALUE-(r.timestamp ?: 0L)).toString().padStart(19,'0')+":"+id
            val p=JSONObject().put("version",1).put("id",id).put("sourceId",r.sourceId).put("source",source.preview.sha256)
                .put("createdAt",r.timestamp ?: JSONObject.NULL).put("total",r.total ?: JSONObject.NULL)
                .put("rawJson",WebSettingsMapping.value(raw[i]).toString())
            WorkspaceDocument(key,1,p.toString())
        }
    }
    fun validate(row: WorkspaceDocument) {
        require(row.id.startsWith(PREFIX) && row.revision==1L)
        val p=JSONObject(row.payload);require(p.getInt("version")==1 && p.getString("id").startsWith("web:"))
        val time=if(p.isNull("createdAt")) 0L else p.getLong("createdAt")
        require(time>=0 && row.id==PREFIX+(Long.MAX_VALUE-time).toString().padStart(19,'0')+":"+p.getString("id"))
        require(JSONObject(p.getString("rawJson")).has("id") && Regex("[0-9a-f]{64}").matches(p.getString("source")))
    }
}
internal data class ReceiptHistoryPageResult(val native: List<ReceiptEntity>,val web: List<WebReceiptRecord>,val nativeOffset: Int,val webOffset: Int,val more: Boolean)
internal class ReceiptHistoryPaging(private val db: CatalogDatabase) {
    suspend fun page(nativeOffset: Int=0,webOffset: Int=0): ReceiptHistoryPageResult {
        val n=db.sales().historyPage(PAGE_SIZE+1,nativeOffset)
        val w=db.workspace().prefixPage(WebReceiptIndex.PREFIX,PAGE_SIZE+1,webOffset).map {WebReceiptRecord(it.id,it.payload)}
        return combine(n,w,nativeOffset,webOffset)
    }
    companion object {
        const val PAGE_SIZE=20
        fun combine(n: List<ReceiptEntity>,w: List<WebReceiptRecord>,nativeOffset: Int,webOffset: Int): ReceiptHistoryPageResult {
        val candidates=(n.map {Triple(it.createdAt,it.id,false)}+w.map {Triple(it.time,it.id,true)})
            .sortedWith(compareByDescending<Triple<Long,String,Boolean>> {it.first}.thenBy {it.second}).take(PAGE_SIZE)
        val ids=candidates.map {it.second}.toSet();val nr=n.filter {it.id in ids};val wr=w.filter {it.id in ids}
        return ReceiptHistoryPageResult(nr,wr,nativeOffset+nr.size,webOffset+wr.size,n.size+w.size>PAGE_SIZE)
        }

    }
}
