package com.mendelev.mpos.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import org.json.JSONObject

class MPosStorageMirror(
    private val database: MPosDatabase,
    scope: CoroutineScope,
    private val rootBootstrap: suspend () -> JSONObject,
    private val onResult: (JSONObject) -> Unit,
) {
    constructor(database: MPosDatabase, scope: CoroutineScope, onResult: (JSONObject) -> Unit) :
        this(database, scope, { MPosRootSessionRepository(database).read().bootstrap() }, onResult)
    private val queue = MPosStorageQueue(scope)
    private val writeState = MPosShadowWriteState()

    private data class Command(val action: String, val requestId: String, val key: String, val serialized: String?, val sourceKey: String, val version: Long?)

    fun handle(payload: JSONObject) {
        val action = payload.optString("action")
        val key = if (action.startsWith("catalog") && action in setOf("catalogInitialize", "catalogWrite", "catalogRemove")) "products" else if (action in setOf("employeeInitialize", "employeeWrite", "employeeRemove")) "employees" else if (action in setOf("shiftInitialize", "shiftWrite", "shiftRemove")) "shifts" else if (action in setOf("orderInitialize", "orderWrite", "orderRemove", "orderUpsert")) "orders" else if (action in setOf("parkedInitialize", "parkedWrite", "parkedRemove")) "parked" else payload.optString("key")
        val command = Command(action, payload.optString("requestId"), key, payload.opt("payload") as? String, payload.optString("sourceKey"),
            if (key.isNotBlank() && action in setOf("put", "remove", "catalogInitialize", "catalogWrite", "catalogRemove", "workspaceInitialize", "workspaceWrite", "workspaceRemove", "employeeInitialize", "employeeWrite", "employeeRemove", "shiftInitialize", "shiftWrite", "shiftRemove", "orderInitialize", "orderWrite", "orderRemove", "orderUpsert", "parkedInitialize", "parkedWrite", "parkedRemove", "recoveryInitialize", "recoveryWrite", "recoveryRemove", "webJournalInitialize", "webJournalWrite", "webJournalRemove", "supplyInitialize", "supplyWrite", "supplyRemove", "inventoryInitialize", "inventoryWrite", "inventoryRemove", "hallInitialize", "hallWrite", "hallRemove")) writeState.request(key) else null)
        if (!queue.submit({ result(command.requestId, false, "native shadow command failed") }) { withRootResult(command.requestId, command.action, command.key) { dispatch(command) } }) {
            result(command.requestId, false, "native shadow queue is full or closed")
        }
    }

    fun navigateWorkspaceToolbar(input:JSONObject,reply:(JSONObject)->Unit) {
        val serialized=input.toString()
        fun failure(error:Throwable) {reply(JSONObject().put("ok",false).put("message","Панель изменилась. Повторите действие"))}
        if(!queue.submit(::failure) {
            attempt {com.mendelev.mpos.workspace.MPosWorkspaceNavigationRepository(database,workspaceNavigation).navigateToolbar(JSONObject(serialized))}
                .onSuccess(reply).onFailure(::failure)
        })failure(IllegalStateException("native queue unavailable"))
    }

    fun close() = queue.close()

    /** Called only by the native opening dialog; credentials never enter the WebView router. */
    fun openShift(input: JSONObject, credential: String) {
        val snapshot = input.toString()
        val requestId = input.getString("requestId")
        if (!queue.submit({ emitResult(MPosShiftOpenCommand.failure(it).put("requestId", requestId).put("blocked", true)) }) {
            withRootResult(requestId, "shiftOpen") {
                attempt { MPosShiftOpenCommand(database).commit(JSONObject(snapshot), credential).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { emitResult(MPosShiftOpenCommand.failure(it).put("requestId", requestId)) }
            }
        }) emitResult(JSONObject().put("requestId", requestId).put("ok", false).put("blocked", false)
            .put("message", "Сохранение ещё не началось. Повторите открытие смены."))
    }

    /** Native employee dialog owns credentials; they never enter bridge input or persistent documents. */
    fun commitEmployee(input: JSONObject, credential: String) {
        val serialized = input.toString()
        val requestId = input.getString("requestId")
        if (!queue.submit({ emitEmployeeFailure(requestId, it) }) {
            withRootResult(requestId, "employeeCommit") {
                attempt { MPosEmployeeCommand(database).commit(serialized, credential).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { emitEmployeeFailure(requestId, it) }
            }
        }) emitEmployeeFailure(requestId, IllegalStateException("native queue unavailable"))
    }

    fun prepareLoyaltyAdjustment(input: JSONObject, ready: (JSONObject) -> Unit, failed: (String) -> Unit) {
        val serialized = input.toString()
        fun failure(error: Throwable) = failed(if (error.message in setOf("Требуются права администратора", "Перед повторной корректировкой проверьте актуальный баланс клиента")) error.message!! else "Не удалось подготовить корректировку")
        if (!queue.submit(::failure) {
            attempt { MPosLoyaltyAdjustmentPreparation(database).prepare(serialized) }.onSuccess(ready).onFailure(::failure)
        }) failed("Не удалось подготовить корректировку")
    }

    private suspend fun loyaltyVerificationConfiguration():JSONObject = MPosLoyaltyBalanceVerification(database).context("",requireAdmin=false)

    private val workspaceNavigation = com.mendelev.mpos.workspace.MPosWorkspaceNavigationOwner()
    private val editorGrants = MPosEditorGrants()

    fun authorizeEditor(input: JSONObject, credential: String) {
        val serialized = input.toString()
        val requestId = input.getString("requestId")
        fun failure(error: Throwable) {
            val known = setOf("Неверный пароль администратора", "Перезапустите M POS для восстановления данных", "Товар изменился. Откройте карточку заново")
            emitResult(JSONObject().put("requestId", requestId).put("ok", false)
                .put("message", error.message.takeIf { it in known } ?: "Не удалось проверить разрешение редактора")
                .put("credentialRejected", error.message == "Неверный пароль администратора"))
        }
        if (!queue.submit(::failure) {
            attempt { MPosEditorAuthorization(database, editorGrants).authorize(serialized, credential).put("requestId", requestId) }
                .onSuccess(::emitResult).onFailure(::failure)
        }) failure(IllegalStateException("native queue unavailable"))
    }

    fun commitCatalogDelete(input: JSONObject, credential: String) {
        val serialized = input.toString()
        val requestId = input.getString("requestId")
        if (!queue.submit({ emitCatalogDeleteFailure(requestId, it) }) {
            attempt { MPosCatalogDeleteCommand(database).commit(serialized, credential).put("requestId", requestId) }
                .onSuccess(::emitResult).onFailure { emitCatalogDeleteFailure(requestId, it) }
        }) emitCatalogDeleteFailure(requestId, IllegalStateException("native queue unavailable"))
    }

    private fun emitCatalogDeleteFailure(requestId: String, error: Throwable) {
        val known = setOf("Неверный пароль администратора", "Перезапустите M POS для восстановления данных",
            "Каталог изменился. Откройте подтверждение заново", "Товар нужен для возврата ранее проданных чеков",
            "Товар используется в составном товаре", "Нельзя удалить категорию: в ней есть товары")
        emitResult(JSONObject().put("requestId", requestId).put("ok", false)
            .put("message", if (error is MPosCatalogDeleteCommand.ReferencedProduct) "Товар используется в составном товаре «${error.productName}»" else error.message.takeIf { it in known } ?: "Не удалось удалить элемент каталога")
            .put("credentialRejected", error.message == "Неверный пароль администратора"))
    }

    private fun emitEmployeeFailure(requestId: String, error: Throwable) {
        val known = setOf("Неверный пароль администратора", "Перезапустите M POS для восстановления данных", "Список сотрудников изменился",
            "Смена изменилась", "Сотрудник не найден", "Нельзя удалить самого себя", "Нельзя удалить администратора", "Для удаления сотрудника откройте смену",
            "Введите ФИО сотрудника", "Изменение сотрудников не совпадает с командой")
        val message = error.message.takeIf { it in known } ?: "Не удалось сохранить сотрудника"
        emitResult(JSONObject().put("requestId", requestId).put("ok", false).put("message", message)
            .put("credentialRejected", error.message == "Неверный пароль администратора"))
    }

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

    private val startup = MPosRootStartup()
    private var startupRoot: String? = null
    private val resultMonitor = Any()
    private var collectingId: String? = null
    private val collected = mutableListOf<JSONObject>()
    private var rootSequence = 0L

    /** Deliver committed root state before the matching command acknowledgement, never from the observer cache. */
    private suspend fun withRootResult(id: String, action: String, key: String = "", block: suspend () -> Unit) {
        synchronized(resultMonitor) { collectingId = id }
        try {
            block()
        } finally {
            val values = synchronized(resultMonitor) {
                collectingId = null
                collected.toList().also { collected.clear() }
            }
            // Reads do not invalidate the root. These are all bridges capable of changing its documents.
            val changesRoot = action in setOf("adminAccess", "rootSessionRefresh", "shiftOpen", "shiftLifecycleCommit", "cashMovementCommit", "returnCommit", "employeeCommit",
                "shiftInitialize", "shiftWrite", "shiftRemove", "employeeInitialize", "employeeWrite", "employeeRemove") ||
                action in setOf("recoveryWrite", "recoveryRemove", "recoveryInitialize", "put", "remove") && key in setOf("shifts", "employees", "criticalStorageJournal")
            val root = if (changesRoot) attempt { rootSelection(rootBootstrap()) }.getOrNull() else null
            val sequence = if (changesRoot) ++rootSequence else rootSequence
            for (value in values) {
                if (changesRoot) value.put("rootSession", root ?: JSONObject.NULL).put("rootSequence", sequence)
                deliverResult(value)
            }
        }
    }

    /** Mutation replies carry selected records only, rather than retransmitting the shift/employee history. */
    private fun rootSelection(root: JSONObject): JSONObject {
        fun selected(records: String, index: String): Any = if (root.isNull(index)) JSONObject.NULL
            else root.getJSONArray(records).getJSONObject(root.getInt(index))
        return JSONObject().put("currentShift", selected("shifts", "activeShiftIndex"))
            .put("selectedEmployee", selected("employees", "activeEmployeeIndex"))
            .put("isAdmin", root.getBoolean("isAdmin")).put("recoveryPending", root.getBoolean("recoveryPending"))
    }

    private fun emitResult(value: JSONObject) {
        synchronized(resultMonitor) {
            if (collectingId != null && collectingId == value.optString("requestId")) {
                collected.add(value)
                return
            }
        }
        deliverResult(value)
    }

    private fun deliverResult(value: JSONObject) {
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
    private val inventoryStorage = MPosInventoryStorage(database)
    private val hallStorage = MPosHallStorage(database)
    private val supplyStorage = MPosSupplyStorage(database)
    private val webJournalStorage = MPosWebJournalStorage(database)
    private val recoveryStorage = MPosRecoveryStorage(database)
    private val catalogStorage = MPosCatalogStorage(database)
    private val employeeStorage = MPosEmployeeStorage(database)
    private val shiftStorage = MPosShiftStorage(database)
    private val orderStorage = MPosOrderStorage(database)
    private val parkedStorage = MPosParkedOrderStorage(database)
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
        if ((command.key == "products" || command.key == "employees" || command.key == "shifts" || command.key == "orders" || command.key == "parked" || command.key in MPosWorkspaceStorage.KEYS || command.key in MPosRecoveryStorage.KEYS || command.key in MPosWebJournalStorage.KEYS || command.key in MPosSupplyStorage.KEYS || command.key in MPosInventoryStorage.KEYS || command.key in MPosHallStorage.KEYS) && command.action in setOf("put", "remove")) {
            val owned = attempt { if (command.key == "products") catalogStorage.isAuthoritative() else if (command.key == "employees") employeeStorage.isAuthoritative() else if (command.key == "shifts") shiftStorage.isAuthoritative() else if (command.key == "orders") orderStorage.isAuthoritative() else if (command.key == "parked") parkedStorage.isAuthoritative() else if (command.key in MPosRecoveryStorage.KEYS) recoveryStorage.isAuthoritative(command.key) else if(command.key in MPosHallStorage.KEYS) hallStorage.isAuthoritative(command.key) else if(command.key in MPosInventoryStorage.KEYS) inventoryStorage.isAuthoritative(command.key) else if(command.key in MPosSupplyStorage.KEYS) supplyStorage.isAuthoritative(command.key) else if(command.key in MPosWebJournalStorage.KEYS) webJournalStorage.isAuthoritative(command.key) else workspaceStorage.isAuthoritative(command.key) }
            if (owned.isFailure) { result(requestId, false, "native catalog ownership check failed"); return }
            if (owned.getOrThrow()) {
                command.version?.let { writeState.commit(command.key, it) }
                emitResult(JSONObject().put("requestId", requestId).put("ok", true).put("authoritative", true).put("ignored", true))
                return
            }
        }
        when (command.action) {
            "rootSessionRefresh" -> emitResult(JSONObject().put("requestId", requestId).put("ok", true))
            "rootStartup" -> {
                attempt {
                    val input = JSONObject(requireNotNull(command.serialized))
                    if (input.optString("operation") == "advance" && input.optString("completed") == "hydrate") {
                        check(startupRoot != null && startupRoot == rootBootstrap().toString()) { "root changed during startup" }
                    }
                    val next = startup.execute(input)
                    when (next.getString("step")) {
                        "recover" -> { startupRoot = null;workspaceNavigation.beginRuntime(next.getLong("generation")) }
                        "hydrate" -> {
                            val root = rootBootstrap()
                            startupRoot = root.toString()
                            next.put("rootSession", root)
                        }
                    }
                    next.put("requestId", requestId).put("ok", true).put("authoritative", true)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native root startup failed") }
            }
            "shiftOpenFormRead" -> {
                readAttempt(command.action) { MPosShiftOpeningRepository(database).read().put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native shift opening form unavailable") }
            }
            "shiftCloseFormRead" -> {
                readAttempt(command.action) { MPosShiftReportRepository(database).readCloseForm(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native shift closing form unavailable") }
            }
            "shiftScreenRead" -> {
                readAttempt(command.action) { MPosShiftReportRepository(database).readScreen(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native shift screen unavailable") }
            }
            "shiftReportRead" -> {
                readAttempt(command.action) { MPosShiftReportRepository(database).read(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native shift report unavailable") }
            }
            "shiftLifecycleCommit" -> {
                attempt { MPosShiftLifecycleCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, MPosShiftLifecycleCommand.failureMessage(it)) }
            }
            "cashMovementCommit" -> {
                attempt { MPosCashMovementCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "local cash movement transaction failed") }
            }
            "returnCommit" -> {
                attempt { MPosReturnCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "local return transaction failed") }
            }
            "cartQuantityRead" -> {
                readAttempt(command.action) { MPosCartQuantityRepository(database).read(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native cart quantity unavailable") }
            }
            "stockPreflightRead" -> {
                readAttempt(command.action) { MPosStockPreflightRepository(database).read(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native stock preflight unavailable") }
            }
            "webJournalCommand" -> {
                attempt { MPosWebJournalCommand(database).execute(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"local WEB journal command failed") }
            }
            "webJournalStatus", "webJournalInitialize", "webJournalRead", "webJournalWrite", "webJournalRemove" -> {
                attempt {
                    val key=command.key
                    val value=when(command.action){
                        "webJournalStatus" -> JSONObject().put("ok",true).put("initialized",webJournalStorage.isAuthoritative(key))
                        "webJournalInitialize" -> webJournalStorage.initialize(key,command.serialized)
                        "webJournalWrite" -> webJournalStorage.write(key,requireNotNull(command.serialized))
                        "webJournalRemove" -> webJournalStorage.remove(key)
                        else -> webJournalStorage.read(key)
                    }
                    command.version?.let { writeState.commit(key,it) };value.put("requestId",requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId,false,"native WEB journal operation failed") }
            }
            "availabilityConsume" -> {
                attempt { val input=JSONObject(requireNotNull(command.serialized));MPosAvailabilityJournal(database).consume(input.getString("token"),input.getJSONObject("body")).put("ok",true).put("authoritative",true).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"native availability permit unavailable") }
            }
            "availabilityPrepare" -> {
                attempt { MPosAvailabilityJournal(database).prepare(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"native payment availability unavailable") }
            }
            "loyaltyJournal" -> {
                attempt { MPosLoyaltyJournal(database).execute(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "local loyalty journal transaction failed") }
            }
            "loyaltyEligibilityRead" -> {
                attempt { MPosLoyaltyEligibilityEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native loyalty eligibility unavailable") }
            }
            "customerContextRead" -> {
                attempt { MPosCustomerEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native customer context unavailable") }
            }
            "analyticsRead" -> {
                attempt { MPosAnalyticsRepository(database).read(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"native sales analytics unavailable") }
            }
            "warehouseRead" -> {
                attempt { MPosWarehouseRepository(database).read(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"native warehouse report unavailable") }
            }
            "hallCommit" -> {
                attempt { MPosHallCommand(database).commit(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,if(it is IllegalArgumentException || it is IllegalStateException) it.message?:"local hall transaction failed" else "local hall transaction failed") }
            }
            "hallStatus", "hallInitialize", "hallRead", "hallWrite", "hallRemove" -> {
                attempt {
                    val value=when(command.action){
                        "hallStatus"->JSONObject().put("ok",true).put("initialized",hallStorage.isAuthoritative(command.key))
                        "hallInitialize"->hallStorage.initialize(command.key,command.serialized)
                        "hallWrite"->hallStorage.write(command.key,requireNotNull(command.serialized))
                        "hallRemove"->hallStorage.remove(command.key)
                        else->hallStorage.read(command.key)
                    };command.version?.let{writeState.commit(command.key,it)};value.put("requestId",requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId,false,"native hall storage operation failed") }
            }
            "inventoryCommit" -> {
                attempt { MPosInventoryCommand(database).commit(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"local inventory transaction failed") }
            }
            "inventoryStatus", "inventoryInitialize", "inventoryRead", "inventoryWrite", "inventoryRemove" -> {
                attempt {
                    val value=when(command.action){
                        "inventoryStatus"->JSONObject().put("ok",true).put("initialized",inventoryStorage.isAuthoritative(command.key))
                        "inventoryInitialize"->inventoryStorage.initialize(command.key,command.serialized)
                        "inventoryWrite"->inventoryStorage.write(command.key,requireNotNull(command.serialized))
                        "inventoryRemove"->inventoryStorage.remove(command.key)
                        else->inventoryStorage.read(command.key)
                    };command.version?.let{writeState.commit(command.key,it)};value.put("requestId",requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId,false,"native inventory storage operation failed") }
            }
            "receivingDraftCommand" -> {
                attempt { MPosReceivingDraftCommand(database).execute(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"local receiving draft operation failed") }
            }
            "receivingCommit" -> {
                attempt { MPosReceivingCommand(database).commit(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"local receiving transaction failed") }
            }
            "purchaseCommit" -> {
                attempt { MPosPurchaseCommand(database).commit(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"local purchase transaction failed") }
            }
            "supplierCommit" -> {
                attempt { MPosSupplierCommand(database).commit(requireNotNull(command.serialized)).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"local supplier transaction failed") }
            }
            "supplyStatus", "supplyInitialize", "supplyRead", "supplyWrite", "supplyRemove" -> {
                attempt {
                    val value=when(command.action){
                        "supplyStatus"->JSONObject().put("ok",true).put("initialized",supplyStorage.isAuthoritative(command.key))
                        "supplyInitialize"->supplyStorage.initialize(command.key,command.serialized)
                        "supplyWrite"->supplyStorage.write(command.key,requireNotNull(command.serialized))
                        "supplyRemove"->supplyStorage.remove(command.key)
                        else->supplyStorage.read(command.key)
                    }
                    command.version?.let{writeState.commit(command.key,it)};value.put("requestId",requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId,false,"native supply storage operation failed") }
            }
            "workspaceNavigation" -> {
                attempt { com.mendelev.mpos.workspace.MPosWorkspaceNavigationRepository(database,workspaceNavigation).execute(JSONObject(requireNotNull(command.serialized))).put("requestId",requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId,false,"Не удалось переключить раздел") }
            }
            "loyaltyAdjustmentStatus" -> {
                attempt {
                    val input=JSONObject(requireNotNull(command.serialized))
                    val config=loyaltyVerificationConfiguration()
                    val token=MPosLoyaltyAdjustmentVerification(database).ticket(config,input.getString("customerId"),input.get("programId"))
                    JSONObject().put("ok",true).put("authoritative",true).put("required",token!=null).put("requestId",requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId,false,"Не удалось проверить состояние корректировки") }
            }
            "adminAccess" -> {
                attempt {
                    val root = MPosRootSessionRepository(database).read()
                    JSONObject().put("ok",true).put("authoritative",true).put("allowed",root.isAdmin).put("requestId",requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId,false,"Не удалось проверить права администратора") }
            }
            "adminSettingsCommit" -> {
                attempt { MPosAdminSettingsCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure {
                        val known = setOf("Сетевые конфигурации доступны только администратору", "Настройки изменились. Откройте форму заново",
                            "Адрес backend должен начинаться с https://", "Укажите корректный ID рабочего устройства", "Укажите корректный ID владельца")
                        result(requestId, false, it.message.takeIf { value -> value in known } ?: "Не удалось сохранить настройки")
                    }
            }
            "productWebCommit" -> {
                attempt { MPosProductWebCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure {
                        val known = setOf("Настройку WEB может изменять только администратор при открытой им смене",
                            "Перезапустите M POS для восстановления данных", "Каталог изменился. Повторите действие")
                        result(requestId, false, it.message.takeIf { value -> value in known } ?: "Не удалось сохранить настройку WEB")
                    }
            }
            "productEditorCommit" -> {
                attempt { MPosProductEditorCommand(database, editorGrants).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure {
                        val message = it.message.takeIf { value -> value != null && value in setOf(
                            "Перезапустите M POS для восстановления данных", "Товар изменился во время сохранения. Откройте карточку заново",
                            "Изменение учёта остатков требует разрешения администратора", "Изменение остатка требует разрешения администратора",
                            "Настройку WEB может изменять только администратор при открытой им смене", "Введите название", "Добавьте хотя бы один товар в состав",
                            "Нельзя изменить тип: товар нужен для возврата ранее проданных чеков", "Тип товара с единицами или связями нельзя менять: создайте отдельный товар")
                        } ?: "Не удалось сохранить товар"
                        result(requestId, false, message)
                    }
            }
            "companyCommit" -> {
                attempt { MPosCompanyCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure {
                        val message = it.message.takeIf { value -> value in setOf("Реквизиты изменились. Откройте форму заново",
                            "Изменять реквизиты может только администратор при открытой им смене") } ?: "Не удалось сохранить реквизиты"
                        result(requestId, false, message)
                    }
            }
            "employeeCommit" -> {
                attempt { MPosEmployeeCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "local employee transaction failed") }
            }
            "navigationRead" -> {
                attempt { MPosNavigationEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native navigation unavailable") }
            }
            "workspaceRouteRead" -> {
                attempt { com.mendelev.mpos.workspace.MPosWorkspaceRouteEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native workspace transition unavailable") }
            }
            "recipeEditRead" -> {
                attempt { MPosRecipeEditRepository(database).calculate(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native recipe edit unavailable") }
            }
            "catalogEditRead" -> {
                attempt { MPosCatalogEditRepository(database).calculate(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native catalog edit unavailable") }
            }
            "parkedCommit" -> {
                attempt { MPosParkedCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "local parked transaction failed") }
            }
            "orderContextRead" -> {
                attempt { com.mendelev.mpos.payment.MPosOrderContextEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native order context unavailable") }
            }
            "deliveryRead" -> {
                attempt { com.mendelev.mpos.payment.MPosDeliveryEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native delivery unavailable") }
            }
            "sessionRestoreRead" -> {
                attempt { MPosSessionRestoreRepository(database).prepare(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native session restore unavailable") }
            }
            "activeSessionBootstrap" -> {
                attempt { MPosActiveSessionRepository(database).bootstrap().put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native active session unavailable") }
            }
            "rootSessionBootstrap" -> {
                attempt { rootBootstrap().put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native root session unavailable") }
            }
            "splitRecoveryRead" -> {
                attempt { com.mendelev.mpos.payment.MPosSplitRecoveryEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native split recovery unavailable") }
            }
            "splitAmountRead" -> {
                attempt { com.mendelev.mpos.payment.MPosSplitAmountEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native split amount unavailable") }
            }
            "splitCountRead" -> {
                attempt { com.mendelev.mpos.payment.MPosSplitCountEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native split count unavailable") }
            }
            "cartTotalsRead" -> {
                attempt { MPosCartTotalsEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "native cart totals unavailable") }
            }
            "configuredPriceRead" -> {
                attempt { MPosConfiguredPriceEngine.calculate(JSONObject(requireNotNull(command.serialized))).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "configured price unavailable") }
            }
            "paymentCommit" -> {
                attempt { MPosPaymentCommand(database).commit(requireNotNull(command.serialized)).put("requestId", requestId) }
                    .onSuccess(::emitResult).onFailure { result(requestId, false, "local payment transaction failed") }
            }
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
            "recoveryStatus", "recoveryInitialize", "recoveryRead", "recoveryWrite", "recoveryRemove" -> {
                attempt {
                    val key = command.key
                    val value = when (command.action) {
                        "recoveryStatus" -> JSONObject().put("ok", true).put("initialized", recoveryStorage.isAuthoritative(key)).put("source", "room-recovery").put("key", key)
                        "recoveryInitialize" -> recoveryStorage.initialize(key, command.serialized)
                        "recoveryWrite" -> recoveryStorage.write(key, requireNotNull(command.serialized))
                        "recoveryRemove" -> recoveryStorage.remove(key)
                        else -> recoveryStorage.read(key)
                    }
                    command.version?.let { writeState.commit(key, it) }
                    value.put("requestId", requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native recovery operation failed") }
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
            "orderStatus", "orderInitialize", "orderRead", "orderWrite", "orderRemove", "orderPage", "orderUpsert" -> {
                attempt {
                    val value = when (command.action) {
                        "orderStatus" -> JSONObject().put("ok", true).put("initialized", orderStorage.isAuthoritative()).put("source", "room-orders")
                        "orderInitialize" -> orderStorage.initialize(command.serialized)
                        "orderWrite" -> orderStorage.write(requireNotNull(command.serialized))
                        "orderRemove" -> orderStorage.remove()
                        "orderPage" -> orderStorage.page(requireNotNull(command.serialized))
                        "orderUpsert" -> orderStorage.upsert(requireNotNull(command.serialized))
                        else -> orderStorage.read()
                    }
                    command.version?.let { writeState.commit("orders", it) }
                    value.put("requestId", requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native order operation failed") }
            }
            "parkedStatus", "parkedInitialize", "parkedRead", "parkedWrite", "parkedRemove" -> {
                attempt {
                    val value = when (command.action) {
                        "parkedStatus" -> JSONObject().put("ok", true).put("initialized", parkedStorage.isAuthoritative()).put("source", "room-parked")
                        "parkedInitialize" -> parkedStorage.initialize(command.serialized)
                        "parkedWrite" -> parkedStorage.write(requireNotNull(command.serialized))
                        "parkedRemove" -> parkedStorage.remove()
                        else -> parkedStorage.read()
                    }
                    command.version?.let { writeState.commit("parked", it) }
                    value.put("requestId", requestId)
                }.onSuccess(::emitResult).onFailure { result(requestId, false, "native parked operation failed") }
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
                                "orders" -> orderStorage.project(serialized)
                                "parked" -> parkedStorage.project(serialized)
                                "receivings", "inventoryHistory" -> projectStockEvents(key, serialized)
                                "webOrderAcceptances" -> projectWebAcceptances(serialized)
                                "currentOrderSession" -> recoveryStorage.project(key, serialized)
                                "webOrderReadyJournal" -> projectWebReadyJournal(serialized)
                                "criticalStorageJournal" -> recoveryStorage.project(key, serialized)
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

    private suspend fun projectStockEvents(sourceKey:String,serialized:String)=stockEventRepository.project(sourceKey,serialized)

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
