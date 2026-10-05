package com.mendelev.mpos.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import org.json.JSONObject

class MPosStorageMirror(
    private val database: MPosDatabase,
    scope: CoroutineScope,
    private val onResult: (JSONObject) -> Unit,
) {
    private val queue = MPosStorageQueue(scope)
    private val writeState = MPosShadowWriteState()

    private data class Command(val action: String, val requestId: String, val key: String, val serialized: String?, val sourceKey: String, val version: Long?)

    fun handle(payload: JSONObject) {
        val action = payload.optString("action")
        val key = payload.optString("key")
        val command = Command(action, payload.optString("requestId"), key, payload.opt("payload") as? String, payload.optString("sourceKey"),
            if (key.isNotBlank() && action in setOf("put", "remove")) writeState.request(key) else null)
        if (!queue.submit({ result(command.requestId, false, "native shadow command failed") }) { dispatch(command) }) {
            result(command.requestId, false, "native shadow queue is full or closed")
        }
    }

    fun close() = queue.close()

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun <T> readAttempt(action: String, block: suspend () -> T): Result<T> = attempt {
        val value = database.withTransaction {
            requireCaughtUp(action)
            block()
        }
        requireCaughtUp(action)
        value
    }

    private fun emitResult(value: JSONObject) {
        value.put("shadowCaughtUp", writeState.caughtUp()).put("pendingShadowKeys", writeState.pendingKeys())
        onResult(value)
    }

    private fun requireCaughtUp(action: String) {
        val keys = when (action) {
            "catalogSnapshot", "catalogParity" -> setOf("products")
            "employeeParity" -> setOf("employees")
            "shiftParity" -> setOf("shifts")
            "orderParity" -> setOf("orders")
            "parkedOrderParity" -> setOf("parked")
            "stockEventParity" -> setOf("receivings", "inventoryHistory")
            "webAcceptanceParity" -> setOf("webOrderAcceptances")
            "webReadyParity" -> setOf("webOrderReadyJournal")
            "criticalStorageJournalParity" -> setOf("criticalStorageJournal")
            else -> return
        }
        check(writeState.caughtUp(keys)) { "native shadow has unapplied changes" }
    }
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
    private val stockEventDao = database.stockEventProjectionDao()
    private val stockEventRepository = MPosStockEventRepository(database)
    private val webAcceptanceDao = database.webAcceptanceProjectionDao()
    private val currentOrderSessionDao = database.currentOrderSessionProjectionDao()
    private val webReadyDao = database.webReadyProjectionDao()
    private val criticalJournalDao = database.criticalStorageJournalProjectionDao()

    private suspend fun dispatch(command: Command) {
        val requestId = command.requestId
        try {
            requireCaughtUp(command.action)
        } catch (_: IllegalStateException) {
            result(requestId, false, "native shadow has unapplied changes")
            return
        }
        when (command.action) {
            "put" -> {
                val key = command.key
                val serialized = command.serialized
                if (key.isBlank() || serialized == null) {
                    result(requestId, false, "invalid shadow storage payload")
                    return
                }
                run {
                    readAttempt(command.action) {
                        database.withTransaction {
                            shadowDao.upsert(
                                LegacyStorageShadowEntity(key, serialized, System.currentTimeMillis())
                            )
                            when (key) {
                                "products" -> projectCatalog(serialized)
                                "employees" -> projectEmployees(serialized)
                                "shifts" -> projectShifts(serialized)
                                "orders" -> projectOrders(serialized)
                                "parked" -> projectParkedOrders(serialized)
                                "receivings", "inventoryHistory" -> projectStockEvents(key, serialized)
                                "webOrderAcceptances" -> projectWebAcceptances(serialized)
                                "currentOrderSession" -> projectCurrentOrderSession(serialized)
                                "webOrderReadyJournal" -> projectWebReadyJournal(serialized)
                                "criticalStorageJournal" -> projectCriticalStorageJournal(serialized)
                            }
                        }
                        writeState.commit(key, command.version!!)
                    }.onSuccess { result(requestId, true, projectionOk = true) }
                        .onFailure { result(requestId, false, "shadow write or projection failed", projectionOk = false) }
                }
            }

            "remove" -> {
                val key = command.key
                if (key.isBlank()) {
                    result(requestId, false, "invalid shadow storage key")
                    return
                }
                run {
                    readAttempt(command.action) {
                        database.withTransaction {
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
                                "receivings", "inventoryHistory" -> database.withTransaction {
                                    stockEventDao.clearLines(key)
                                    stockEventDao.clearEvents(key)
                                }
                                "webOrderAcceptances" -> webAcceptanceDao.clear()
                                "currentOrderSession" -> currentOrderSessionDao.clear()
                                "webOrderReadyJournal" -> webReadyDao.clear()
                                "criticalStorageJournal" -> criticalJournalDao.clear()
                            }
                        }
                        writeState.commit(key, command.version!!)
                    }.onSuccess { result(requestId, true) }
                        .onFailure { result(requestId, false, it.localizedMessage ?: "shadow delete failed") }
                }
            }

            "criticalStorageJournalParity" -> run {
                readAttempt(command.action) {
                    val shadow = shadowDao.get("criticalStorageJournal")?.payload
                    val source = shadow?.takeUnless { it == "null" }?.let(::JSONObject)
                    val native = criticalJournalDao.current()
                    val legacyKeys = mutableSetOf<String>()
                    val writes = source?.optJSONArray("writes") ?: JSONArray()
                    for (index in 0 until writes.length()) {
                        writes.optJSONObject(index)?.optString("key")?.takeIf { it.isNotBlank() }?.let(legacyKeys::add)
                    }
                    val nativeKeys = native?.writeKeys?.let { raw ->
                        val array = JSONArray(raw)
                        buildSet { for (index in 0 until array.length()) array.optString(index).takeIf { it.isNotBlank() }?.let(::add) }
                    } ?: emptySet()
                    val presenceMatches = (source == null) == (native == null)
                    val idMatches = source?.optString("id").orEmpty() == native?.journalId.orEmpty()
                    val typeMatches = source?.optString("type").orEmpty() == native?.operationType.orEmpty()
                    val keysMatch = legacyKeys == nativeKeys
                    JSONObject()
                        .put("requestId", requestId)
                        .put("ok", true)
                        .put("authoritative", false)
                        .put("legacyPresent", source != null)
                        .put("nativePresent", native != null)
                        .put("presenceMatches", presenceMatches)
                        .put("idMatches", idMatches)
                        .put("typeMatches", typeMatches)
                        .put("writeKeysMatch", keysMatch)
                        .put("matches", presenceMatches && idMatches && typeMatches && keysMatch)
                }.onSuccess(::emitResult)
                    .onFailure { result(requestId, false, it.localizedMessage ?: "critical storage journal parity failed") }
            }

            "webAcceptanceParity" -> run {
                readAttempt(command.action) {
                    val shadow = shadowDao.get("webOrderAcceptances")?.payload ?: "{}"
                    val source = JSONObject(shadow)
                    val nativeRows = webAcceptanceDao.all()
                    val nativeById = nativeRows.associateBy { it.webOrderId }
                    val legacyIds = mutableSetOf<String>()
                    val stageMismatches = JSONArray()
                    for (id in source.keys()) {
                        legacyIds += id
                        val legacyStage = source.optJSONObject(id)?.optString("stage").orEmpty()
                        val nativeStage = nativeById[id]?.stage
                        if (nativeStage != legacyStage) stageMismatches.put(id)
                    }
                    val missingNative = JSONArray()
                    legacyIds.filter { it !in nativeById }.sorted().forEach { missingNative.put(it) }
                    val extraNative = JSONArray()
                    nativeById.keys.filter { it !in legacyIds }.sorted().forEach { extraNative.put(it) }
                    JSONObject()
                        .put("requestId", requestId)
                        .put("ok", true)
                        .put("authoritative", false)
                        .put("legacyCount", legacyIds.size)
                        .put("nativeCount", nativeRows.size)
                        .put("missingNativeIds", missingNative)
                        .put("extraNativeIds", extraNative)
                        .put("stageMismatches", stageMismatches)
                        .put("matches", missingNative.length() == 0 && extraNative.length() == 0 && stageMismatches.length() == 0)
                }.onSuccess(::emitResult)
                    .onFailure { result(requestId, false, it.localizedMessage ?: "web acceptance parity failed") }
            }

            "webReadyParity" -> run {
                readAttempt(command.action) {
                    val shadow = shadowDao.get("webOrderReadyJournal")?.payload ?: "{}"
                    val source = JSONObject(shadow)
                    val nativeRows = webReadyDao.all()
                    val nativeById = nativeRows.associateBy { it.webOrderId }
                    val legacyIds = mutableSetOf<String>()
                    val stageMismatches = JSONArray()
                    for (id in source.keys()) {
                        legacyIds += id
                        val legacyStage = source.optJSONObject(id)?.optString("stage").orEmpty()
                        val nativeStage = nativeById[id]?.stage
                        if (nativeStage != legacyStage) stageMismatches.put(id)
                    }
                    val missingNative = JSONArray()
                    legacyIds.filter { it !in nativeById }.sorted().forEach { missingNative.put(it) }
                    val extraNative = JSONArray()
                    nativeById.keys.filter { it !in legacyIds }.sorted().forEach { extraNative.put(it) }
                    JSONObject()
                        .put("requestId", requestId)
                        .put("ok", true)
                        .put("authoritative", false)
                        .put("legacyCount", legacyIds.size)
                        .put("nativeCount", nativeRows.size)
                        .put("missingNativeIds", missingNative)
                        .put("extraNativeIds", extraNative)
                        .put("stageMismatches", stageMismatches)
                        .put("matches", missingNative.length() == 0 && extraNative.length() == 0 && stageMismatches.length() == 0)
                }.onSuccess(::emitResult)
                    .onFailure { result(requestId, false, it.localizedMessage ?: "web ready parity failed") }
            }

            "stockEventParity" -> run {
                val sourceKey = command.sourceKey
                readAttempt(command.action) { stockEventRepository.parityReport(sourceKey) }
                    .onSuccess { report -> report.put("requestId", requestId); emitResult(report) }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "stock event parity failed") }
            }

            "parkedOrderParity" -> run {
                readAttempt(command.action) { parkedRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        emitResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "parked order parity failed") }
            }

            "orderParity" -> run {
                readAttempt(command.action) { orderRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        emitResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "order parity failed") }
            }

            "shiftParity" -> run {
                readAttempt(command.action) { shiftRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        emitResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "shift parity failed") }
            }

            "employeeParity" -> run {
                readAttempt(command.action) { employeeRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        emitResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "employee parity failed") }
            }

            "catalogSnapshot" -> run {
                readAttempt(command.action) { catalogRepository.snapshot() }
                    .onSuccess { snapshot ->
                        snapshot.put("requestId", requestId)
                        emitResult(snapshot)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "catalog snapshot failed") }
            }

            "catalogParity" -> run {
                readAttempt(command.action) { catalogRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        emitResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "catalog parity failed") }
            }

            "stats" -> run {
                readAttempt(command.action) {
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
                        stockEventDao.eventCount(),
                        stockEventDao.lineCount(),
                        webAcceptanceDao.count(),
                        webAcceptanceDao.pendingCount(),
                    )
                }.onSuccess { counts ->
                    emitResult(
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
                            .put("stockEvents", counts[11])
                            .put("stockEventLines", counts[12])
                            .put("webAcceptances", counts[13])
                            .put("pendingWebAcceptances", counts[14])
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
                loyaltySyncStatus = order.optJSONObject("loyaltySync")?.optString("status").orEmpty(),
                loyaltyReversalStatus = order.optJSONObject("loyaltyReversal")?.optString("status").orEmpty(),
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

    private suspend fun projectStockEvents(sourceKey:String, serialized:String) {
        val source=JSONArray(serialized); val now=System.currentTimeMillis()
        val events=ArrayList<StockEventProjectionEntity>(); val lines=ArrayList<StockEventLineProjectionEntity>()
        for(i in 0 until source.length()){
            val event=source.optJSONObject(i)?:continue
            val rawId=event.optString("id").trim()
            val eventId=if(rawId.isNotEmpty()) "$sourceKey:$rawId" else "$sourceKey:event:$i"
            val inventory=sourceKey=="inventoryHistory"
            events += StockEventProjectionEntity(
                id=eventId, sourceKey=sourceKey, eventType=if(inventory) event.optString("type","inventory") else event.optString("type","receiving"),
                supplierId=event.optString("supplierId"), supplierName=event.optString("supplierName"),
                referenceId=if(inventory) event.optString("id") else event.optString("purchaseOrderId"),
                totalCost=if(inventory) event.optDouble("estimatedLoss") else event.optDouble("totalCost"),
                timestamp=if(inventory) event.optLong("completedAt") else event.optLong("timestamp"),
                sortIndex=i,payload=event.toString(),updatedAt=now)
            val items=event.optJSONArray("items")?:JSONArray()
            for(j in 0 until items.length()){
                val item=items.optJSONObject(j)?:continue
                lines += StockEventLineProjectionEntity(
                    id="$eventId:line:$j", eventId=eventId, productId=item.optString("productId"),
                    productName=item.optString("productName",item.optString("name")),
                    quantity=if(inventory) item.optDouble("actual") else item.optDouble("qty"),
                    unitCost=if(inventory) item.optDouble("cost") else item.optDouble("unitCost"),
                    difference=if(inventory) item.optDouble("difference") else item.optDouble("qty"),
                    stockUnit=if(inventory) item.optString("unit") else item.optString("stockUnit"),
                    sortIndex=j,payload=item.toString(),updatedAt=now)
            }
        }
        database.withTransaction {
            stockEventDao.clearLines(sourceKey); stockEventDao.clearEvents(sourceKey)
            if(events.isNotEmpty()) stockEventDao.insertEvents(events)
            if(lines.isNotEmpty()) stockEventDao.insertLines(lines)
        }
    }

    private suspend fun projectCurrentOrderSession(serialized: String) {
        val source = JSONObject(serialized)
        val items = source.optJSONArray("items")
        currentOrderSessionDao.upsert(
            CurrentOrderSessionProjectionEntity(
                itemCount = items?.length() ?: 0,
                orderType = source.optString("orderType"),
                source = source.optString("source"),
                webOrderId = source.optString("webOrderId"),
                webOrderStatus = source.optString("webOrderStatus"),
                updatedAt = source.optLong("updatedAt"),
                payload = source.toString(),
            ),
        )
    }

    private suspend fun projectWebAcceptances(serialized:String) {
        val source=JSONObject(serialized); val now=System.currentTimeMillis(); val rows=ArrayList<WebAcceptanceProjectionEntity>()
        for(webOrderId in source.keys()){
            val record=source.optJSONObject(webOrderId)?:continue
            val parked=record.optJSONObject("parked")?:JSONObject()
            rows += WebAcceptanceProjectionEntity(
                webOrderId=webOrderId, stage=record.optString("stage"), readyEstimate=record.optString("readyEstimate"),
                parkedOrderId=parked.optString("id"), preparedAt=record.optLong("preparedAt"), confirmedAt=record.optLong("confirmedAt"),
                payload=record.toString(), updatedAt=now)
        }
        database.withTransaction { webAcceptanceDao.clear(); if(rows.isNotEmpty()) webAcceptanceDao.insertAll(rows) }
    }

    private suspend fun projectWebReadyJournal(serialized: String) {
        val source = JSONObject(serialized)
        val now = System.currentTimeMillis()
        val rows = mutableListOf<WebReadyProjectionEntity>()
        for (id in source.keys()) {
            val record = source.optJSONObject(id) ?: continue
            rows += WebReadyProjectionEntity(
                webOrderId = id,
                stage = record.optString("stage"),
                createdAt = record.optLong("createdAt"),
                confirmedAt = record.optLong("confirmedAt"),
                payload = record.toString(),
                updatedAt = now,
            )
        }
        database.withTransaction {
            webReadyDao.clear()
            if (rows.isNotEmpty()) webReadyDao.insertAll(rows)
        }
    }

    private suspend fun projectCriticalStorageJournal(serialized: String) {
        val journal = if (serialized == "null") null else JSONObject(serialized)
        if (journal == null) {
            criticalJournalDao.clear()
            return
        }
        val writes = journal.optJSONArray("writes") ?: JSONArray()
        val keys = mutableListOf<String>()
        for (index in 0 until writes.length()) {
            val key = writes.optJSONObject(index)?.optString("key").orEmpty()
            if (key.isNotBlank()) keys += key
        }
        criticalJournalDao.replace(
            CriticalStorageJournalProjectionEntity(
                journalId = journal.optString("id"),
                operationType = journal.optString("type"),
                createdAt = journal.optLong("createdAt"),
                writeKeys = JSONArray(keys).toString(),
                payload = journal.toString(),
                updatedAt = System.currentTimeMillis(),
            )
        )
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
        emitResult(result)
    }
}
