package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.domain.refund.RefundMoney
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReceiptPaymentCorrectionTest {
    private fun receipt(): ReceiptEntity {
        val cart=CurrentCart(sessionId="session",lines=listOf(CartLine("line","p","Товар","10",false,"10","1","piece",emptyList(),emptyList(),emptyMap())))
        val q=SaleMoney.quote(listOf(SaleLineInput("line","10","1")))
        val d=CheckoutDraft("sale","session",0,"shift",0,"actor",parts=listOf(PaymentInput("cash",1000,2000)))
        val payment=SaleMoney.payments(q,d.parts,0)
        return ReceiptEntity("r","sale","session","shift",1,1,1000,1000,0,0,ReceiptCodec.encode(cart,d,q,payment,emptyMap(),emptyList()))
    }
    @Test fun switchingTwiceKeepsOriginalGoodsAmountsAndUnknownTender() {
        val original=receipt()
        val card=ReceiptPaymentHistory.change(original,"card","edit1",2,"actor","Сотрудник",false)
        val cash=ReceiptPaymentHistory.change(card,"cash","edit2",3,"owner","Администратор",true)
        val s=ReceiptCodec.decode(cash.payload)
        assertEquals(original.payload,s.originalReceiptJson)
        assertEquals(1000L,cash.cash);assertEquals(0L,cash.card)
        assertNull(s.payment.parts.single().given);assertNull(s.payment.parts.single().change)
        assertEquals(ReceiptCodec.decode(original.payload).cart,s.cart)
        assertEquals(ReceiptCodec.decode(original.payload).checkout,s.checkout)
        val plan=RefundMoney.plan(s.quote,s.payment,1000,unknownTender=true)
        assertEquals(1000L,plan.cash);assertEquals(0L,plan.expectedCashAfter)
        assertTrue(runCatching {RefundMoney.plan(s.quote,s.payment,1000)}.isFailure)
        ReceiptPaymentHistory.validate(s)
    }
    @Test fun changedAmountsInventedTenderOrBrokenHistoryAreRejected() {
        val changed=ReceiptPaymentHistory.change(receipt(),"card","edit",2,"actor","Сотрудник",false)
        val json=JSONObject(changed.payload)
        json.put("total",999)
        assertTrue(runCatching {ReceiptPaymentHistory.validate(ReceiptCodec.decode(json.toString()))}.isFailure)
        val history=JSONObject(changed.payload);history.getJSONArray("paymentCorrections").getJSONObject(0).put("from","card")
        assertTrue(runCatching {ReceiptPaymentHistory.validate(ReceiptCodec.decode(history.toString()))}.isFailure)
        assertTrue(runCatching {ReceiptPaymentHistory.change(receipt(),"cash","noop",2,"actor","Сотрудник",false)}.isFailure)
    }
}
