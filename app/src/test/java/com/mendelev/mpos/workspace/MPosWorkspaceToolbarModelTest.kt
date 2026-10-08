package com.mendelev.mpos.workspace

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosWorkspaceToolbarModelTest {
    private fun snapshot(path:String?=null,folder:String="",edit:Boolean=false)=JSONObject().put("tab","pos")
        .put("posPath",path?:JSONObject.NULL).put("posFolder",folder).put("search","чай").put("editMode",edit).put("revision",3)
    private val navigation=JSONObject("""{"version":1,"categories":[{"category":"Кофе","items":[{"type":"folder","id":"f","name":"  Напитки  "}]}]}""")
    @Test fun rootCategoryAndEditingLabelsComeFromNativeStateAndPreserveSourceHierarchy() {
        val root=MPosWorkspaceToolbarModel.calculate(snapshot(),null,null)
        assertEquals("Рабочая зона",root.getString("title"));assertEquals(1,root.getJSONArray("buttons").length())
        assertEquals("Раскладка",root.getJSONArray("buttons").getJSONObject(0).getString("label"))
        val category=MPosWorkspaceToolbarModel.calculate(snapshot("Кофе","f",true),null,navigation)
        assertEquals("Кофе / Напитки",category.getString("title"));assertEquals("closeCategory",category.getJSONArray("buttons").getJSONObject(0).getString("operation"))
        assertEquals("Готово",category.getJSONArray("buttons").getJSONObject(1).getString("label"))
    }
    @Test fun folderUsesNormalizedRoomNameAndOnlyItsCloseAction() {
        val modal=JSONObject().put("category","Кофе").put("id","f")
        val source=snapshot("Кофе")
        val model=MPosWorkspaceToolbarModel.calculate(source,modal,navigation)
        assertEquals("Напитки",model.getString("title"));assertEquals(1,model.getJSONArray("buttons").length())
        assertEquals("Закрыть папку",model.getJSONArray("buttons").getJSONObject(0).getString("label"))
        model.getJSONObject("expected").put("search","changed");assertEquals("чай",source.getString("search"))
        assertThrows(IllegalStateException::class.java){MPosWorkspaceToolbarModel.calculate(snapshot("Кофе"),modal,JSONObject())}
    }
}
