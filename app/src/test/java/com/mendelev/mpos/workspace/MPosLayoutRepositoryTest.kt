package com.mendelev.mpos.workspace

import androidx.room.Room
import com.mendelev.mpos.data.*
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
class MPosLayoutRepositoryTest {
    private fun runCase(category:String?=null,block:suspend(MPosDatabase,MPosWorkspaceNavigationOwner,MPosLayoutRepository)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            MPosCatalogStorage(db).initialize("""[{"id":"p","category":"Кофе","name":"Латте"},{"id":"q","category":"Кофе","name":"Капучино"}]""")
            MPosWorkspaceStorage(db).initialize("layout","""{"categoryOrder":["Кофе"],"colors":{"Кофе":"#123456"},"extra":42,"tiles":[{"type":"category","id":"Кофе","col":0,"row":0}]}""")
            MPosWorkspaceStorage(db).initialize("posNavigation","""{"version":1,"categories":[]}""")
            val owner=MPosWorkspaceNavigationOwner()
            owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos").put("editMode",true).put("posPath",category?:JSONObject.NULL))
            block(db,owner,MPosLayoutRepository(db,owner))
        }finally{db.close()}
    }
    private fun input(owner:MPosWorkspaceNavigationOwner,operation:String="layoutView",parent:String="")=JSONObject().put("version",1).put("operation",operation).put("parent",parent)
        .put("expected",owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot"))
    private suspend fun commit(repo:MPosLayoutRepository,owner:MPosWorkspaceNavigationOwner,command:JSONObject,parent:String=""):JSONObject {
        val view=repo.execute(input(owner,parent=parent))
        return repo.execute(input(owner,"layoutCommit",parent).put("documentRevision",view.getString("documentRevision")).put("command",command))
    }
    @Test fun rootCommandsReadRoomAndPreserveMetadataAndFinancialDocuments()=runCase {db,owner,repo->
        val products=db.legacyStorageShadowDao().get("products")
        val model=repo.execute(input(owner));assertEquals("Рабочая зона",model.getString("title"));assertEquals(3,model.getJSONArray("choices").length())
        val added=commit(repo,owner,JSONObject().put("operation","addTile").put("type","product").put("id","p").put("tiles",JSONArray()))
        assertTrue(added.getBoolean("allowed"));assertEquals(2,added.getJSONObject("document").getJSONArray("tiles").length())
        assertEquals(42,added.getJSONObject("document").getInt("extra"));assertEquals("#123456",added.getJSONObject("document").getJSONObject("colors").getString("Кофе"))
        commit(repo,owner,JSONObject().put("operation","moveTile").put("index",1).put("col",0).put("row",0).put("cols",5))
        val moved=JSONObject(db.legacyStorageShadowDao().get("layout")!!.payload).getJSONArray("tiles").getJSONObject(1)
        assertEquals(1,moved.getInt("col"));assertEquals(0,moved.getInt("row"))
        commit(repo,owner,JSONObject().put("operation","removeTile").put("index",0))
        assertEquals(1,repo.execute(input(owner)).getJSONArray("tiles").length())
        assertEquals(products,db.legacyStorageShadowDao().get("products"));assertNull(db.legacyStorageShadowDao().get("receipts"))
    }
    @Test fun staleImportOrNavigationRejectsCommitWithoutOverwritingDocuments()=runCase {db,owner,repo->
        val view=repo.execute(input(owner))
        val command=input(owner,"layoutCommit").put("documentRevision",view.getString("documentRevision")).put("command",JSONObject().put("operation","removeTile").put("index",0))
        MPosWorkspaceStorage(db).write("layout","""{"tiles":[],"imported":true}""")
        val before=db.legacyStorageShadowDao().get("layout")
        try{repo.execute(command);fail("stale import accepted")}catch(_:IllegalStateException){}
        assertEquals(before,db.legacyStorageShadowDao().get("layout"))
        owner.handle(JSONObject().put("version",1).put("operation","selectSearch").put("search","changed"))
        try{repo.execute(command);fail("stale owner accepted")}catch(_:IllegalStateException){}
        assertEquals(before,db.legacyStorageShadowDao().get("layout"))
    }
    @Test fun twentyTilesLimitRejectsAdditionAndDoesNotWrite()=runCase {db,owner,repo->
        val tiles=JSONArray((0 until 20).map{JSONObject().put("type","product").put("id","p").put("col",it%5).put("row",it/5)})
        MPosWorkspaceStorage(db).write("layout",JSONObject().put("tiles",tiles).toString());val before=db.legacyStorageShadowDao().get("layout")
        assertFalse(commit(repo,owner,JSONObject().put("operation","addTile").put("type","product").put("id","q")).getBoolean("allowed"))
        assertEquals(before,db.legacyStorageShadowDao().get("layout"))
    }
    @Test fun folderCreateRenameMoveReorderDeletePreserveProductsAndReturnItemsToRoot()=runCase("Кофе") {db,owner,repo->
        val before=db.legacyStorageShadowDao().get("products")
        val created=commit(repo,owner,JSONObject().put("operation","saveFolder").put("name","Молочный кофе"))
        val folder=created.getJSONObject("document").getJSONArray("categories").getJSONObject(0).getJSONArray("items").let{a->(0 until a.length()).map{a.getJSONObject(it)}.first{it.getString("type")=="folder"}.getString("id")}
        commit(repo,owner,JSONObject().put("operation","saveFolder").put("id",folder).put("name","Латте"))
        commit(repo,owner,JSONObject().put("operation","moveProduct").put("id","p").put("folderId",folder))
        assertEquals("p",repo.execute(input(owner,parent=folder)).getJSONArray("tiles").getJSONObject(0).getString("id"))
        commit(repo,owner,JSONObject().put("operation","reorder").put("type","product").put("id","q").put("targetIndex",0))
        assertEquals("q",repo.execute(input(owner)).getJSONArray("tiles").getJSONObject(0).getString("id"))
        commit(repo,owner,JSONObject().put("operation","removeFolder").put("id",folder))
        val model=repo.execute(input(owner));assertEquals(2,model.getJSONArray("tiles").length());assertEquals(0,model.getJSONArray("folders").length())
        assertEquals(before,db.legacyStorageShadowDao().get("products"))
        try{repo.execute(input(owner,parent=folder));fail("deleted parent accepted")}catch(_:IllegalStateException){}
    }
}
