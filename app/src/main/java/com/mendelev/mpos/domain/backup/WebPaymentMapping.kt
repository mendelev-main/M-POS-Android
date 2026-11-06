package com.mendelev.mpos.domain.backup

import java.math.BigDecimal

/** Imported evidence, NOT native paid commands or permission to charge the remaining amount. */
data class WebPaymentPart(val position: Int,val method: String,val amount: Long,val paid: Boolean,
    val given: Long?,val change: Long?)
data class WebPaymentFacts(val location: String,val total: Long,val parts: List<WebPaymentPart>,
    val paid: Long,val unpaid: Long,val paidCash: Long,val paidCard: Long,val blockers: Set<String>)

/** Pure version-1 draft conversion. No catalog repricing, cash movement, transport or command synthesis. */
object WebPaymentMapping {
    private const val MAX_SAFE=9007199254740991L
    fun map(document: WebBackupDocument): List<WebPaymentFacts> {
        val root=document.root.fields
        val sessions=buildList {
            (root["currentOrderSession"] as? BackupJson.Obj)?.let {add("current" to it)}
            (root["parked"] as? BackupJson.Arr)?.values?.forEachIndexed {i,value->
                add("parked:$i" to (value as? BackupJson.Obj ?: backupBad("payment")))
            }
        }
        return sessions.mapNotNull {(location,session)->mapSession(location,session)}
    }
    private fun mapSession(location: String,session: BackupJson.Obj): WebPaymentFacts? {
        val draft=session.fields["paymentDraft"]?.takeUnless {it===BackupJson.Nil} ?: return null
        val fields=(draft as? BackupJson.Obj)?.fields ?: backupBad("payment")
        if(integer(fields["version"])!=1L) backupBad("paymentVersion")
        val total=integer(fields["totalCents"])
        val rows=(fields["parts"] as? BackupJson.Arr)?.values ?: backupBad("payment")
        if(rows.size !in 2..10) backupBad("payment")
        val blockers=linkedSetOf<String>()
        val parts=rows.mapIndexed {i,row->
            val p=(row as? BackupJson.Obj)?.fields ?: backupBad("payment")
            val method=(p["method"] as? BackupJson.Str)?.value ?: backupBad("payment")
            if(method !in setOf("cash","card")) backupBad("payment")
            val amount=minor(p["amount"])
            val paid=(p["paid"] as? BackupJson.Bool)?.value ?: backupBad("payment")
            val given=optionalMinor(p["cashGiven"]);val change=optionalMinor(p["change"])
            if(method=="card" && (given!=null || change!=null)) backupBad("payment")
            if(method=="cash") {
                if(given!=null && given<amount || given!=null && change!=null && given-amount!=change) backupBad("payment")
                // Keep missing source tender unknown. Never invent given=amount or change=0.
                if(paid && (given==null || change==null)) blockers.add("paidCashTenderUnknown")
                if(given==null && change!=null) blockers.add("cashGivenUnknown")
            }
            WebPaymentPart(i,method,amount,paid,given,change)
        }
        fun sum(predicate: (WebPaymentPart)->Boolean)=parts.filter(predicate).fold(0L) {a,p->
            try {Math.addExact(a,p.amount).also {if(it>MAX_SAFE) backupBad("payment")}} catch(_:ArithmeticException) {backupBad("payment")}
        }
        if(sum {true}!=total || parts.none {it.paid}) backupBad("payment")
        if(parts.all {it.paid}) blockers.add("allPartsPaidReceiptReconciliation")
        val rewards=session.fields["loyaltyRedemptions"]
        if(rewards!=null && rewards!==BackupJson.Nil) {
            val values=(rewards as? BackupJson.Obj)?.fields?.values ?: backupBad("payment")
            if(values.any {decimal(it).signum()>0}) blockers.add("paidLoyaltyEntitlement")
        }
        return WebPaymentFacts(location,total,parts,sum {it.paid},sum {!it.paid},
            sum {it.paid && it.method=="cash"},sum {it.paid && it.method=="card"},blockers)
    }
    private fun optionalMinor(value: BackupJson?)=value?.takeUnless {it===BackupJson.Nil}?.let(::minor)
    private fun decimal(value: BackupJson?): BigDecimal {
        val raw=when(value) {is BackupJson.Num->value.raw;is BackupJson.Str->value.value;else->backupBad("payment")}
        if(raw.length>128) backupBad("payment")
        return try {BigDecimal(raw).also {if(it.precision()>128 || it.scale().toLong() !in -128L..128L || it.signum()<0) backupBad("payment")}}
        catch(e:BackupFailure) {throw e} catch(_:NumberFormatException) {backupBad("payment")}
    }
    private fun bounded(value: BigDecimal): Long=try {value.longValueExact().also {if(it !in 0..MAX_SAFE) backupBad("payment")}}
        catch(e:BackupFailure) {throw e} catch(_:ArithmeticException) {backupBad("payment")}
    private fun minor(value: BackupJson?)=bounded(decimal(value).movePointRight(2))
    private fun integer(value: BackupJson?): Long {
        if(value !is BackupJson.Num) backupBad("payment")
        return bounded(decimal(value))
    }
}
