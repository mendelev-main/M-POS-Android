package com.mendelev.mpos.data

import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Editor policies only; persistence, authorization and media have separate boundaries. */
object MPosCatalogEditEngine {
    private fun trim(s: String) = MPosOrderContextEngine.trim(s)
    private fun rows(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
    private fun same(a: Any?, b: Any?) = if (a is Number && b is Number) a.toDouble() == b.toDouble() else a == b
    private fun key(p: JSONObject): String = trim(if (MPosJsonNumbers.truthy(p.opt("category"))) p.getString("category") else "").ifEmpty { "Без категории" }
    private fun failure(message: String): JSONObject = reply().put("allowed", false).put("message", message)
    private fun reply() = JSONObject().put("ok", true).put("authoritative", true).put("source", "native-catalog-edit")
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        if (input.getString("operation") == "product") return product(input)
        val state = JSONObject(input.getJSONObject("state").toString())
        val products = state.getJSONArray("products"); val order = state.getJSONArray("categoryOrder")
        val name = input.optString("name")
        if (input.getString("operation") == "deleteCheck") {
            if (rows(products).any { key(it) == name }) return failure("Нельзя удалить категорию: в ней есть товары")
            return reply().put("allowed", (0 until order.length()).any { order.get(it) == name })
        }
        val menu = state.getJSONObject("categoryOnlineMenu"); val online = state.getJSONObject("categoryOnlineOrder")
        if (input.getString("operation") == "channel") {
            val map = if (input.optString("channel") == "menu") menu else online
            val enabled = map.opt(name) == false; map.put(name, enabled)
            return reply().put("allowed", true).put("state", state).put("enabled", enabled)
        }
        require(input.getString("operation") == "save")
        val normalized = trim(input.getString("name")); if (normalized.isEmpty()) return failure("Введите название категории")
        val old = input.opt("oldName"); val adding = old == null || old === JSONObject.NULL
        if ((0 until order.length()).any { val c = order.getString(it); c.lowercase(Locale.ROOT) == normalized.lowercase(Locale.ROOT) && c != old }) return failure("Такая категория уже существует")
        val colors = state.getJSONObject("categoryColors"); val symbols = state.getJSONObject("categorySymbols")
        var navigationChanged = false
        if (adding) order.put(normalized) else {
            for (i in 0 until order.length()) if (order.get(i) == old) { order.put(i, normalized); break }
            for (p in rows(products)) if (key(p) == old) p.put("category", normalized)
            for (t in rows(state.getJSONArray("layoutTiles"))) if (t.opt("type") == "category" && t.opt("id") == old) t.put("id", normalized)
            if (old != normalized) {
                val nav = state.getJSONObject("posNavigation").getJSONArray("categories")
                rows(nav).firstOrNull { it.opt("category") == old }?.let { it.put("category", normalized); navigationChanged = true }
                for (map in listOf(colors, symbols, menu, online)) map.remove(old as String)
            }
        }
        colors.put(normalized, input.getString("color"))
        menu.put(normalized, input.opt("menu") != false); online.put(normalized, input.opt("order") != false)
        val symbol = input.getString("symbol"); if (symbol.isEmpty()) symbols.remove(normalized) else symbols.put(normalized, symbol)
        return reply().put("allowed", true).put("state", state).put("adding", adding).put("navigationChanged", navigationChanged)
    }
    private fun product(input: JSONObject): JSONObject {
        if (trim(input.getString("name")).isEmpty()) return failure("Введите название")
        val type = input.getString("type")
        if (type == "composite" && input.getJSONArray("components").length() == 0) return failure("Добавьте хотя бы один товар в состав")
        val products = rows(input.getJSONArray("products")); val id = input.opt("editingId")
        val existing = products.firstOrNull { same(it.opt("id"), id) }
        if (MPosJsonNumbers.truthy(id) && existing?.opt("type") != type) {
            val history = rows(input.getJSONArray("orders")).any { o -> !MPosJsonNumbers.truthy(o.opt("returnedAt")) &&
                o.optJSONObject("stockConsumption")?.optJSONArray("items")?.let { items -> rows(items).any { same(it.opt("productId"), id) } } == true }
            if (history) return failure("Нельзя изменить тип: товар нужен для возврата ранее проданных чеков")
        }
        fun depends(start: Any?, target: Any?, seen: MutableSet<Any?>): Boolean {
            if (same(start, target)) return true
            if (!seen.add(start)) return false
            val p = products.firstOrNull { same(it.opt("id"), start) } ?: return false
            if (p.opt("type") != "composite") return false
            return rows(p.optJSONArray("components") ?: JSONArray()).any { depends(it.opt("productId"), target, seen) }
        }
        if (existing != null && existing.opt("type") != type && (MPosJsonNumbers.truthy(existing.opt("stockUnit")) || products.any { !same(it.opt("id"), id) && depends(it.opt("id"), id, mutableSetOf()) }))
            return failure("Тип товара с единицами или связями нельзя менять: создайте отдельный товар")
        return reply().put("allowed", true)
    }
}
