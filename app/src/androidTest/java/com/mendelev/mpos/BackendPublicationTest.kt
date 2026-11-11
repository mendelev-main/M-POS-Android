package com.mendelev.mpos

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.ProductInput
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class BackendPublicationTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun alwaysAvailableSendsNullThenRestoresPhysicalStockWithoutChangingBalance()=runBlocking {
        val root=File(app.cacheDir,"online-unlimited-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object: ContextWrapper(app) {override fun getNoBackupFilesDir()=root}
        val db=Room.inMemoryDatabaseBuilder(app,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            val details=com.mendelev.mpos.domain.catalog.ProductDetails(alwaysAvailableOnline=true)
            val input=ProductInput("p",null,"Кофе",null,"10","3","0",null,true,"create",details=details)
            catalog.saveProduct(input)
            val settings=BackendSettings(url="https://fixture.example.org",deviceKey="fixture",datasetId="fixture")
            val sent=mutableListOf<JSONObject>()
            val client=OkHttpClient.Builder().addInterceptor {chain->
                val request=chain.request();assertEquals(settings.availabilityPath,request.url.encodedPath)
                val raw=Buffer();request.body!!.writeTo(raw);sent+=JSONObject(raw.readUtf8())
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body("""{"ok":true,"applied":true}""".toResponseBody("application/json".toMediaType())).build()
            }.build()
            val publication=BackendPublication(context,BackendHttp(client),db) {}
            val before=db.catalog().balance("p")
            publication.availability(settings,emptyList())
            assertTrue(sent.last().getJSONArray("items").getJSONObject(0).isNull("quantity"))
            catalog.saveProduct(input.copy(expectedRevision=1,stockEdited=false,commandId="disable",details=details.copy(alwaysAvailableOnline=false)))
            publication.availability(settings,emptyList())
            assertEquals(0,sent.last().getJSONArray("items").getJSONObject(0).getInt("quantity"))
            assertEquals(before,db.catalog().balance("p"))
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun lostTariffReplyReusesPersistedPublicationIdWithoutDuplicateVersion()=runBlocking {
        val root=File(app.cacheDir,"backend-publication-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object: ContextWrapper(app) {override fun getNoBackupFilesDir()=root}
        val db=Room.inMemoryDatabaseBuilder(app,CatalogDatabase::class.java).build()
        try {
            val catalog=CatalogRepository(db);catalog.initialize()
            catalog.saveProduct(ProductInput("p",null,"Кофе",null,"10","3","20",null,true,"create"))
            val s=BackendSettings(url="https://fixture.example.org",deviceKey="fixture-key",datasetId="fixture")
            var version=0L;var lastId="";var lose=true;val sentIds=mutableListOf<String>()
            val client=OkHttpClient.Builder().addInterceptor {chain->
                val request=chain.request();assertEquals(s.deviceKey,request.header("X-Device-Key"))
                var reply=JSONObject().put("ok",true)
                when(request.url.encodedPath) {
                    s.tariffsPath->if(request.method=="GET") reply=JSONObject().put("version",version) else {
                        val raw=Buffer();request.body!!.writeTo(raw);val job=JSONObject(raw.readUtf8());val id=job.getString("publicationId");sentIds+=id
                        if(id!=lastId) {assertEquals(version,job.getLong("baseVersion"));version++;lastId=id}
                        if(lose) {lose=false;throw IOException("fixture: applied but reply lost")}
                        reply.put("version",version)
                    }
                    s.availabilityPath->reply.put("applied",true).put("ignoredAsStale",false)
                    s.menuPath->Unit
                    else->fail("Unexpected endpoint")
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(reply.toString().toResponseBody("application/json".toMediaType())).build()
            }.build()
            val publication=BackendPublication(context,BackendHttp(client),db) {assertEquals(s,it)}
            val first=publication.publish(s,publication.preview(s),{}, {})
            assertTrue(first.contains("Частичный результат"))
            assertEquals(1L,version)
            val second=publication.publish(s,publication.preview(s),{}, {})
            assertFalse(second.contains("Частичный результат"))
            assertEquals(1L,version);assertEquals(2,sentIds.size);assertEquals(1,sentIds.distinct().size)
        } finally {db.close();root.deleteRecursively()}
    }
    @Test fun changedServerTariffsNeedNewPreviewAndDoNotSilentlyOverwriteVersion()=runBlocking {
        val root=File(app.cacheDir,"backend-conflict-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object: ContextWrapper(app) {override fun getNoBackupFilesDir()=root}
        val db=Room.inMemoryDatabaseBuilder(app,CatalogDatabase::class.java).build()
        try {
            CatalogRepository(db).initialize()
            val s=BackendSettings(url="https://fixture.example.org",deviceKey="fixture-key",datasetId="fixture")
            var version=0L;var writes=0
            val client=OkHttpClient.Builder().addInterceptor {chain->
                val request=chain.request();val reply=JSONObject().put("ok",true).put("applied",true)
                if(request.url.encodedPath==s.tariffsPath) {
                    if(request.method=="PUT") {val raw=Buffer();request.body!!.writeTo(raw);assertEquals(version,JSONObject(raw.readUtf8()).getLong("baseVersion"));writes++;version++}
                    reply.put("version",version)
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(reply.toString().toResponseBody("application/json".toMediaType())).build()
            }.build()
            val publication=BackendPublication(context,BackendHttp(client),db) {}
            val old=publication.preview(s);version=1
            assertTrue(publication.publish(s,old,{},{}).contains("Частичный результат"));assertEquals(0,writes)
            assertFalse(publication.publish(s,publication.preview(s),{},{}).contains("Частичный результат"));assertEquals(1,writes)
        } finally {db.close();root.deleteRecursively()}
    }
}
