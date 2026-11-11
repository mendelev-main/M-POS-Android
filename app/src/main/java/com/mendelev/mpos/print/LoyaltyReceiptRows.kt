package com.mendelev.mpos.print

import com.mendelev.mpos.data.local.LoyaltyEffect
import com.mendelev.mpos.data.local.ReceiptSnapshot
import org.json.JSONObject

internal object LoyaltyReceiptRows {
    fun build(snapshot: ReceiptSnapshot,effect: LoyaltyEffect?): List<Pair<String,String>> {
        if(snapshot.cart.loyalty==null || effect==null || !effect.saleDone || effect.blocked.isNotEmpty() || effect.reverseRequested) return emptyList()
        return progress(effect.result)
    }
    /** Same confirmed progress/events precedence and twelve dots per row as Web. */
    internal fun progress(result: String): List<Pair<String,String>> {
        val data=runCatching {JSONObject(result)}.getOrNull() ?: return emptyList()
        val programs=data.optJSONArray("programs") ?: return emptyList()
        val events=data.optJSONArray("events")
        return (0 until programs.length()).mapNotNull {i->
            val p=programs.optJSONObject(i) ?: return@mapNotNull null
            val goal=p.opt("required_quantity")?.toString()?.toBigDecimalOrNull() ?: return@mapNotNull null
            if(goal.stripTrailingZeros().scale()>0 || goal< java.math.BigDecimal.ONE || goal>1000.toBigDecimal()) return@mapNotNull null
            val event=(0 until (events?.length() ?: 0)).mapNotNull {events?.optJSONObject(it)}.find {it.optString("programId")==p.optString("id")}
            fun number(key: String)=(event?.opt(key) ?: p.opt(key))?.toString()?.toBigDecimalOrNull()
            val progress=number("progress") ?: return@mapNotNull null
            val rewards=number("rewards") ?: return@mapNotNull null
            if(progress.signum()<0 || rewards.signum()<0) return@mapNotNull null
            val count=goal.toInt()
            val filled=if(rewards.signum()>0)count else progress.min(goal).toInt()
            val name=p.optString("name").trim().ifEmpty {"Программа лояльности"}
            name to (0 until count).chunked(12).joinToString("\n") {row->row.joinToString(" ") {if(it<filled)"●" else "○"}}
        }
    }
}
