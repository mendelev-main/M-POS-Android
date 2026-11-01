package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.report.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

class WarehouseReportTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val period=ReportPeriod(LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-01"),ZoneId.of("Europe/Minsk"))
    @Test fun real8To9MigrationPreservesLegacyUnitUnknownReplayAndReadOnlyReopen()=runBlocking {
        val name="warehouse89-${UUID.randomUUID()}.db"
        val input=ProductInput("p",null,"Товар",null,"1","2","10",null,true,"old-command",unit="kg",displayUnit="kg")
        val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("com.mendelev.mpos.data.local.CatalogDatabase/8.json").bufferedReader().use {JSONObject(it.readText()).getJSONObject("database")}
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use {old->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val table=e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                e.optJSONArray("indices")?.let {a->for(j in 0 until a.length()) old.execSQL(a.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
            }
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO app_config VALUES (1,'register','owner','BYN')")
            old.execSQL("INSERT INTO products (id,name,searchKey,price,cost,revision,unit,displayUnit,type,currency) VALUES ('p','Товар','товар','1','2',1,'kg','kg','simple','BYN')")
            old.execSQL("INSERT INTO stock_balances VALUES ('p','10',1)")
            old.execSQL("INSERT INTO stock_movements VALUES ('old','p','10','opening','old-command',?,NULL)",arrayOf(period.start+1))
            old.execSQL("INSERT INTO operation_results VALUES (?,?,?)",arrayOf("old-command","product:"+input.validated().fingerprint(),"p"))
            old.version=8
        }
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).addMigrations(CatalogDatabase.MIGRATION_8_9).build()
        var db=open()
        try {
            val before=db.catalog().movements();assertNull(before.single().unit)
            val product=db.catalog().product("p");val balance=db.catalog().balance("p")
            assertEquals("p",CatalogRepository(db).saveProduct(input))
            val clock=Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"),ZoneOffset.UTC)
            val report=SalesReportRepository(db,clock).snapshot(period)
            val row=report.warehouse!!.rows.single();assertNull(row.start);assertEquals(0,row.end!!.compareTo("10".toBigDecimal()))
            assertTrue(row.warnings.isNotEmpty());assertEquals(before,db.catalog().movements());assertEquals(product,db.catalog().product("p"));assertEquals(balance,db.catalog().balance("p"))
            db.close();db=open();assertEquals(report,SalesReportRepository(db,clock).snapshot(period));assertNull(db.catalog().movements().single().unit)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun newMovementCapturesBaseUnitAndSqlFailureRollsBackWholeProductCreation()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_unit BEFORE INSERT ON stock_movements BEGIN SELECT RAISE(ABORT,'fail unit'); END")
            val input=ProductInput("p",null,"Мука",null,"1","2","0.0004",null,true,"create-unit",unit="kg",displayUnit="kg")
            assertTrue(runCatching {repo.saveProduct(input)}.isFailure)
            assertNull(db.catalog().product("p"));assertNull(db.catalog().balance("p"));assertNull(db.catalog().result("create-unit"))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_unit")
            repo.saveProduct(input)
            val saved=db.catalog().movements().single();assertEquals("kg",saved.unit);assertEquals("0.0004",saved.delta)
            repo.saveProduct(input);assertEquals(listOf(saved),db.catalog().movements())
        } finally {db.close()}
    }
}
