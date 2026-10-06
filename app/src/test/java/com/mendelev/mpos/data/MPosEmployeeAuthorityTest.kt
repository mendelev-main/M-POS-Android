package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosEmployeeAuthorityTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "employee-authority-${UUID.randomUUID()}.db"

    @Before fun open() {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mirror = MPosStorageMirror(database, scope) { replies.add(it) }
    }
    @After fun close() { mirror.close(); scope.cancel(); database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun send(action: String, id: String = action, payload: String? = null, key: String? = null) {
        mirror.handle(JSONObject().put("action", action).put("requestId", id).also {
            if (payload != null) it.put("payload", payload)
            if (key != null) it.put("key", key)
        })
    }
    private fun reply(id: String): JSONObject = requireNotNull(replies.poll(5, TimeUnit.SECONDS)).also { assertEquals(id, it.getString("requestId")) }
    private fun call(action: String, payload: String? = null): JSONObject { send(action, action, payload); return reply(action) }
    private fun employees(name: String) = JSONArray().put(JSONObject().put("id", "e1").put("name", name)
        .put("role", "admin").put("phone", "synthetic-phone").put("pinHash", "synthetic-hash")
        .put("custom", JSONObject().put("preserve", true))).toString()

    @Test fun firstMigrationImportsLegacyInsteadOfTrustingStaleShadowAndIsIdempotent() = runBlocking {
        send("put", "stale", employees("Старая копия"), "employees"); reply("stale")
        val original = employees("Текущий сотрудник")
        assertTrue(call("employeeInitialize", original).getBoolean("authoritative"))
        assertTrue(call("employeeStatus").getBoolean("initialized"))
        assertEquals(original, call("employeeRead").getString("payload"))
        call("employeeInitialize", employees("Устаревший cache"))
        assertEquals(original, call("employeeRead").getString("payload"))
        assertNotNull(database.legacyStorageShadowDao().get(MPosEmployeeStorage.AUTHORITY_KEY))
    }

    @Test fun writeReadAndRemoveAreFifoAndDoNotReimportAfterRemoval() {
        send("employeeInitialize", "init", employees("Первый"))
        send("employeeWrite", "write", employees("Новый"))
        send("employeeRead", "read")
        assertTrue(reply("init").getBoolean("ok")); assertTrue(reply("write").getBoolean("ok"))
        assertEquals(employees("Новый"), reply("read").getString("payload"))
        assertTrue(call("employeeRemove").getBoolean("ok"))
        assertFalse(call("employeeRead").getBoolean("found"))
        call("employeeInitialize", employees("Не импортировать повторно"))
        assertFalse(call("employeeRead").getBoolean("found"))
    }

    @Test fun absentAndStoredNullRemainDifferentAndPreserveDefaultSeedingContract() {
        assertTrue(call("employeeInitialize").getBoolean("ok"))
        assertFalse(call("employeeRead").getBoolean("found"))
        assertTrue(call("employeeWrite", "null").getBoolean("ok"))
        val read = call("employeeRead")
        assertTrue(read.getBoolean("found")); assertEquals("null", read.getString("payload"))
    }

    @Test fun obsoleteShadowPutAndDeleteCannotOverwriteNativeAuthority() {
        call("employeeInitialize", employees("Основной"))
        send("put", "obsolete", employees("Cache"), "employees")
        assertTrue(reply("obsolete").getBoolean("ignored"))
        send("remove", "obsolete-remove", key = "employees")
        assertTrue(reply("obsolete-remove").getBoolean("ignored"))
        assertEquals(employees("Основной"), call("employeeRead").getString("payload"))
    }

    @Test fun failedMigrationRollsBackAuthorityMarkerDocumentAndIndexesAndCanRetry() = runBlocking {
        send("put", "old", employees("До миграции"), "employees"); reply("old")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_employee BEFORE INSERT ON employee_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("employeeInitialize", employees("Нельзя сохранить")).getBoolean("ok"))
        assertNull(database.legacyStorageShadowDao().get(MPosEmployeeStorage.AUTHORITY_KEY))
        assertEquals(employees("До миграции"), database.legacyStorageShadowDao().get("employees")!!.payload)
        assertEquals("До миграции", database.employeeProjectionDao().all().single().name)
        assertFalse(call("employeeRead").getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_employee")
        assertTrue(call("employeeInitialize", employees("Повторная миграция")).getBoolean("ok"))
    }

    @Test fun primaryWriteAndDeleteFailuresLeavePreviousCommittedCatalogReadable() = runBlocking {
        val original = employees("Сохранённый")
        call("employeeInitialize", original)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_employee BEFORE INSERT ON employee_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("employeeWrite", employees("Не сохранённый")).getBoolean("ok"))
        assertEquals(original, call("employeeRead").getString("payload"))
        assertEquals("Сохранённый", database.employeeProjectionDao().all().single().name)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_employee")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON employee_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("employeeRemove").getBoolean("ok"))
        assertEquals(original, call("employeeRead").getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_delete")
        assertTrue(call("employeeWrite", employees("Восстановленный")).getBoolean("ok"))
    }

    @Test fun fullDocumentRetainsUnknownFieldsOrderDuplicateOrMissingIdsRatherThanUsingLossyIndexes() {
        val source = "[{\"id\":\"same\",\"name\":\"Кофе ☕\",\"custom\":null},{\"name\":\"Без ID\"},{\"id\":\"same\",\"name\":\"Другой\"}]"
        call("employeeInitialize", source)
        assertEquals(source, call("employeeRead").getString("payload"))
        for (invalid in listOf("[", "{}", "[] trailing")) assertFalse(call("employeeWrite", invalid).getBoolean("ok"))
        assertEquals(source, call("employeeRead").getString("payload"))
    }

    @Test fun fileBackedCatalogAndAuthoritySurviveReopenWithoutImportingStaleLegacy() {
        call("employeeInitialize", employees("Первый"))
        call("employeeWrite", employees("Последний"))
        mirror.close(); scope.cancel(); database.close()
        open()
        assertTrue(call("employeeStatus").getBoolean("initialized"))
        call("employeeInitialize", employees("Устаревший legacy"))
        assertEquals(employees("Последний"), call("employeeRead").getString("payload"))
    }
}
