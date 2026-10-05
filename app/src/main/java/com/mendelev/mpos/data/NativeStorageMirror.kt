package com.mendelev.mpos.data

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NativeStorageMirror(
    private val database: MPosDatabase,
    private val scope: LifecycleCoroutineScope,
    private val onResult: (JSONObject) -> Unit,
) {
    private val shadowDao = database.legacyStorageShadowDao()
    private val catalogDao = database.catalogProjectionDao()
    private val catalogRepository = MPosCatalogRepository(database)
    private val employeeDao = database.employeeProjectionDao()
    private val employeeRepository = MPosEmployeeRepository(database)
    private val shiftDao = database.shiftProjectionDao()
    private val shiftRepository = MPosShiftRepository(database)
    private val orderDao = database.orderProjectionDao()
    private val orderRepository = MPosOrderRepository(database)
    private val parkedDao = database.parkedOrderProjectionDao()
    private val parkedRepository = MPosParkedOrderRepository(database)

    fun handle(payload: JSONObject) {
        val requestId = payload.optString("requestId")
        when (payload.optString("action")) {
            "put" -> {
                val key = payload.optString("key")
                val serialized = payload.optString("payload", null)
                if (key.isBlank() || serialized == null) {
                    result(requestId, false, "invalid shadow storage payload")
                    return
                }
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        shadowDao.upsert(
                            LegacyStorageShadowEntity(
                                key = key,
                                payload = serialized,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                        val projectionOk = when (key) {
                            "products" -> runCatching { projectCatalog(serialized) }.isSuccess
                            "employees" -> runCatching { projectEmployees(serialized) }.isSuccess
                            "shifts" -> runCatching { projectShifts(serialized) }.isSuccess
                            "orders" -> runCatching { projectOrders(serialized) }.isSuccess
                            "parked" -> runCatching { projectParkedOrders(serialized) }.isSuccess
                            else -> true
                        }
                        result(requestId, true, projectionOk = projectionOk)
                    }.onFailure {
                        result(requestId, false, it.localizedMessage ?: "shadow write failed")
                    }
                }
            }

            "remove" -> {
                val key = payload.optString("key")
                if (key.isBlank()) {
                    result(requestId, false, "invalid shadow storage key")
                    return
                }
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        shadowDao.delete(key)
                        when (key) {
                            "products" -> database.withTransaction {
                                catalogDao.clearProducts()
                                catalogDao.clearCategories()
                            }
                            "employees" -> employeeDao.clear()
                            "shifts" -> database.withTransaction {
                                shiftDao.clearMovements()
                                shiftDao.clearShifts()
                            }
                            "orders" -> database.withTransaction {
                                orderDao.clearPayments()
                                orderDao.clearLines()
                                orderDao.clearOrders()
                            }
                            "parked" -> database.withTransaction {
                                parkedDao.clearLines()
                                parkedDao.clearOrders()
                            }
                        }
                    }.onSuccess { result(requestId, true) }
                        .onFailure { result(requestId, false, it.localizedMessage ?: "shadow delete failed") }
                }
            }

            "parkedOrderParity" -> scope.launch(Dispatchers.IO) {
                runCatching { parkedRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        onResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "parked order parity failed") }
            }

            "orderParity" -> scope.launch(Dispatchers.IO) {
                runCatching { orderRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        onResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "order parity failed") }
            }

            "shiftParity" -> scope.launch(Dispatchers.IO) {
                runCatching { shiftRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        onResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "shift parity failed") }
            }

            "employeeParity" -> scope.launch(Dispatchers.IO) {
                runCatching { employeeRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        onResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "employee parity failed") }
            }

            "catalogSnapshot" -> scope.launch(Dispatchers.IO) {
                runCatching { catalogRepository.snapshot() }
                    .onSuccess { snapshot ->
                        snapshot.put("requestId", requestId)
                        onResult(snapshot)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "catalog snapshot failed") }
            }

            "catalogParity" -> scope.launch(Dispatchers.IO) {
                runCatching { catalogRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        onResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "catalog parity failed") }
            }

            "stats" -> scope.launch(Dispatchers.IO) {
                runCatching {
                    listOf(
                        shadowDao.count(),
                        catalogDao.productCount(),
                        catalogDao.categoryCount(),
                        employeeDao.count(),
                        shiftDao.shiftCount(),
                        shiftDao.movementCount(),
                        orderDao.orderCount(),
                        orderDao.lineCount(),
                        orderDao.paymentCount(),
                        parkedDao.orderCount(),
                        parkedDao.lineCount(),
                    )
                }.onSuccess { counts ->
                    onResult(
                        JSONObject()
                            .put("requestId", requestId)
                            .put("ok", true)
                            .put("count", counts[0])
                            .put("catalogProducts", counts[1])
                            .put("catalogCategories", counts[2])
                            .put("employees", counts[3])
                            .put("shifts", counts[4])
                            .put("cashMovements", counts[5])
                            .put("orders", counts[6])
                            .put("orderLines", counts[7])
                            .put("payments", counts[8])
                            .put("parkedOrders", counts[9])
                            .put("parkedOrderLines", counts[10])
                            .put("authoritative", false)
                    )
                }.onFailure {
                    result(requestId, false, it.localizedMessage ?: "shadow stats failed")
                }
            }

            else -> result(requestId, false, "unknown storage action")
        }
    }

    private suspend fun projectCatalog(serialized: String) {
        val source = JSONArray(serialized)
        val now = System.currentTimeMillis()
        val products = ArrayList<ProductProjectionEntity>(source.length())
        val categoryOrder = linkedMapOf<String, Int>()
        val categoryCounts = linkedMapOf<String, Int>()

        for (index in 0 until source.length()) {
            val product = source.optJSONObject(index) ?: continue
            val id = product.optString("id").trim()
            if (id.isEmpty()) continue

            val category = product.optString("category").trim().ifEmpty { "Без категории" }
            if (!categoryOrder.containsKey(category)) categoryOrder[category] = categoryOrder.size
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1

            products += ProductProjectionEntity(
                id = id,
                name = product.optString("name"),
                category = category,
                type = product.optString("type", "simple"),
                sortIndex = index,
                payload = product.toString(),
                updatedAt = now,
            )
        }

        val categories = categoryOrder.map { (name, sortIndex) ->
            CategoryProjectionEntity(
                name = name,
                sortIndex = sortIndex,
                productCount = categoryCounts[name] ?: 0,
                updatedAt = now,
            )
        }

        database.withTransaction {
            catalogDao.clearProducts()
            catalogDao.clearCategories()
            if (products.isNotEmpty()) catalogDao.insertProducts(products)
            if (categories.isNotEmpty()) catalogDao.insertCategories(categories)
        }
    }

    private suspend fun projectEmployees(serialized: String) {
        val source = JSONArray(serialized)
        val now = System.currentTimeMillis()
        val employees = ArrayList<EmployeeProjectionEntity>(source.length())

        for (index in 0 until source.length()) {
            val employee = source.optJSONObject(index) ?: continue
            val id = employee.optString("id").trim()
            if (id.isEmpty()) continue

            employees += EmployeeProjectionEntity(
                id = id,
                name = employee.optString("name"),
                phone = employee.optString("phone"),
                role = employee.optString("role", "employee"),
                sortIndex = index,
                payload = employee.toString(),
                updatedAt = now,
            )
        }

        database.withTransaction {
            employeeDao.clear()
            if (employees.isNotEmpty()) employeeDao.insertAll(employees)
        }
    }

    private suspend fun projectShifts(serialized: String) {
        val source = JSONArray(serialized)
        val now = System.currentTimeMillis()
        val shifts = ArrayList<ShiftProjectionEntity>(source.length())
        val movements = ArrayList<CashMovementProjectionEntity>()

        for (shiftIndex in 0 until source.length()) {
            val shift = source.optJSONObject(shiftIndex) ?: continue
            val shiftId = shift.optString("id").trim()
            if (shiftId.isEmpty()) continue

            shifts += ShiftProjectionEntity(
                id = shiftId,
                status = shift.optString("status"),
                employeeId = shift.optString("employeeId"),
                employeeName = shift.optString("employeeName"),
                employeePhone = shift.optString("employeePhone"),
                openedAt = shift.optLong("openedAt"),
                closedAt = shift.optLong("closedAt"),
                openingCash = shift.optDouble("openingCash"),
                countedCash = shift.optDouble("countedCash"),
                sortIndex = shiftIndex,
                payload = shift.toString(),
                updatedAt = now,
            )

            val sourceMovements = shift.optJSONArray("cashMovements") ?: JSONArray()
            for (movementIndex in 0 until sourceMovements.length()) {
                val movement = sourceMovements.optJSONObject(movementIndex) ?: continue
                val movementId = movement.optString("id").trim()
                if (movementId.isEmpty()) continue
                movements += CashMovementProjectionEntity(
                    id = movementId,
                    shiftId = shiftId,
                    type = movement.optString("type"),
                    subtype = movement.optString("subtype"),
                    amount = movement.optDouble("amount"),
                    timestamp = movement.optLong("timestamp"),
                    note = movement.optString("note"),
                    sortIndex = movementIndex,
                    payload = movement.toString(),
                    updatedAt = now,
                )
            }
        }

        database.withTransaction {
            shiftDao.clearMovements()
            shiftDao.clearShifts()
            if (shifts.isNotEmpty()) shiftDao.insertShifts(shifts)
            if (movements.isNotEmpty()) shiftDao.insertMovements(movements)
        }
    }

    private suspend fun projectOrders(serialized: String) {
        val source = JSONArray(serialized)
        val now = System.currentTimeMillis()
        val orders = ArrayList<OrderProjectionEntity>(source.length())
        val lines = ArrayList<OrderLineProjectionEntity>()
        val payments = ArrayList<PaymentProjectionEntity>()

        for (orderIndex in 0 until source.length()) {
            val order = source.optJSONObject(orderIndex) ?: continue
            val orderId = order.optString("id").trim()
            if (orderId.isEmpty()) continue

            orders += OrderProjectionEntity(
                id = orderId,
                shiftId = order.optString("shiftId"),
                receiptNumber = order.optInt("receiptNumber"),
                receiptDisplayNumber = order.optString("receiptDisplayNumber"),
                employeeId = order.optString("employeeId"),
                employeeName = order.optString("employeeName"),
                method = order.optString("method"),
                total = order.optDouble("total"),
                orderType = order.optString("orderType"),
                orderLabel = order.optString("orderLabel"),
                deliveryFee = order.optDouble("deliveryFee"),
                source = order.optString("source"),
                webOrderId = order.optString("webOrderId"),
                timestamp = order.optLong("timestamp"),
                returnedAt = order.optLong("returnedAt"),
                returnAmount = order.optDouble("returnAmount"),
                sortIndex = orderIndex,
                payload = order.toString(),
                updatedAt = now,
            )

            val sourceLines = order.optJSONArray("items") ?: JSONArray()
            for (lineIndex in 0 until sourceLines.length()) {
                val line = sourceLines.optJSONObject(lineIndex) ?: continue
                lines += OrderLineProjectionEntity(
                    id = MPosOrderRepository.lineKey(orderId, lineIndex),
                    orderId = orderId,
                    productId = line.optString("productId"),
                    name = line.optString("name"),
                    category = line.optString("category"),
                    qty = line.optDouble("qty"),
                    price = line.optDouble("price"),
                    cost = line.optDouble("cost"),
                    discountName = line.optString("discountName"),
                    discountType = line.optString("discountType"),
                    discountValue = line.optDouble("discountValue"),
                    comment = line.optString("comment"),
                    sortIndex = lineIndex,
                    payload = line.toString(),
                    updatedAt = now,
                )
            }

            val sourcePayments = order.optJSONArray("payments") ?: JSONArray()
            for (paymentIndex in 0 until sourcePayments.length()) {
                val payment = sourcePayments.optJSONObject(paymentIndex) ?: continue
                payments += PaymentProjectionEntity(
                    id = MPosOrderRepository.paymentKey(orderId, paymentIndex),
                    orderId = orderId,
                    method = payment.optString("method"),
                    amount = payment.optDouble("amount"),
                    cashGiven = payment.optDouble("cashGiven"),
                    changeAmount = payment.optDouble("change"),
                    sortIndex = paymentIndex,
                    payload = payment.toString(),
                    updatedAt = now,
                )
            }
        }

        database.withTransaction {
            orderDao.clearPayments()
            orderDao.clearLines()
            orderDao.clearOrders()
            if (orders.isNotEmpty()) orderDao.insertOrders(orders)
            if (lines.isNotEmpty()) orderDao.insertLines(lines)
            if (payments.isNotEmpty()) orderDao.insertPayments(payments)
        }
    }

    private suspend fun projectParkedOrders(serialized: String) {
        val source = JSONArray(serialized)
        val now = System.currentTimeMillis()
        val orders = ArrayList<ParkedOrderProjectionEntity>(source.length())
        val lines = ArrayList<ParkedOrderLineProjectionEntity>()

        for (orderIndex in 0 until source.length()) {
            val order = source.optJSONObject(orderIndex) ?: continue
            val orderId = order.optString("id").trim()
            if (orderId.isEmpty()) continue
            val customer = order.optJSONObject("customer") ?: JSONObject()

            orders += ParkedOrderProjectionEntity(
                id = orderId,
                receiptDisplayNumber = order.optString("receiptDisplayNumber"),
                total = order.optDouble("total"),
                subtotal = order.optDouble("subtotal"),
                orderLabel = order.optString("orderLabel"),
                orderType = order.optString("orderType"),
                deliveryFee = order.optDouble("deliveryFee"),
                comment = order.optString("comment"),
                source = order.optString("source"),
                webOrderId = order.optString("webOrderId"),
                webOrderStatus = order.optString("webOrderStatus"),
                employeeName = order.optString("employeeName"),
                customerId = customer.optString("id"),
                customerName = customer.optString("name"),
                customerPhone = customer.optString("phone"),
                createdAt = order.optLong("createdAt"),
                kitchenPrinted = order.optBoolean("kitchenPrinted"),
                sortIndex = orderIndex,
                payload = order.toString(),
                updatedAt = now,
            )

            val items = order.optJSONArray("items") ?: JSONArray()
            for (lineIndex in 0 until items.length()) {
                val line = items.optJSONObject(lineIndex) ?: continue
                lines += ParkedOrderLineProjectionEntity(
                    id = MPosParkedOrderRepository.lineKey(orderId, lineIndex),
                    parkedOrderId = orderId,
                    productId = line.optString("productId"),
                    name = line.optString("name"),
                    category = line.optString("category"),
                    qty = line.optDouble("qty"),
                    price = line.optDouble("price"),
                    comment = line.optString("comment"),
                    sortIndex = lineIndex,
                    payload = line.toString(),
                    updatedAt = now,
                )
            }
        }

        database.withTransaction {
            parkedDao.clearLines()
            parkedDao.clearOrders()
            if (orders.isNotEmpty()) parkedDao.insertOrders(orders)
            if (lines.isNotEmpty()) parkedDao.insertLines(lines)
        }
    }

    private fun result(
        requestId: String,
        ok: Boolean,
        message: String? = null,
        projectionOk: Boolean? = null,
    ) {
        val result = JSONObject()
            .put("requestId", requestId)
            .put("ok", ok)
            .put("authoritative", false)
        if (message != null) result.put("message", message)
        if (projectionOk != null) result.put("projectionOk", projectionOk)
        onResult(result)
    }
}
