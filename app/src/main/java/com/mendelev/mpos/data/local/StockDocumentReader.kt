package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.catalog.CatalogRules
import java.math.BigDecimal
import org.json.JSONObject

/** Follow the stored movement link and verify its exact product/delta/command association. Never writes. */
class StockDocumentReader(private val db: CatalogDatabase) {
    private fun money(value: Long)=BigDecimal.valueOf(value,2).toPlainString()+" BYN"
    suspend fun read(id: String): StockDocument=db.withTransaction {
        val m=checkNotNull(db.catalog().stockMovement(id)) {"Движение недоступно"}
        val link=checkNotNull(m.reason) {"Для этого движения нет отдельного документа"}
        fun matches(namespace: String,command: String,at: Long,delta: String) {
            require(m.commandId==CatalogRules.fingerprint(namespace,command,m.productId) && m.createdAt==at && m.delta.toBigDecimal().compareTo(delta.toBigDecimal())==0) {"Связь движения с документом повреждена"}
        }
        when(m.source) {
            "sale"->{
                val r=checkNotNull(db.sales().receipt(link));val saved=ReceiptCodec.decode(r.payload)
                val qty=checkNotNull(saved.consumption[m.productId]);matches("sale.stock.v1",r.commandId,r.createdAt,(-qty.toBigDecimal()).toPlainString())
                StockDocument("Чек №${r.number}",r.createdAt,saved.cart.lines.map {"${it.name} × ${it.quantity}"}+listOf("Итого: ${money(r.total)}","Наличные: ${money(r.cash)} · карта: ${money(r.card)}"))
            }
            "refund"->{
                val r=checkNotNull(db.refunds().refund(link));val j=JSONObject(r.payload);require(j.getInt("version")==1 && j.getString("currency")=="BYN")
                val a=j.getJSONArray("stock");val change=(0 until a.length()).map {a.getJSONObject(it)}.single {it.getString("productId")==m.productId}
                matches("refund.stock.v1",r.commandId,r.createdAt,change.getString("delta"))
                val receipt=ReceiptCodec.decode(j.getJSONObject("receipt").toString())
                val original=checkNotNull(db.sales().receipt(r.receiptId))
                require(CatalogRules.fingerprint("refund.receipt.v1",original.payload)==r.receiptHash && ReceiptCodec.decode(original.payload)==receipt)
                StockDocument("Возврат чека",r.createdAt,receipt.cart.lines.map {"${it.name} × ${it.quantity}"}+listOf("Возвращено: ${money(r.total)}","Наличные: ${money(r.cash)} · карта: ${money(r.card)}"))
            }
            "receiving"->{
                val r=PostedReceivingStore(db).all().single {it.id==link};val line=r.result.lines.single {it.productId==m.productId}
                matches("receiving.stock.v1",r.commandId,r.at,line.actual)
                StockDocument("Приёмка · ${r.draft.invoiceNumber.ifBlank {"Без номера"}}",r.at,r.result.lines.map {"${it.name}: получено ${it.actual} · ${money(it.totalMinor)}"}+"Итого: ${money(r.result.totalMinor)}")
            }
            "inventory"->{
                val d=InventoryCodec.decode(checkNotNull(db.workspace().document(InventoryCodec.KEY)).payload)
                val record=d.history.singleOrNull {it.id==link};val draft=record?.draft ?: checkNotNull(d.draft?.takeIf {it.id==link})
                val fixed=draft.counts.mapNotNull {it.fixed}.single {it.movementId==m.id}
                require(fixed.product.id==m.productId);matches("inventory.stock.v1",fixed.commandId,fixed.at,fixed.delta)
                StockDocument("Инвентаризация · ${if(record==null) "В работе" else if(record.cancelled) "Отменена" else "Завершена"}",record?.at ?: draft.startedAt,
                    draft.products.map {p->val f=draft.counts.find {it.product.id==p.id}?.fixed; if(f==null) "${p.name}: не зафиксировано" else "${f.product.name}: учёт ${f.product.stock} · факт ${f.actual} · разница ${f.delta}"})
            }
            else->error("Для этого движения нет отдельного документа")
        }
    }
}
