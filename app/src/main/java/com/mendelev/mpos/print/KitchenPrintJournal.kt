package com.mendelev.mpos.print

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest

object KitchenPrintDelta {
    fun items(current: List<PrintItem>,previous: Map<String,String>): List<PrintItem> = current.mapNotNull {item->
        val delta=item.quantity.toBigDecimal()-(previous[item.id]?.toBigDecimal() ?: BigDecimal.ZERO)
        if(delta.signum()>0) item.copy(quantity=delta.stripTrailingZeros().toPlainString()) else null
    }
}
/** Claim quantities before dispatch. Ambiguous failures require explicit manual reprint. */
internal class KitchenPrintJournal(private val directory: File) {
    private fun file(session: String,printer: String)=AtomicFile(File(directory,MessageDigest.getInstance("SHA-256").digest("$session:$printer".toByteArray()).joinToString("") {"%02x".format(it)}))
    @Synchronized fun has(session: String,printer: String): Boolean {val f=file(session,printer);return f.baseFile.exists() || File(f.baseFile.path+".bak").exists()}
    @Synchronized fun claim(document: PrintDocument,printer: String): List<PrintItem> {
        val file=file(document.session,printer)
        val previous=if(has(document.session,printer)) JSONObject(file.openRead().bufferedReader().use {it.readText()}).let {p->p.keys().asSequence().associateWith {p.getString(it)}} else emptyMap()
        val delta=KitchenPrintDelta.items(document.items,previous)
        check(directory.isDirectory || directory.mkdirs())
        val next=JSONObject();document.items.forEach {next.put(it.id,it.quantity)}
        val out=file.startWrite()
        try {out.write(next.toString().toByteArray());file.finishWrite(out)} catch(e:Exception) {file.failWrite(out);throw e}
        return delta
    }
}
