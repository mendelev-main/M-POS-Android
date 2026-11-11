package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.CatalogRules
import java.math.BigDecimal

/** Source IDs are Web String(id), scoped by kind. Never reuse local owner/register/command IDs. */
object WebIdentity {
    fun source(value: BackupJson?): String=when(value) {
        is BackupJson.Str->value.value.also {if(it.isBlank() || it.length>512) backupBad("identity")}
        is BackupJson.Num->try {value.decimal().toBigIntegerExact().also {if(it.signum()==0 || it.abs()>9007199254740991L.toBigInteger()) backupBad("identity")}.toString()}
            catch(e:BackupFailure) {throw e} catch(_:ArithmeticException) {backupBad("identity")}
        else->backupBad("identity")
    }
    fun mapped(kind: String,source: String): String {
        require(kind in setOf("product","supplier","employee","shift","category","modifierGroup","modifierOption","folder","receipt","receiving","purchaseOrder","inventory","discount","receiptLabel","printer"))
        return "web-"+CatalogRules.fingerprint("web-identity-v1",kind,source)
    }
}
internal object WebValues {
    fun obj(value: BackupJson?)=(value as? BackupJson.Obj)?.fields ?: backupBad("mapping")
    fun rows(value: BackupJson?): List<BackupJson> = when(value) {null,BackupJson.Nil->emptyList();is BackupJson.Arr->value.values;else->backupBad("mapping")}
    fun text(value: BackupJson?,default: String=""): String=when(value) {null,BackupJson.Nil->default;is BackupJson.Str->value.value;else->backupBad("mapping")}
    fun bool(value: BackupJson?,default: Boolean=false): Boolean=when(value) {null,BackupJson.Nil->default;is BackupJson.Bool->value.value;else->backupBad("mapping")}
    fun decimal(value: BackupJson?,default: String?=null,signed: Boolean=false): BigDecimal {
        val raw=when(value) {null,BackupJson.Nil->default ?: backupBad("number");is BackupJson.Num->value.raw;is BackupJson.Str->value.value;else->backupBad("number")}
        if(raw.length>128) backupBad("number")
        return try {BigDecimal(raw).also {if(it.precision()>128 || it.scale().toLong() !in -128L..128L || !signed && it.signum()<0) backupBad("number")}}
        catch(e:BackupFailure) {throw e} catch(_:NumberFormatException) {backupBad("number")}
    }
    fun number(value: BackupJson?,default: String?=null,signed: Boolean=false,scale: Int=12): String {
        val raw=decimal(value,default,signed)
        val n=if(scale==12) raw.setScale(12,java.math.RoundingMode.HALF_UP).stripTrailingZeros() else raw
        if(raw.signum()!=0 && n.signum()==0) backupBad("number")
        if(n.stripTrailingZeros().scale()>scale || n.abs()>BigDecimal("999999999."+"9".repeat(scale))) backupBad("number")
        return CatalogRules.canonical(n)
    }
    fun minor(value: BackupJson?,signed: Boolean=false): Long? {
        if(value==null || value===BackupJson.Nil) return null
        return try {decimal(value,signed=signed).let {raw->
            val rounded=raw.setScale(2,java.math.RoundingMode.HALF_UP)
            if((raw-rounded).abs()>BigDecimal("0.00000001")) backupBad("money")
            rounded.movePointRight(2).longValueExact()
        }}
        catch(e:BackupFailure) {throw e} catch(_:ArithmeticException) {backupBad("money")}
    }
    fun time(value: BackupJson?): Long? {
        if(value==null || value===BackupJson.Nil) return null
        if(value !is BackupJson.Num) backupBad("time")
        return try {value.decimal().longValueExact().also {if(it !in 0..9007199254740991L) backupBad("time")}}
        catch(e:BackupFailure) {throw e} catch(_:ArithmeticException) {backupBad("time")}
    }
}
