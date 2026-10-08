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
}
