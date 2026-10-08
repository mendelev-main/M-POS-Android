package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.MPosAvailabilityEngine
import com.mendelev.mpos.data.MPosJsonNumbers
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import java.util.Locale

/** Live search preserves mounted-tile scope and strict string IDs from getProduct(dataset.id). */
object MPosWorkspaceSearchModel {
    private val russian = Locale.forLanguageTag("ru")
    fun query(search: String): String = MPosOrderContextEngine.trim(search).lowercase(russian)
    fun names(products: JSONArray): Map<String, String> {
        val names = linkedMapOf<String, String>()
        for (index in 0 until products.length()) {
            val product = products.optJSONObject(index) ?: continue
            val id = product.opt("id") as? String ?: continue
            // Array.find uses the first exact ID, including an empty ID.
            if (!names.containsKey(id)) names[id] = MPosAvailabilityEngine.text(
                MPosJsonNumbers.fallback(product.opt("name"), "")
            ).lowercase(russian)
        }
        return names
    }
    fun visibility(search: String, tiles: JSONArray, names: Map<String, String>): JSONArray {
        val q = query(search)
        return JSONArray((0 until tiles.length()).map { index ->
            val tile = tiles.getJSONObject(index)
            q.isEmpty() || (tile.opt("type") == "product" &&
                ((tile.opt("id") as? String)?.let { names[it]?.contains(q) } == true))
        })
    }
}
