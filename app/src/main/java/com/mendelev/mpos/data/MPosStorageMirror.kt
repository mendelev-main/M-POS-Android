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
        val key = if (action.startsWith("catalog") && action in setOf("catalogInitialize", "catalogWrite", "catalogRemove")) "products" else if (action in setOf("employeeInitialize", "employeeWrite", "employeeRemove")) "employees" else if (action in setOf("shiftInitialize", "shiftWrite", "shiftRemove")) "shifts" else payload.optString("key")
        val command = Command(action, payload.optString("requestId"), key, payload.opt("payload") as? String, payload.optString("sourceKey"),
            if (key.isNotBlank() && action in setOf("put", "remove", "catalogInitialize", "catalogWrite", "catalogRemove", "workspaceInitialize", "workspaceWrite", "workspaceRemove", "employeeInitialize", "employeeWrite", "employeeRemove", "shiftInitialize", "shiftWrite", "shiftRemove")) writeState.request(key) else null)
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
    private val workspaceStorage = MPosWorkspaceStorage(database)
    private val catalogStorage = MPosCatalogStorage(database)
    private val employeeStorage = MPosEmployeeStorage(database)
    private val shiftStorage = MPosShiftStorage(database)
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
        if ((command.key == "products" || command.key == "employees" || command.key == "shifts" || command.key in MPosWorkspaceStorage.KEYS) && command.action in setOf("put", "remove")) {
            val owned = attempt { if (command.key == "products") catalogStorage.isAuthoritative() else if (command.key == "employees") employeeStorage.isAuthoritative() else if (command.key == "shifts") shiftStorage.isAuthoritative() else workspaceStorage.isAuthoritative(command.key) }
            if (owned.isFailure) { result(requestId, false, "native catalog ownership check failed"); return }
            if (owned.getOrThrow()) {
                command.version?.let { writeState.commit(command.key, it) }
                emitResult(JSONObject().put("requestId", requestId).put("ok", true).put("authoritative", true).put("ignored", true))
                return
            }
        }
        when (command.action) {
            "workspaceStatus", "workspaceInitialize", "workspaceRead", "workspaceWrite", "workspaceRemove" -> {
                attempt {
                    val key = command.key
                    val value = when (command.action) {
                        "workspaceStatus" -> JSONObject().put("ok", true).put("initialized", workspaceStorage.isAuthoritative(key)).put("source", "room-workspace").put("key", key)
                        "workspaceInitialize" -> workspaceStorage.initialize(key, command.serialized)
                        "workspaceWrite" -> workspaceStorage.write(key, requireNotNull(command.serialized))
                        "workspaceRemove" -> workspaceStorage.remove(key)
                        else -> workspaceStorage.read(key)
                    }
                    command.version?.let { writeState.commit(key, it) }
                    value.put("requestId", requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native workspace operation failed") }
            }
            "catalogStatus", "catalogInitialize", "catalogRead", "catalogWrite", "catalogRemove" -> {
                attempt {
                    val value = when (command.action) {
                        "catalogStatus" -> JSONObject().put("ok", true).put("initialized", catalogStorage.isAuthoritative()).put("source", "room-catalog")
                        "catalogInitialize" -> catalogStorage.initialize(command.serialized)
                        "catalogWrite" -> catalogStorage.write(requireNotNull(command.serialized))
                        "catalogRemove" -> catalogStorage.remove()
                        else -> catalogStorage.read()
                    }
                    command.version?.let { writeState.commit("products", it) }
                    value.put("requestId", requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native catalog operation failed") }
            }
            "employeeStatus", "employeeInitialize", "employeeRead", "employeeWrite", "employeeRemove" -> {
                attempt {
                    val value = when (command.action) {
                        "employeeStatus" -> JSONObject().put("ok", true).put("initialized", employeeStorage.isAuthoritative()).put("source", "room-employees")
                        "employeeInitialize" -> employeeStorage.initialize(command.serialized)
                        "employeeWrite" -> employeeStorage.write(requireNotNull(command.serialized))
                        "employeeRemove" -> employeeStorage.remove()
                        else -> employeeStorage.read()
                    }
                    command.version?.let { writeState.commit("employees", it) }
                    value.put("requestId", requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native employee operation failed") }
            }
            "shiftStatus", "shiftInitialize", "shiftRead", "shiftWrite", "shiftRemove" -> {
                attempt {
                    val value = when (command.action) {
                        "shiftStatus" -> JSONObject().put("ok", true).put("initialized", shiftStorage.isAuthoritative()).put("source", "room-shifts")
                        "shiftInitialize" -> shiftStorage.initialize(command.serialized)
                        "shiftWrite" -> shiftStorage.write(requireNotNull(command.serialized))
                        "shiftRemove" -> shiftStorage.remove()
                        else -> shiftStorage.read()
                    }
                    command.version?.let { writeState.commit("shifts", it) }
                    value.put("requestId", requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native shift operation failed") }
            }
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
                                "employees" -> employeeStorage.project(serialized)
                                "shifts" -> shiftStorage.project(serialized)
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

    private suspend fun projectCatalog(serialized: String) = catalogStorage.project(serialized)

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
