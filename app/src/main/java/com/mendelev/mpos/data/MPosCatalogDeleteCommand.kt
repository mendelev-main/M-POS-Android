package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Permission, return/recipe guards and all affected documents commit atomically. */
class MPosCatalogDeleteCommand(
    private val database: MPosDatabase,
    private val verifies: (String) -> Boolean = MPosAdministratorCredential::accepts,
) {
    class ReferencedProduct(val productName: String) : IllegalStateException("Товар используется в составном товаре")

    suspend fun commit(raw: String, credential: String = ""): JSONObject = database.withTransaction {
        val input = JSONObject(raw)
        require(input.getInt("version") == 1 && input.getString("operation") == "delete")
        val type = input.getString("type")
        require(type in setOf("product", "category"))
        val id = input.get("id")
        val root = MPosRootSessionRepository(database).read()
        check(!root.recoveryPending) { "Перезапустите M POS для восстановления данных" }
        if (type != "product" || !root.isAdmin) check(verifies(credential)) { "Неверный пароль администратора" }
        val catalog = MPosCatalogStorage(database)
        val archive = MPosOrderStorage(database)
        val workspace = MPosWorkspaceStorage(database)
        val expected = input.getJSONObject("expected")
        fun value(document: JSONObject): Any {
            if (!document.getBoolean("found")) return JSONObject.NULL
            val parser = JSONTokener(document.getString("payload"))
            return parser.nextValue().also { require(parser.nextClean() == '\u0000') }
        }
        val documents = linkedMapOf("products" to value(catalog.read()), "layout" to value(workspace.read("layout")),
            "posNavigation" to value(workspace.read("posNavigation")))
        check(documents.all { (key, value) -> MPosSupplyParity.same(value, expected.get(key)) }) { "Каталог изменился. Откройте подтверждение заново" }
        val products = documents.getValue("products").let { if (it === JSONObject.NULL) JSONArray() else it as JSONArray }
        val layout = documents.getValue("layout").let { if (it === JSONObject.NULL) JSONObject() else it as JSONObject }
        val navigation = documents.getValue("posNavigation").let { if (it === JSONObject.NULL) JSONObject() else it as JSONObject }
        fun filter(array: JSONArray, keep: (JSONObject) -> Boolean) = JSONArray().apply {
            for (i in 0 until array.length()) { val row = array.getJSONObject(i); if (keep(row)) put(row) }
        }
        val tiles = layout.optJSONArray("tiles") ?: JSONArray()
        val nextProducts: JSONArray
        if (type == "product") {
            val orders = value(archive.read()).let { if (it === JSONObject.NULL) JSONArray() else it as JSONArray }
            check((0 until orders.length()).none { i ->
                val order = orders.getJSONObject(i)
                val items = order.optJSONObject("stockConsumption")?.optJSONArray("items") ?: JSONArray()
                !MPosJsonNumbers.truthy(order.opt("returnedAt")) && (0 until items.length()).any { MPosSupplyParity.same(items.getJSONObject(it).opt("productId"), id) }
            }) { "Товар нужен для возврата ранее проданных чеков" }
            val usedIn = (0 until products.length()).map { products.getJSONObject(it) }.firstOrNull { product ->
                val components = product.optJSONArray("components") ?: JSONArray()
                product.opt("type") == "composite" && (0 until components.length()).any { MPosSupplyParity.same(components.getJSONObject(it).opt("productId"), id) }
            }
            if (usedIn != null) throw ReferencedProduct(usedIn.optString("name"))
            nextProducts = filter(products) { !MPosSupplyParity.same(it.opt("id"), id) }
            layout.put("tiles", filter(tiles) { !(it.opt("type") == "product" && MPosSupplyParity.same(it.opt("id"), id)) })
            catalog.write(nextProducts.toString())
        } else {
            check((0 until products.length()).none { i ->
                val product = products.getJSONObject(i)
                val category = if (MPosJsonNumbers.truthy(product.opt("category"))) com.mendelev.mpos.payment.MPosOrderContextEngine.trim(product.getString("category")) else ""
                category.ifEmpty { "Без категории" } == id
            }) { "Нельзя удалить категорию: в ней есть товары" }
            nextProducts = products
            val order = layout.optJSONArray("categoryOrder") ?: JSONArray()
            layout.put("categoryOrder", JSONArray().apply { for (i in 0 until order.length()) if (!MPosSupplyParity.same(order.get(i), id)) put(order.get(i)) })
            for (key in listOf("categoryColors", "categorySymbols", "categoryOnline", "categoryOnlineOrder", "categoryOnlineMenu")) layout.optJSONObject(key)?.remove(id.toString())
            layout.put("tiles", filter(tiles) { !(it.opt("type") == "category" && MPosSupplyParity.same(it.opt("id"), id)) })
            navigation.put("categories", filter(navigation.optJSONArray("categories") ?: JSONArray()) { !MPosSupplyParity.same(it.opt("category"), id) })
            workspace.write("posNavigation", navigation.toString())
        }
        workspace.write("layout", layout.toString())
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-catalog-delete")
            .put("products", nextProducts).put("layout", layout).put("posNavigation", navigation)
    }
}
