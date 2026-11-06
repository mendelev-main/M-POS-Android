package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.refund.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

/** Retain this preview and command unchanged until commit/outcome resolves a lost response. */
data class RefundPreview(val receiptId: String,val shiftId: String,val revision: Long,val actorId: String,
    val token: String,val amounts: RefundAmounts,val stock: List<RestockChange>)

class RefundRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemDefaultZone(),
    private val afterWrite: (() -> Unit)?=null) {
    private fun conflict(): Nothing=throw CatalogFailure("refund","Данные возврата изменились. Проверьте возврат заново")
    private fun wrote() {afterWrite?.invoke()}
    fun observe()=db.refunds().observe()
    suspend fun history()=db.refunds().history()
    suspend fun outcome(command: String)=db.refunds().command(command)
    private fun stockPayload(stock: List<RestockChange>)=JSONArray().also {array->stock.forEach {
        array.put(JSONObject().put("productId",it.productId).put("delta",it.delta).put("before",it.before)
            .put("after",it.after).put("expectedRevision",it.expectedRevision).put("nextRevision",it.nextRevision))
    }}.toString()
    suspend fun preview(receiptId: String,actorId: String): RefundPreview=db.withTransaction {
        val receipt=db.sales().receipt(receiptId) ?: throw CatalogFailure("refund","Чек не найден")
        if(db.refunds().forReceipt(receiptId)!=null) throw CatalogFailure("refund","Чек уже возвращён")
        val state=ShiftRepository(db).snapshot()
        val shift=state.active ?: throw CatalogFailure("shift","Для возврата откройте смену")
        if(actorId!=shift.actorId) throw CatalogFailure("owner","Недопустимый оператор возврата")
        SplitPaymentRepository(db).requireNone()
        if(state.revision==Long.MAX_VALUE) conflict()
        val saved=ReceiptCodec.decode(receipt.payload)
        if(receipt.total!=saved.quote.total || receipt.cash!=saved.payment.cash || receipt.card!=saved.payment.card ||
            receipt.deliveryCashOut!=saved.payment.deliveryCashOut || receipt.sessionId!=saved.cart.sessionId ||
            receipt.shiftId!=saved.checkout.shiftId || receipt.commandId!=saved.checkout.commandId) conflict()
        val amounts=RefundMoney.plan(saved.quote,saved.payment,state.currentCash)
        val work=WorkspaceRepository(db).snapshot()
        val balances=db.catalog().allBalances().associate {it.productId to RestockBalance(it.quantity,it.revision)}
        val stock=HistoricalRestock.plan(saved.consumption,saved.cart.graph,work.graph,balances)
        val token=CatalogRules.fingerprint("refund.preview.v1",receiptId,receipt.payload,shift.id,state.revision,
            actorId,state.currentCash,stockPayload(stock))
        RefundPreview(receiptId,shift.id,state.revision,actorId,token,amounts,stock)
    }
    suspend fun commit(command: String,submitted: RefundPreview): String=InstallationGate.write {db.withTransaction {
        require(command.isNotBlank() && command.length<=100)
        // Caller-owned collections never become authoritative write data.
        val fp=CatalogRules.fingerprint("refund.commit.v1",submitted.receiptId,submitted.shiftId,submitted.revision,submitted.actorId,submitted.token)
        db.catalog().result(command)?.let {old->
            if(old.fingerprint!=fp) conflict()
            check(db.refunds().refund(old.resultId)?.commandId==command) {"Не найден сохранённый возврат"}
            return@withTransaction old.resultId
        }
        val actual=preview(submitted.receiptId,submitted.actorId)
        if(actual!=submitted) conflict()
        val receipt=db.sales().receipt(actual.receiptId) ?: conflict()
        val shift=ShiftRepository(db).requireActive(actual.shiftId,actual.revision)
        val now=clock.millis()
        if(now<receipt.createdAt || now<shift.openedAt) throw CatalogFailure("clock","Время возврата раньше продажи или открытия смены")
        val id=UUID.randomUUID().toString()
        val hash=CatalogRules.fingerprint("refund.receipt.v1",receipt.payload)
        val payload=JSONObject().put("version",1).put("currency","BYN").put("receipt",JSONObject(receipt.payload))
            .put("stock",JSONArray(stockPayload(actual.stock))).put("cashDelta",actual.amounts.cashDelta)
            .put("expectedCashAfter",actual.amounts.expectedCashAfter).put("previewToken",actual.token).toString()
        db.refunds().insert(RefundEntity(id,receipt.id,command,shift.id,actual.actorId,now,actual.amounts.total,
            actual.amounts.cash,actual.amounts.card,actual.amounts.deliveryCashCredit,hash,payload));wrote()
        actual.stock.forEach {change->
            db.catalog().updateBalance(BalanceEntity(change.productId,change.after,change.nextRevision));wrote()
            db.catalog().movement(MovementEntity(UUID.randomUUID().toString(),change.productId,change.delta,"refund",
                CatalogRules.fingerprint("refund.stock.v1",command,change.productId),now,id));wrote()
        }
        db.shifts().register(ShiftRegisterEntity(revision=actual.revision+1,lastShiftId=shift.id));wrote()
        db.catalog().result(ResultEntity(command,fp,id));wrote()
        db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,actual.actorId,"sale.refund",id,now));wrote()
        id
    }}
}
