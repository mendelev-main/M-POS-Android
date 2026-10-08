package com.mendelev.mpos.workspace

import androidx.room.Room
import com.mendelev.mpos.data.MPosCatalogStorage
import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.MPosWorkspaceStorage
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
class MPosWorkspaceNavigationRepositoryTest {
    private fun search(owner:MPosWorkspaceNavigationOwner,query:String)=JSONObject().put("version",1).put("operation","selectFilteredSearch")
        .put("search",query).put("expected",owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot"))
        .put("tiles",JSONArray("""[{"type":"product","id":"p"},{"type":"folder","id":"f"}]"""))
    private fun runCase(block:suspend(MPosDatabase,MPosWorkspaceNavigationOwner,MPosWorkspaceNavigationRepository)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            MPosCatalogStorage(db).initialize("""[{"id":"p","category":"Кофе"}]""")
            MPosWorkspaceStorage(db).initialize("posNavigation","""{"version":1,"categories":[{"category":"Кофе","items":[{"id":"f","type":"folder","name":"Папка"}]}]}""")
            val owner=MPosWorkspaceNavigationOwner()
            owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos").put("posPath","Кофе").put("search","чай"))
            block(db,owner,MPosWorkspaceNavigationRepository(db,owner))
        }finally{db.close()}
    }
    private fun command(owner:MPosWorkspaceNavigationOwner,id:String="f")=JSONObject().put("version",1).put("operation","prepareRoute")
        .put("route","openFolder").put("value",id).put("expected",owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot"))
    private fun accept(token:String)=JSONObject().put("version",1).put("operation","acceptRoute").put("proposalToken",token)
    @Test fun roomDocumentsOverrideUntrustedInputsAndNavigationDoesNotWriteBusinessData()=runCase {db,owner,repository->
        val documents=db.legacyStorageShadowDao();val beforeProducts=documents.get("products");val beforeNav=documents.get("posNavigation")
        val prepared=repository.execute(command(owner).put("products",JSONArray()).put("navigation",JSONObject()))
        assertTrue(prepared.getBoolean("allowed"));assertEquals("чай",owner.state.value.search)
        val applied=repository.execute(accept(prepared.getString("proposalToken")))
        assertEquals("f",applied.getJSONObject("folderModal").getString("id"));assertEquals("",owner.state.value.search)
        assertEquals(beforeProducts,documents.get("products"));assertEquals(beforeNav,documents.get("posNavigation"))
        assertFalse(repository.execute(command(owner,"invented")).getBoolean("allowed"))
    }
    @Test fun catalogueOrNavigationChangedAfterPreparationRejectsAcceptance()=runCase {db,owner,repository->
        for(key in listOf("products","posNavigation")) {
            val token=repository.execute(command(owner)).getString("proposalToken");val before=owner.state.value
            if(key=="products")MPosCatalogStorage(db).write("""[{"id":"p","category":"Кофе","name":"Changed"}]""")
            else MPosWorkspaceStorage(db).write(key,"""{"version":1,"categories":[]}""")
            try {repository.execute(accept(token));fail("stale route accepted")}catch(_:IllegalStateException){}
            assertEquals(before,owner.state.value)
        }
    }
    @Test fun toolbarUsesCurrentRoomFolderNameWithoutTrustingCallerLabelsOrWritingDocuments()=runCase {db,owner,repository->
        val documents=db.legacyStorageShadowDao()
        val beforeProducts=documents.get("products")
        MPosWorkspaceStorage(db).write("posNavigation","""{"version":1,"categories":[{"category":"Кофе","items":[{"id":"f","type":"folder","name":"Новое имя"}]}]}""")
        val beforeNav=documents.get("posNavigation")
        val expected=owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")
        val result=repository.execute(JSONObject().put("version",1).put("operation","toolbarView").put("expected",expected)
            .put("folderModal",JSONObject().put("category","Кофе").put("id","f").put("name","Подмена")))
        assertEquals("Новое имя",result.getJSONObject("navigation").getString("title"))
        assertEquals(1,result.getJSONObject("navigation").getJSONArray("buttons").length())
        assertEquals(beforeProducts,documents.get("products"));assertEquals(beforeNav,documents.get("posNavigation"))
    }
    @Test fun toolbarTransitionConsumesRevisionAndRejectsDuplicateOrUnsupportedActions()=runCase {db,owner,repository->
        val beforeProducts=db.legacyStorageShadowDao().get("products")
        val beforeNav=db.legacyStorageShadowDao().get("posNavigation")
        val expected=owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")
        val input=JSONObject().put("version",1).put("route","closeCategory").put("expected",expected)
        val accepted=repository.navigateToolbar(input)
        assertTrue(accepted.getBoolean("ok"));assertEquals(JSONObject.NULL,accepted.getJSONObject("patch").get("posPath"))
        assertNull(owner.state.value.posPath)
        val after=owner.state.value
        try{repository.navigateToolbar(input);fail("duplicate toolbar action accepted")}catch(_:IllegalStateException){}
        assertEquals(after,owner.state.value)
        try{repository.navigateToolbar(JSONObject(input.toString()).put("route","deleteFolder"));fail("unsupported toolbar action accepted")}catch(_:IllegalArgumentException){}
        assertEquals(beforeProducts,db.legacyStorageShadowDao().get("products"));assertEquals(beforeNav,db.legacyStorageShadowDao().get("posNavigation"))
    }

    @Test fun typedCategorySelectionPreservesOrderDocumentsAndRejectsOldRevision()=runCase {db,owner,repository->
        val documents=db.legacyStorageShadowDao();val products=documents.get("products");val navigation=documents.get("posNavigation")
        val before=owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot")
        val command=JSONObject().put("version",1).put("route","openCategory").put("value","  Напитки  ").put("expected",before)
        val accepted=repository.navigateToolbar(command)
        assertEquals("Напитки",owner.state.value.posPath);assertEquals("",owner.state.value.search)
        assertEquals("render",accepted.getString("effect"));assertFalse(owner.state.value.editMode)
        assertEquals(products,documents.get("products"));assertEquals(navigation,documents.get("posNavigation"))
        try{repository.navigateToolbar(command);fail("stale category click accepted")}catch(_:IllegalStateException){}
        assertEquals("Напитки",owner.state.value.posPath)
    }

    @Test fun nativeFolderTileChecksCurrentRoomAndReturnsModalWithoutWritingBusinessData()=runCase {db,owner,repository->
        val documents=db.legacyStorageShadowDao();val products=documents.get("products");val navigation=documents.get("posNavigation")
        fun input(id:String)=JSONObject().put("version",1).put("route","openFolder").put("value",id)
            .put("expected",owner.handle(JSONObject().put("version",1).put("operation","read")).getJSONObject("snapshot"))
        val accepted=repository.navigateToolbar(input("f"))
        assertEquals("renderFolder",accepted.getString("effect"));assertEquals("Кофе",accepted.getJSONObject("folderModal").getString("category"))
        assertEquals("f",accepted.getJSONObject("folderModal").getString("id"));assertEquals("Кофе",owner.state.value.posPath)
        assertEquals("",owner.state.value.search);assertEquals(products,documents.get("products"));assertEquals(navigation,documents.get("posNavigation"))
        MPosWorkspaceStorage(db).write("posNavigation","""{"version":1,"categories":[]}""")
        val before=owner.state.value
        try{repository.navigateToolbar(input("f"));fail("deleted folder accepted")}catch(_:IllegalStateException){}
        assertEquals(before,owner.state.value)
        try{repository.navigateToolbar(input("invented"));fail("invented folder accepted")}catch(_:IllegalStateException){}
        assertEquals(before,owner.state.value)
    }

    @Test fun headerViewNeedsOnlyNavigationOwnerAndDoesNotInitializeCatalogAuthority()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val owner=MPosWorkspaceNavigationOwner()
            val snapshot=owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos")).getJSONObject("snapshot")
            val header=MPosWorkspaceNavigationRepository(db,owner).execute(JSONObject().put("version",1).put("operation","headerView").put("expected",snapshot))
            assertEquals(7,header.getJSONObject("navigation").getJSONArray("buttons").length())
            assertNull(db.legacyStorageShadowDao().get("products"));assertNull(db.legacyStorageShadowDao().get(MPosCatalogStorage.AUTHORITY_KEY))
            assertFalse(MPosWorkspaceStorage(db).isAuthoritative("posNavigation"))
        }finally{db.close()}
    }

    @Test fun searchUsesFreshAuthoritativeCatalogAndIgnoresCallerNamesWithoutWritingDocuments()=runCase {db,owner,repository->
        MPosCatalogStorage(db).write("""[{"id":"p","name":"КОФЕ"}]""")
        val documents=db.legacyStorageShadowDao();val before=documents.get("products");val navigation=documents.get("posNavigation")
        val result=repository.execute(search(owner,"кофе").put("products",JSONArray("""[{"id":"p","name":"Подмена"}]""")))
        assertEquals("[true,false]",result.getJSONArray("visible").toString());assertEquals("кофе",owner.state.value.search)
        assertEquals(before,documents.get("products"));assertEquals(navigation,documents.get("posNavigation"))
        MPosCatalogStorage(db).write("""[{"id":"p","name":"ЧАЙ"}]""")
        assertEquals("[false,false]",repository.execute(search(owner,"кофе")).getJSONArray("visible").toString())
        assertEquals("[true,false]",repository.execute(search(owner,"чай")).getJSONArray("visible").toString())
        MPosCatalogStorage(db).remove()
        assertEquals("[false,false]",repository.execute(search(owner,"чай")).getJSONArray("visible").toString())
    }
    @Test fun searchFailurePreservesQueryAndEmptySearchDoesNotInitializeCatalog()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val owner=MPosWorkspaceNavigationOwner();owner.handle(JSONObject().put("version",1).put("operation","initialize").put("tab","pos").put("search","keep"))
            val repository=MPosWorkspaceNavigationRepository(db,owner)
            try {repository.execute(search(owner,"кофе"));fail("unowned catalog accepted")}
            catch(_:IllegalStateException){}
            assertEquals("keep",owner.state.value.search)
            assertEquals("[true,true]",repository.execute(search(owner," \uFEFF ")).getJSONArray("visible").toString())
            assertNull(db.legacyStorageShadowDao().get("products"));assertNull(db.legacyStorageShadowDao().get(MPosCatalogStorage.AUTHORITY_KEY))
        }finally{db.close()}
    }

}
