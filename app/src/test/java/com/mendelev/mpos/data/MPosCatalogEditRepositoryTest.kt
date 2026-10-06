package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
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
class MPosCatalogEditRepositoryTest {
    @Test fun typeChangeReadsRoomHistoryAndCannotTrustForgedClientArchive()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try{
            val products="""[{"id":"p","type":"simple","name":"Milk","category":"C","stock":2}]"""
            MPosCatalogStorage(db).initialize(products)
            MPosOrderStorage(db).initialize("""[{"id":"o","items":[],"stockConsumption":{"items":[{"productId":"p","qty":1}]}}]""")
            val request=JSONObject("""{"version":1,"operation":"product","editingId":"p","name":"Milk","type":"composite","components":[{"productId":"x"}],"products":[],"orders":[]}""")
            val result=MPosCatalogEditRepository(db).calculate(request.toString())
            assertFalse(result.getBoolean("allowed"));assertTrue(result.getString("message").contains("возврата"))
            assertEquals(products,MPosCatalogStorage(db).read().getString("payload"))
        }finally{db.close()}
    }
}
