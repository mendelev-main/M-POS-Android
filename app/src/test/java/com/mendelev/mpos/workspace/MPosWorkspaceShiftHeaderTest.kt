package com.mendelev.mpos.workspace

import androidx.room.Room
import com.mendelev.mpos.data.MPosDatabase
import com.mendelev.mpos.data.MPosShiftStorage
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
class MPosWorkspaceShiftHeaderTest {
    private fun command(operation:String)=JSONObject().put("version",1).put("operation",operation)
    private fun snapshot(owner:MPosWorkspaceNavigationOwner)=owner.handle(command("read")).getJSONObject("snapshot")
    private fun runCase(block:suspend(MPosDatabase,MPosWorkspaceNavigationOwner,MPosWorkspaceNavigationRepository)->Unit)=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),MPosDatabase::class.java).build()
        try {
            val owner=MPosWorkspaceNavigationOwner();owner.handle(command("initialize").put("tab","pos").put("search","чай").put("posPath","Кофе"))
            block(db,owner,MPosWorkspaceNavigationRepository(db,owner))
        }finally{db.close()}
    }
    private suspend fun view(owner:MPosWorkspaceNavigationOwner,repository:MPosWorkspaceNavigationRepository)=repository.execute(command("shiftHeaderView").put("expected",snapshot(owner))).getJSONObject("navigation")
    private fun select(model:JSONObject)=command("selectShiftHeader").put("expected",model.getJSONObject("expected")).put("shiftRevision",model.getJSONObject("shift").getString("revision"))
    @Test fun closedShiftLaunchesOpeningWithoutMutatingNavigationOrDocuments()=runCase {db,owner,repository->
        MPosShiftStorage(db).initialize("""[{"id":"old","status":"closed","countedCash":80,"extension":1}]""")
        val before=db.legacyStorageShadowDao().get("shifts");val state=owner.state.value
        val model=view(owner,repository);assertFalse(model.getJSONObject("shift").getBoolean("open"));assertEquals("Открыть смену",model.getJSONObject("shift").getString("label"))
        assertEquals("openShift",repository.execute(select(model)).getString("effect"));assertEquals(state,owner.state.value)
        assertEquals(before,db.legacyStorageShadowDao().get("shifts"));assertNull(db.legacyStorageShadowDao().get("products"))
    }
    @Test fun firstSavedOpenShiftSuppliesLabelAndSelectionKeepsCartContextAndCanBeDiscarded()=runCase {db,owner,repository->
        MPosShiftStorage(db).initialize("""[{"id":"closed","status":"closed"},{"id":"first","status":"open","employeeName":"  Иванов   Иван Иванович Другое "},{"id":"second","status":"open","employeeName":"Другой"}]""")
        val before=db.legacyStorageShadowDao().get("shifts");val model=view(owner,repository)
        assertEquals("Иванов И.И.",model.getJSONObject("shift").getString("label"))
        val result=repository.execute(select(model).put("label","Подмена"));assertEquals("render",result.getString("effect"));assertEquals("shift",owner.state.value.tab)
        assertEquals("чай",owner.state.value.search);assertEquals("Кофе",owner.state.value.posPath)
        repository.execute(command("discardHeaderTab").put("headerToken",result.getString("headerToken")));assertEquals("pos",owner.state.value.tab)
        assertEquals(before,db.legacyStorageShadowDao().get("shifts"))
    }
    @Test fun changedSavedShiftOrNavigationRejectsOldButtonBeforeAnyTransition()=runCase {db,owner,repository->
        MPosShiftStorage(db).initialize("[]");val model=view(owner,repository)
        MPosShiftStorage(db).write("""[{"id":"new","status":"open","employeeName":"Новый"}]""")
        try{repository.execute(select(model));fail("old closed shift button accepted")}catch(_:IllegalStateException){}
        assertEquals("pos",owner.state.value.tab)
        val current=view(owner,repository);owner.handle(command("selectSearch").put("search","кофе"))
        try{repository.execute(select(current));fail("old navigation accepted")}catch(_:IllegalStateException){}
        assertEquals("pos",owner.state.value.tab);assertEquals("кофе",owner.state.value.search)
    }
    @Test fun missingAuthorityCannotCreateAnEmptyShiftOrOpenDialog()=runCase {db,owner,repository->
        val state=owner.state.value
        try{view(owner,repository);fail("unowned shifts accepted")}catch(_:IllegalStateException){}
        assertEquals(state,owner.state.value);assertNull(db.legacyStorageShadowDao().get("shifts"));assertNull(db.legacyStorageShadowDao().get(MPosShiftStorage.AUTHORITY_KEY))
    }
    @Test fun shortNamePreservesReviewedWhitespaceInitialsAndMissingName() {
        assertEquals("Сотрудник",MPosWorkspaceShiftHeaderModel.shortName(JSONObject.NULL))
        assertEquals("Петров П.И.",MPosWorkspaceShiftHeaderModel.shortName("\uFEFFПетров\u00a0пётр\tиванович лишнее"))
        assertEquals("Один",MPosWorkspaceShiftHeaderModel.shortName("Один"))
        assertEquals("Фамилия SS.",MPosWorkspaceShiftHeaderModel.shortName("Фамилия ß"))
    }
}
