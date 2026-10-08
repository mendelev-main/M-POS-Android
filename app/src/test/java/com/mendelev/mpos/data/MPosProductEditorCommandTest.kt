package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosProductEditorCommandTest {
    private val seed="""[{"id":"p","name":"Coffee","category":"Drinks","price":2,"type":"simple","stock":5,"cost":1,"availableOnline":false,"availableInOnlineMenu":false,"description":"","extension":7}]"""
    private fun runCase(block:suspend (MPosDatabase,MPosEditorGrants,MPosProductEditorCommand)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            MPosCatalogStorage(db).initialize(seed);MPosOrderStorage(db).initialize("[]")
            MPosEmployeeStorage(db).initialize("""[{"id":"a","role":"employee"}]""")
            MPosShiftStorage(db).initialize("""[{"id":"s","status":"open","employeeId":"a"}]""")
            MPosRecoveryStorage(db).initialize("criticalStorageJournal","null")
            val grants=MPosEditorGrants();block(db,grants,MPosProductEditorCommand(db,grants))
        }finally{db.close()}
    }
    private fun input(key:String="name",value:Any="New")=JSONObject().put("version",1).put("editingId","p")
        .put("expected",JSONArray(seed)).put("nextProducts",JSONArray(seed).apply { getJSONObject(0).put(key,value) })
    private suspend fun denied(engine:MPosProductEditorCommand,input:JSONObject){try{engine.commit(input.toString());fail("unexpected save")}catch(_:IllegalStateException){}}
    @Test fun ordinaryEditPreservesExtensionsWithoutCredential()=runCase {db,_,engine->
        engine.commit(input().toString());val saved=JSONArray(MPosCatalogStorage(db).read().getString("payload")).getJSONObject(0)
        assertEquals("New",saved.getString("name"));assertEquals(7,saved.getInt("extension"))
    }
    @Test fun protectedFieldsAndForgedTokensAreRejectedForOrdinaryEmployee()=runCase {db,_,engine->
        for((key,value) in listOf("stock" to 20,"noStockTracking" to true,"stockUnit" to "kg","minStock" to 2,"cost" to 2,"availableOnline" to true,"description" to "Changed")) denied(engine,input(key,value))
        denied(engine,input("stock",20).put("grants",JSONObject().put("stock","forged")))
        assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"))
    }
    @Test fun verifiedGrantIsProductAndFieldBoundAndConsumedOnlyAfterCommit()=runCase {db,grants,engine->
        val authorization=MPosEditorAuthorization(db,grants){it=="synthetic-confirmation"}
        val request=JSONObject().put("version",1).put("operation","stock").put("productId","p").put("expected",JSONArray(seed).getJSONObject(0))
        try{authorization.authorize(request.toString(),"synthetic-wrong");fail("wrong credential accepted")}catch(_:IllegalStateException){}
        val token=authorization.authorize(request.toString(),"synthetic-confirmation").getString("grant")
        assertFalse(grants.allows(token,"no-stock","p",JSONArray(seed).getJSONObject(0)))
        assertFalse(grants.allows(token,"stock","another",JSONArray(seed).getJSONObject(0)))
        engine.commit(input("stock",20).put("grants",JSONObject().put("stock",token)).toString())
        assertFalse(grants.allows(token,"stock","p",JSONArray(seed).getJSONObject(0)))
        assertEquals(20,JSONArray(MPosCatalogStorage(db).read().getString("payload")).getJSONObject(0).getInt("stock"))
    }
    @Test fun changedProductCannotUseOldGrantAndPendingRecoveryPreventsAuthorization()=runCase {db,grants,engine->
        val prior=JSONArray(seed).getJSONObject(0);val token=grants.issue("stock","p",prior)
        val changed=JSONArray(seed);changed.getJSONObject(0).put("name","Newer");MPosCatalogStorage(db).write(changed.toString())
        denied(engine,input("stock",20).put("expected",changed).apply { getJSONArray("nextProducts").getJSONObject(0).put("name","Newer") }.put("grants",JSONObject().put("stock",token)))
        MPosRecoveryStorage(db).write("criticalStorageJournal","""{"operation":"pending"}""")
        val authorization=MPosEditorAuthorization(db,grants){true}
        try{authorization.authorize(JSONObject().put("version",1).put("operation","stock").put("productId","p").put("expected",changed.getJSONObject(0)).toString(),"synthetic");fail("recovery ignored")}catch(_:IllegalStateException){}
    }
    @Test fun liveAdminCanEditProtectedFieldsButDemotionAndClosedShiftRemoveBypass()=runCase {db,_,engine->
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"admin"}]""")
        val c=input("stock",20)
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"employee"}]""");denied(engine,c)
        MPosEmployeeStorage(db).write("""[{"id":"a","role":"admin"}]""");MPosShiftStorage(db).write("[]");denied(engine,c)
        MPosShiftStorage(db).write("""[{"id":"s","status":"open","employeeId":"a"}]""");engine.commit(c.toString())
        assertEquals(20,JSONArray(MPosCatalogStorage(db).read().getString("payload")).getJSONObject(0).getInt("stock"))
    }
    @Test fun knownRollbackRetainsGrantAndDocumentForCorrection()=runCase {db,grants,engine->
        val prior=JSONArray(seed).getJSONObject(0);val token=grants.issue("stock","p",prior)
        val c=input("stock",20).put("grants",JSONObject().put("stock",token))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_product BEFORE INSERT ON product_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try{engine.commit(c.toString());fail("failed write accepted")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"));assertTrue(grants.allows(token,"stock","p",prior))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_product");engine.commit(c.toString())
        assertFalse(grants.allows(token,"stock","p",prior))
    }
    @Test fun typeChangesRecheckLiveUnreturnedReceiptAtCommit()=runCase {db,_,engine->
        val c=input("type","composite");c.getJSONArray("nextProducts").getJSONObject(0).put("components",JSONArray().put(JSONObject().put("productId","ingredient").put("qty",1)))
        MPosOrderStorage(db).write("""[{"id":"receipt","stockConsumption":{"items":[{"productId":"p","qty":1}]}}]""")
        denied(engine,c);assertEquals(seed,MPosCatalogStorage(db).read().getString("payload"))
    }
    @Test fun grantLifetimeIsRuntimeOnlyAndNewAuthorizationReplacesOldToken()=runCase {_,grants,_->
        val prior=JSONArray(seed).getJSONObject(0);val first=grants.issue("stock","p",prior);val second=grants.issue("stock","p",prior)
        assertFalse(grants.allows(first,"stock","p",prior));assertTrue(grants.allows(second,"stock","p",prior))
        assertFalse(MPosEditorGrants().allows(second,"stock","p",prior))
    }
}
