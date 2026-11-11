package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.shift.*
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigInteger

internal object WebShiftSummary {
    const val PREFIX="web-shift-summary:"
    fun documents(source: WebBackupDocument): List<WorkspaceDocument> {
        val staff=WebStaffMapping.map(source);val receipts=WebReceiptHistory.map(source)
        val raw=WebValues.rows(source.root.fields["orders"]).map {WebSettingsMapping.obj(it)}
        fun total(rows: List<WebHistoricalReceipt>,f: (WebHistoricalReceipt)->Long?)=rows.fold(BigInteger.ZERO) {a,r->a+(f(r) ?: 0L).toBigInteger()}.toString()
        fun payment(r: WebHistoricalReceipt,method: String): Long?=r.payments?.filter {it.method==method}?.fold(0L) {a,p->Math.addExact(a,p.amount)}
            ?: when(r.method) {"cash"->if(method=="cash") r.total else 0;"card"->if(method=="card") r.total else 0;else->null}
        return staff.shifts.map {shift->
            val sold=receipts.filter {it.sourceShiftId==shift.sourceId}
            val returned=receipts.filter {it.sourceReturnShiftId==shift.sourceId && it.returnedAt!=null}
            val discount=receipts.indices.filter {receipts[it].sourceShiftId==shift.sourceId}.fold(BigInteger.ZERO) {sum,i->
                val n=raw[i].opt("productDiscountTotal")?.toString()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                sum+n.setScale(2,java.math.RoundingMode.HALF_UP).movePointRight(2).toBigIntegerExact()
            }
            val j=JSONObject().put("version",1).put("source",source.preview.sha256).put("count",sold.size)
                .put("sales",total(sold) {it.total}).put("refunds",total(returned) {it.returnAmount})
                .put("saleCash",total(sold) {payment(it,"cash")}).put("saleCard",total(sold) {payment(it,"card")})
                .put("refundCash",total(returned) {if(it.returnAmount==it.total) payment(it,"cash") else null})
                .put("refundCard",total(returned) {if(it.returnAmount==it.total) payment(it,"card") else null}).put("discounts",discount.toString())
                .put("deposits",shift.movements.filter {it.type=="deposit"}.fold(BigInteger.ZERO) {a,m->a+m.amount.toBigInteger()}.toString())
                .put("withdrawals",shift.movements.filter {it.type=="withdrawal"}.fold(BigInteger.ZERO) {a,m->a+m.amount.toBigInteger()}.toString())
                .put("movements",JSONArray(shift.movements.mapIndexed {i,m->JSONObject().put("id","web:$i").put("at",m.timestamp ?: 0).put("note",m.note)
                    .put("title",when(m.subtype) {"refund"->"Возврат чека";"delivery"->"Оплата курьеру";else->if(m.type=="deposit") "Внесение наличных" else "Изъятие наличных"})
                    .put("delta",if(m.type=="deposit") m.amount else -m.amount)}))
            WorkspaceDocument(PREFIX+shift.id,1,j.toString())
        }
    }
    fun merge(summary: ShiftSummary,raw: String): ShiftSummary {
        val j=JSONObject(raw)
        fun n(key: String)=j.getString(key).toBigInteger().also {require(it.signum()>=0)}
        val a=j.getJSONArray("movements")
        val rows=List(a.length()) {i->val m=a.getJSONObject(i);ShiftMovementRow("web:"+summary.shift.id+":"+m.getString("id"),m.getLong("at"),m.getString("title"),m.getString("note"),m.getLong("delta"))}
        return summary.copy(count=Math.addExact(summary.count,j.getInt("count")),sales=summary.sales+n("sales"),refunds=summary.refunds+n("refunds"),discounts=summary.discounts+n("discounts"),
            saleCash=summary.saleCash+n("saleCash"),saleCard=summary.saleCard+n("saleCard"),refundCash=summary.refundCash+n("refundCash"),refundCard=summary.refundCard+n("refundCard"),
            deposits=summary.deposits+n("deposits"),withdrawals=summary.withdrawals+n("withdrawals"),movements=(summary.movements+rows).sortedByDescending {it.at})
    }
}
