package com.mendelev.mpos

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mendelev.mpos.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReceiptPagingRepositoryTest {
    @Test fun sqliteLimitReturnsFiftyAtATimeForMixedImportedAndNativeRows()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            db.shifts().insert(ShiftEntity("s","register","employee","Тест",1,"Europe/Minsk",0,null,100000,0,0,0,null))
            repeat(73) {i->db.sales().insert(ReceiptEntity("n-$i","c-$i","session-$i","s",i.toLong(),1000L-i*2,100,100,0,0,"{}"))}
            repeat(84) {i->
                val at=999L-i*2;val id="web:$i"
                val payload=JSONObject().put("version",1).put("id",id).put("sourceId",i.toString()).put("source","a".repeat(64)).put("createdAt",at).put("total",100).put("rawJson",JSONObject().put("id",i).toString()).toString()
                db.workspace().put(WorkspaceDocument(WebReceiptIndex.PREFIX+(Long.MAX_VALUE-at).toString().padStart(19,'0')+":"+id,1,payload))
            }
            val seen=mutableSetOf<String>();var no=0;var wo=0;var pages=0
            do {
                val page=ReceiptHistoryPaging(db).page(no,wo)
                val ids=page.native.map {it.id}+page.web.map {it.id}
                assertTrue(ids.size<=50);assertTrue(ids.none {it in seen});seen+=ids
                no=page.nativeOffset;wo=page.webOffset;pages++
            } while(page.more)
            assertEquals(157,seen.size);assertEquals(4,pages)
            assertEquals(51,db.sales().historyPage(51,0).size)
            assertEquals(51,db.workspace().prefixPage(WebReceiptIndex.PREFIX,51,0).size)
        } finally {db.close()}
    }
}
