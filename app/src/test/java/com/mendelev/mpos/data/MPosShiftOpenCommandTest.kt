package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
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
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosShiftOpenCommandTest {
    private lateinit var database: MPosDatabase
    private val name = "native-opening-${UUID.randomUUID()}.db"
    private fun file(path: String) = listOf(File("../$path"), File(path)).first { it.isFile }
    private fun fixtures() = JSONArray(file("tests/fixtures/shift-open-command.json").readText())
    // Read reference only for parity; never put credentials in fixtures, results or database.
    private fun credential(kind: String): String {
        val source = file("app/src/main/assets/pos/Web/js/features/shifts.js").readText()
        val block = source.substringAfter("async function submitOpenShift()").substringBefore("function openCashMovementModal")
        val accepted = Regex("password!==(['\"])(.*?)\\1").find(block)!!.groupValues[2]
        return when (kind) { "accepted" -> accepted; "spaced" -> " $accepted"; "caseChanged" -> accepted.lowercase(); "wrong" -> "synthetic-invalid"; else -> "" }
    }
    @Before fun open() { database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build() }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private suspend fun initialize(input: JSONObject) {
        MPosShiftStorage(database).initialize(input.getJSONArray("expectedShifts").toString())
        MPosEmployeeStorage(database).initialize(input.getJSONArray("expectedEmployees").toString())
        MPosShiftStorage(database).write(input.getJSONArray("expectedShifts").toString())
        MPosEmployeeStorage(database).write(input.getJSONArray("expectedEmployees").toString())
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", "null")
        database.legacyStorageShadowDao().delete("mpos_shift_lifecycle_v1:open:new-shift:200")
    }
    private suspend fun rejected(input: JSONObject, password: String = ""): Throwable {
        try { MPosShiftOpenCommand(database).commit(input, password) } catch (error: Exception) { return error }
        throw AssertionError("opening should have failed")
    }
    @Test fun nativeOpeningMatchesReviewedSourceWithoutChangingInput() = runBlocking {
        val rows = fixtures()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val input = row.getJSONObject("input"); initialize(input)
            val snapshot = input.toString(); val expected = row.getJSONObject("expected")
            if (expected.getBoolean("ok")) {
                val result = MPosShiftOpenCommand(database).commit(input, credential(row.getString("credential")))
                assertTrue(row.getString("name"), MPosSupplyParity.same(expected.getJSONArray("shifts"), result.getJSONArray("shifts")))
                assertTrue(row.getString("name"), MPosSupplyParity.same(expected.getJSONObject("shift"), result.getJSONObject("shift")))
                assertTrue(MPosSupplyParity.same(result.getJSONArray("shifts"), JSONArray(MPosShiftStorage(database).read().getString("payload"))))
                assertEquals(input.getJSONArray("expectedEmployees").toString(), MPosEmployeeStorage(database).read().getString("payload"))
            } else {
                rejected(input, credential(row.getString("credential")))
                assertEquals(input.getJSONArray("expectedShifts").toString(), MPosShiftStorage(database).read().getString("payload"))
            }
            assertEquals(snapshot, input.toString())
            database.openHelper.readableDatabase.query("SELECT payload FROM legacy_storage_shadow").use { cursor ->
                while (cursor.moveToNext()) {
                    assertFalse(cursor.getString(0).contains(credential("accepted")))
                    assertFalse(cursor.getString(0).contains("synthetic-invalid"))
                }
            }
        }
    }
    @Test fun staleRoleHistoryPendingJournalAndUnownedDataCannotOpen() = runBlocking {
        val input = fixtures().getJSONObject(0).getJSONObject("input")
        rejected(input); initialize(input)
        MPosEmployeeStorage(database).write(JSONArray(input.getJSONArray("expectedEmployees").toString()).also { it.getJSONObject(0).put("role", "admin") }.toString())
        assertTrue(MPosShiftOpenCommand.failure(rejected(input)).getBoolean("blocked"))
        initialize(input); MPosShiftStorage(database).write("[]")
        assertTrue(MPosShiftOpenCommand.failure(rejected(input)).getBoolean("blocked"))
        initialize(input); MPosRecoveryStorage(database).write("criticalStorageJournal", """{"pending":true}""")
        assertTrue(MPosShiftOpenCommand.failure(rejected(input)).getBoolean("blocked"))
        assertEquals(input.getJSONArray("expectedShifts").toString(), MPosShiftStorage(database).read().getString("payload"))
    }
    @Test fun projectionAndMarkerFailuresRollBackWholeOpeningAndKeepCart() = runBlocking {
        val input = fixtures().getJSONObject(0).getJSONObject("input"); initialize(input)
        val cart = """{"items":[{"productId":"unpaid","qty":1}],"custom":false}"""
        MPosRecoveryStorage(database).initialize("currentOrderSession", cart)
        for (table in listOf("shift_projection", "legacy_storage_shadow")) {
            val condition = if (table == "legacy_storage_shadow") "WHEN NEW.key LIKE 'mpos_shift_lifecycle_v1:%'" else ""
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_opening BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
            rejected(input)
            assertEquals(input.getJSONArray("expectedShifts").toString(), MPosShiftStorage(database).read().getString("payload"))
            assertNull(database.legacyStorageShadowDao().get("mpos_shift_lifecycle_v1:open:new-shift:200"))
            assertEquals(cart, MPosRecoveryStorage(database).read("currentOrderSession").getString("payload"))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_opening")
        }
        MPosShiftOpenCommand(database).commit(input, "")
        assertEquals(cart, MPosRecoveryStorage(database).read("currentOrderSession").getString("payload"))
    }
    @Test fun savedOpeningSurvivesRestartAndRepeatedSubmissionCannotDuplicateIt() = runBlocking {
        val input = fixtures().getJSONObject(0).getJSONObject("input"); initialize(input)
        MPosShiftOpenCommand(database).commit(input, "")
        database.close(); database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        rejected(input)
        assertEquals(input.getJSONArray("expectedShifts").length() + 1, MPosShiftStorage(database).readRecords().length())
    }
    @Test fun nativeQueueCapturesInputAndSerializesCompetingOpenings() = runBlocking {
        val input = fixtures().getJSONObject(0).getJSONObject("input"); initialize(input)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val replies = Channel<JSONObject>(Channel.UNLIMITED)
        val mirror = MPosStorageMirror(database, scope) { replies.trySend(it) }
        try {
            mirror.openShift(input.put("requestId", "native-shift-open-commit-1"), "synthetic-invalid")
            mirror.openShift(JSONObject(input.toString()).put("id", "other-shift").put("requestId", "native-shift-open-commit-2"), "")
            input.getJSONArray("expectedEmployees").getJSONObject(0).put("name", "Changed after capture")
            val first = withTimeout(10000) { replies.receive() }
            val second = withTimeout(10000) { replies.receive() }
            assertEquals("native-shift-open-commit-1", first.getString("requestId")); assertTrue(first.getBoolean("ok"))
            assertEquals("Кассир", first.getJSONObject("shift").getString("employeeName"))
            assertEquals("native-shift-open-commit-2", second.getString("requestId")); assertFalse(second.getBoolean("ok")); assertTrue(second.getBoolean("blocked"))
            assertEquals(3, MPosShiftStorage(database).readRecords().length())
            assertFalse(first.toString().contains("synthetic-invalid")); assertFalse(second.toString().contains("synthetic-invalid"))
        } finally { mirror.close(); scope.cancel(); replies.close() }
    }
    @Test fun errorsAreFixedMessagesWithoutParserSqlOrCredentialPayloads() {
        val secret = credential("accepted")
        for (error in listOf(Exception("administrator credential rejected"), Exception("SQL payload $secret"), Exception("invalid JSON $secret"))) {
            val result = MPosShiftOpenCommand.failure(error)
            assertFalse(result.toString().contains(secret)); assertFalse(result.toString().contains("SQL")); assertFalse(result.toString().contains("JSON"))
        }
        assertFalse(MPosShiftOpenCommand.failure(Exception("administrator credential rejected")).getBoolean("blocked"))
    }

    @Test fun failedResultDeliveryAfterCommitRequiresReloadRatherThanResubmit() = runBlocking {
        val input = fixtures().getJSONObject(0).getJSONObject("input"); initialize(input)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reply = Channel<JSONObject>(1)
        var first = true
        val mirror = MPosStorageMirror(database, scope) {
            if (first) { first = false; throw IllegalStateException("synthetic lost acknowledgement") }
            reply.trySend(it)
        }
        try {
            mirror.openShift(input.put("requestId", "native-shift-open-commit-lost"), "")
            val failure = withTimeout(10000) { reply.receive() }
            assertFalse(failure.getBoolean("ok")); assertTrue(failure.getBoolean("blocked"))
            assertEquals(3, MPosShiftStorage(database).readRecords().length())
            assertEquals("new-shift", MPosShiftStorage(database).readRecords().getJSONObject(2).getString("id"))
        } finally { mirror.close(); scope.cancel(); reply.close() }
    }
}
