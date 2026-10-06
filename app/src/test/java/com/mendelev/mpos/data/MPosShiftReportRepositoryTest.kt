package com.mendelev.mpos.data

import androidx.room.Room
import com.mendelev.mpos.telegram.MPosShiftReceipt
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
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosShiftReportRepositoryTest {
    private lateinit var database: MPosDatabase
    private val name = "shift-report-${UUID.randomUUID()}.db"
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosShiftStorage(database).initialize("[]")
        MPosOrderStorage(database).initialize("[]")
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", "null")
        Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun request(closedOnly: Boolean = true) = JSONObject().put("shiftId", "s1").put("currency", "BYN").put("establishmentName", "Тестовая касса").put("closedOnly", closedOnly)
    private suspend fun seed() {
        MPosShiftStorage(database).write("""[{"id":"s1","status":"closed","openedAt":100,"closedAt":3000,"openingCash":100,"countedCash":119,"custom":true}]""")
        MPosOrderStorage(database).write("""[{"id":"r1","shiftId":"s1","method":"cash","total":20,"timestamp":1000,"items":[{"name":"Чай","qty":1,"price":20}],"custom":"preserved"}]""")
    }
    private fun same(expected: Any?, actual: Any?) {
        when {
            expected is JSONObject && actual is JSONObject -> {
                assertEquals(expected.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
                expected.keys().asSequence().forEach { same(expected.opt(it), actual.opt(it)) }
            }
            expected is JSONArray && actual is JSONArray -> {
                assertEquals(expected.length(), actual.length())
                for (i in 0 until expected.length()) same(expected.opt(i), actual.opt(i))
            }
            expected is Number && actual is Number -> assertEquals(expected.toDouble(), actual.toDouble(), 0.00000001)
            else -> assertEquals(expected, actual)
        }
    }
    @Test fun persistedReportsMatchReviewedRuntimeFixturesWithoutMutatingSource() = runBlocking {
        val file = listOf(File("../tests/fixtures/shift-report-model.json"), File("tests/fixtures/shift-report-model.json")).first { it.exists() }
        val fixtures = JSONArray(file.readText())
        for (i in 0 until fixtures.length()) {
            val f = fixtures.getJSONObject(i)
            val shift = f.getJSONObject("shift")
            val beforeShifts = JSONArray().put(shift).toString()
            val beforeOrders = f.getJSONArray("orders").toString()
            MPosShiftStorage(database).write(beforeShifts); MPosOrderStorage(database).write(beforeOrders)
            val query = request().put("shiftId", shift.getString("id"))
            val result = MPosShiftReportRepository(database).read(query.toString())
            same(f.getJSONObject("expected"), result.getJSONObject("report"))
            assertEquals(beforeShifts, MPosShiftStorage(database).read().getString("payload"))
            assertEquals(beforeOrders, MPosOrderStorage(database).read().getString("payload"))
            val summary = result.getJSONObject("summary")
            assertEquals(result.getJSONObject("report").getDouble("expectedCash"), summary.getDouble("expectedCash"), 0.0)
        }
    }
    @Test fun reportsAfterReopenReadCurrentRowsRatherThanCallerFinancialValues() = runBlocking {
        seed(); database.close(); database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        val input = request().put("cash", 9999).put("expectedCash", 9999).put("orders", JSONArray())
        val first = MPosShiftReportRepository(database).read(input.toString())
        assertEquals(120.0, first.getJSONObject("report").getDouble("expectedCash"), 0.0)
        assertEquals(-1.0, first.getJSONObject("report").getDouble("difference"), 0.0)
        MPosOrderStorage(database).write("""[{"id":"r1","shiftId":"s1","method":"card","total":30,"timestamp":1000}]""")
        val second = MPosShiftReportRepository(database).read(request().toString())
        assertEquals(100.0, second.getJSONObject("report").getDouble("expectedCash"), 0.0)
        assertEquals(30.0, second.getJSONObject("report").getDouble("card"), 0.0)
        assertTrue(second.getLong("ordersRevision") > first.getLong("ordersRevision"))
    }
    @Test fun pendingRecoveryMissingOrOpenShiftCannotGenerateClosingOutput() = runBlocking {
        seed()
        suspend fun reject(query: JSONObject) {
            var failed = false
            try { MPosShiftReportRepository(database).read(query.toString()) } catch (_: Exception) { failed = true }
            assertTrue(failed)
        }
        reject(request().put("shiftId", "absent"))
        MPosRecoveryStorage(database).write("criticalStorageJournal", """{"version":1,"writes":[]}""")
        reject(request()); MPosRecoveryStorage(database).write("criticalStorageJournal", "null")
        MPosShiftStorage(database).write("""[{"id":"s1","status":"open","openingCash":100}]""")
        reject(request())
        assertEquals(120.0, MPosShiftReportRepository(database).read(request(false).toString()).getJSONObject("report").getDouble("expectedCash"), 0.0)
    }
    @Test fun invalidFinancialScalarRejectsInsteadOfManufacturingZeroBalance() = runBlocking {
        seed()
        val raw = """[{"id":"s1","status":"closed","openingCash":"invalid","countedCash":100}]"""
        MPosShiftStorage(database).write(raw)
        var failed = false
        try { MPosShiftReportRepository(database).read(request().toString()) } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(raw, MPosShiftStorage(database).read().getString("payload"))
    }
    @Test fun nullArchiveProducesZeroSalesAndRestoreUpdatesReportWithoutOldCache() = runBlocking {
        seed(); MPosOrderStorage(database).write("null")
        val report = MPosShiftReportRepository(database).read(request().toString()).getJSONObject("report")
        assertEquals(0, report.getInt("count")); assertEquals(100.0, report.getDouble("expectedCash"), 0.0)
        MPosShiftStorage(database).write("""[{"id":"s1","status":"closed","openingCash":40,"countedCash":40}]""")
        assertEquals(40.0, MPosShiftReportRepository(database).read(request().toString()).getJSONObject("report").getDouble("expectedCash"), 0.0)
    }
    @Test fun nativeReceiptPresentationKeepsAllSaleReceiptsIncludingSameShiftReturn() = runBlocking {
        MPosShiftStorage(database).write("""[{"id":"s1","status":"closed","openingCash":100,"countedCash":100,"cashMovements":[{"id":"m1","type":"withdrawal","subtype":"refund","amount":20,"timestamp":2000}]}]""")
        MPosOrderStorage(database).write("""[{"id":"r1","shiftId":"s1","method":"cash","total":20,"returnedAt":2000,"returnedShiftId":"s1","returnAmount":20}]""")
        val report = MPosShiftReportRepository(database).read(request().toString()).getJSONObject("report")
        assertEquals(0, report.getInt("count")); assertEquals(1, report.getJSONArray("orders").length())
        assertEquals("1", MPosShiftReceipt.rows(report).single { it.label == "Количество чеков" }.value)
        assertEquals("100.00 BYN", MPosShiftReceipt.rows(report).single { it.label == "Ожидается в кассе" }.value)
        val zero = MPosShiftReportRepository.build(JSONObject().put("id", "zero").put("openingCash", 0).put("countedCash", -0.0), JSONArray(), "BYN", "Тестовая касса").getJSONObject("report")
        assertEquals("0.00 BYN", MPosShiftReceipt.rows(zero).single { it.label == "Фактически в кассе" }.value)
    }
}
