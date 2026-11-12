package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.sale.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A versioned correction retains the exact original receipt; no second sale/device effect. */
object ReceiptPaymentHistory {
    fun validate(s: ReceiptSnapshot) {
        val original=s.originalReceiptJson?.let {
            val json=JSONObject(it)
            fun strict(value: Any?,depth: Int=0) {
                require(depth<=64)
                when(value) {
                    is JSONObject->value.keys().forEach {key->strict(value.get(key),depth+1)}
                    is JSONArray->for(i in 0 until value.length()) strict(value.get(i),depth+1)
                    is Number->require(value.toString().toLongOrNull()!=null)
                }
            }
            strict(json);require(json.getInt("version")==1)
            ReceiptCodec.decode(it)
        } ?: s
        val before=Math.addExact(Math.subtractExact(original.payment.expectedCashAfter,original.payment.cash),original.payment.deliveryCashOut)
        // Older receipts really issued the courier fee; preserve that immutable cash movement.
        val courier=original.payment.deliveryCashOut
        require(courier==0L || courier==original.quote.delivery?.fee)
        val current=SaleMoney.payments(original.quote,original.checkout.parts,before)
        require(current.expectedCashAfter>=courier)
        require(current.copy(deliveryCashOut=courier,expectedCashAfter=current.expectedCashAfter-courier)==original.payment)
        val rows=JSONArray(s.paymentCorrections)
        if(s.originalReceiptJson==null) {require(rows.length()==0);return}
        require(s.cart==original.cart && s.checkout==original.checkout && s.quote==original.quote && s.consumption==original.consumption && s.products==original.products)
        require(original.payment.parts.size==1 && rows.length()>0)
        var method=original.payment.parts.single().method
        val ids=mutableSetOf<String>();var previous=0L
        for(i in 0 until rows.length()) {
            val r=rows.getJSONObject(i)
            require(ids.add(r.getString("id")) && r.getString("id").isNotBlank())
            require(r.getString("from")==method && r.getString("to") in setOf("cash","card") && r.getString("to")!=method)
            require(r.getLong("at")>=previous && r.getString("actor").isNotBlank() && r.getString("name").isNotBlank())
            require(r.get("administration") is Boolean)
            method=r.getString("to");previous=r.getLong("at")
        }
        require(s.payment==SalePayment(listOf(PaymentAllocation(method,s.quote.total,null,null)),
            if(method=="cash") s.quote.total else 0,if(method=="card") s.quote.total else 0,original.payment.deliveryCashOut,original.payment.expectedCashAfter))
    }
    fun change(r: ReceiptEntity,to: String,id: String,at: Long,actor: String,name: String,administration: Boolean): ReceiptEntity {
        val s=ReceiptCodec.decode(r.payload);validate(s)
        require(r.total==s.quote.total && r.cash==s.payment.cash && r.card==s.payment.card && r.deliveryCashOut==s.payment.deliveryCashOut)
        val from=s.payment.parts.single().method
        require(to in setOf("cash","card") && to!=from && at>=r.createdAt)
        val j=JSONObject(r.payload)
        val rows=JSONArray(s.paymentCorrections).put(JSONObject().put("id",id).put("at",at).put("from",from).put("to",to).put("actor",actor).put("name",name).put("administration",administration))
        j.put("version",2).put("originalReceiptJson",s.originalReceiptJson ?: r.payload).put("paymentCorrections",rows)
        j.put("payments",JSONArray().put(JSONObject().put("method",to).put("amount",r.total).put("given",JSONObject.NULL).put("change",JSONObject.NULL)))
        j.put("cash",if(to=="cash") r.total else 0).put("card",if(to=="card") r.total else 0)
        return r.copy(cash=if(to=="cash") r.total else 0,card=if(to=="card") r.total else 0,payload=j.toString()).also {validate(ReceiptCodec.decode(it.payload))}
    }
}
class ReceiptPaymentCorrectionRepository(private val db: CatalogDatabase,private val afterWrite: (() -> Unit)?=null) {
    suspend fun outcome(command: String)=db.catalog().result(command)?.resultId
    suspend fun commit(command: String,receiptId: String,baseline: String,to: String,administrative: Boolean): String=InstallationGate.write {
        db.purchaseOrderTransaction(administrative) {
            val fp=CatalogRules.fingerprint("receipt.payment.correct.v1",receiptId,baseline,to,administrative)
            db.catalog().result(command)?.let {require(it.fingerprint==fp);return@purchaseOrderTransaction it.resultId}
            val r=db.sales().receipt(receiptId) ?: throw CatalogFailure("receipt","Чек не найден")
            if(r.payload!=baseline) throw CatalogFailure("conflict","Чек изменился. Откройте изменение оплаты заново")
            if(db.refunds().forReceipt(receiptId)!=null) throw CatalogFailure("receipt","Способ оплаты возвращённого чека изменить нельзя")
            val s=ReceiptCodec.decode(r.payload)
            if(s.payment.parts.size!=1) throw CatalogFailure("receipt","Изменение разделённой оплаты пока недоступно")
            val shift=db.shifts().shift(r.shiftId) ?: throw CatalogFailure("receipt","Не найдена смена чека")
            val current=db.shifts().active().singleOrNull()
            if(!administrative && (shift.closedAt!=null || current?.id!=shift.id)) throw CatalogFailure("access","После закрытия смены изменить оплату может только администратор")
            SplitPaymentRepository(db).requireNone()
            val actor=if(administrative) db.catalog().config()!!.ownerId else current!!.actorId
            val name=if(administrative) "Администратор" else current!!.actorName
            val next=ReceiptPaymentHistory.change(r,to,command,System.currentTimeMillis(),actor,name,administrative)
            val delta=Math.subtractExact(next.cash,r.cash)
            if(shift.closedAt!=null) {
                val cash=Math.addExact(shift.expectedCash!!,delta)
                if(cash<0) throw CatalogFailure("cash","Исправление приведёт к отрицательному остатку наличных исходной смены")
                db.shifts().update(shift.copy(expectedCash=cash,difference=Math.subtractExact(shift.countedCash!!,cash)));afterWrite?.invoke()
            } else if(Math.addExact(ShiftRepository(db).snapshot().currentCash,delta)<0) throw CatalogFailure("cash","Недостаточно наличных для исправления оплаты")
            db.sales().update(next);afterWrite?.invoke()
            val register=db.shifts().register()!!
            db.shifts().register(register.copy(revision=Math.addExact(register.revision,1)));afterWrite?.invoke()
            db.catalog().result(ResultEntity(command,fp,receiptId));afterWrite?.invoke()
            db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,actor,"receipt.payment.corrected",receiptId,System.currentTimeMillis()));afterWrite?.invoke()
            receiptId
        }
    }
}
