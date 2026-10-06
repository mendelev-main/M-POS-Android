package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Read-only advisory stock check. Reservation/deduction still happens at settlement. */
class MPosStockPreflightRepository(private val database: MPosDatabase) {
    suspend fun read(raw: String): JSONObject = database.withTransaction {
        val parser = JSONTokener(raw); val input = parser.nextValue()
        require(input is JSONObject && parser.nextClean() == '\u0000')
        require(input.getInt("version") == 1)
        val items = input.getJSONArray("items")
        val envelope = MPosCatalogStorage(database).read()
        val parsed = if (envelope.getBoolean("found")) JSONTokener(envelope.getString("payload")).nextValue() else JSONObject.NULL
        require(parsed === JSONObject.NULL || parsed is JSONArray)
        val products = parsed as? JSONArray ?: JSONArray()
        val result = JSONObject().put("ok", true).put("authoritative", true).put("source", "room-stock-preflight")
        try {
            MPosRecipeConsumptionEngine.calculate(products, items)
            result.put("allowed", true)
        } catch (error: MPosRecipeConsumptionEngine.StockShortage) {
            result.put("allowed", false).put("reason", "Недостаточно остатка: ${error.productName}")
        } catch (error: Exception) {
            // Local recipe messages only; never expose raw JSON, parser details or SQL.
            val reason = when (error.message) {
                "cyclic recipe" -> "Циклический состав товара"
                "empty recipe", "invalid recipe type" -> "Проверьте состав товара"
                "invalid ingredient quantity" -> "Количество ингредиента должно быть больше нуля"
                "recipe product missing", "recipe ingredient missing" -> "Товар или ингредиент не найден"
                "ingredient quantity overflow" -> "Слишком большое количество ингредиента"
                else -> "Проверьте состав и количества товаров"
            }
            result.put("allowed", false).put("reason", reason)
        }
    }
}
