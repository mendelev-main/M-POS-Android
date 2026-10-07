package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
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
@Config(sdk=[28],manifest=Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosSupplyAuthorityTest {
    @Test fun nativeFifoMigrationIgnoresStaleMirrorsAndRetainsDocumentsAfterReopen(){
        val name="supply-authority-${UUID.randomUUID()}.db";val context=RuntimeEnvironment.getApplication()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);val replies=LinkedBlockingQueue<JSONObject>()
        var db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build();var mirror=MPosStorageMirror(db,scope){replies.add(it)}
        fun call(action:String,key:String,raw:String?=null):JSONObject{
            mirror.handle(JSONObject().put("action",action).put("key",key).put("requestId",action).apply{if(raw!=null)put("payload",raw)})
            return requireNotNull(replies.poll(5,TimeUnit.SECONDS)).also{assertTrue("$action failed",it.getBoolean("ok"))}
        }
        val arrayRaw="[{\"id\":\"s\",\"name\":\"Supply\",\"extra\":{\"v\":7}}]"
        try{
            for(key in MPosSupplyStorage.KEYS){
                val raw=if(key=="receivingDraft")"{\"version\":1,\"lines\":[],\"extra\":7}" else arrayRaw
                val initial=if(key=="receivingDraft")"null" else "[]"
                call("put",key,initial);call("supplyInitialize",key,raw);call("supplyInitialize",key,initial)
                assertTrue(call("put",key,"[]").getBoolean("ignored"));assertTrue(call("remove",key).getBoolean("ignored"));assertEquals(raw,call("supplyRead",key).getString("payload"))
            }
            mirror.close();db.close();db=Room.databaseBuilder(context,MPosDatabase::class.java,name).build();mirror=MPosStorageMirror(db,scope){replies.add(it)}
            for(key in MPosSupplyStorage.KEYS)assertEquals(if(key=="receivingDraft")"{\"version\":1,\"lines\":[],\"extra\":7}" else arrayRaw,call("supplyRead",key).getString("payload"))
        }finally{mirror.close();scope.cancel();db.close();context.deleteDatabase(name)}
    }
}
