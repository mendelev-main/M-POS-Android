package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.MPosNavigationEngine
import org.json.JSONArray
import org.json.JSONObject

/** Native workspace navigation labels/actions, independent of HTML labels or mounted buttons. */
object MPosWorkspaceToolbarModel {
    fun calculate(snapshot:JSONObject,folderModal:JSONObject?,navigation:Any?):JSONObject {
        val path=snapshot.opt("posPath") as? String
        check(folderModal==null||(folderModal.opt("category")==path&&folderModal.opt("id") is String)){"workspace folder context changed"}
        val modal=folderModal
        val folderId=modal?.getString("id")?:snapshot.optString("posFolder")
        val categories=MPosNavigationEngine.normalize(navigation).getJSONArray("categories")
        val items=(0 until categories.length()).map{categories.getJSONObject(it)}.firstOrNull{it.opt("category")==path}?.getJSONArray("items")?:JSONArray()
        val folder=(0 until items.length()).map{items.getJSONObject(it)}.firstOrNull{it.opt("type")=="folder"&&it.opt("id")==folderId}
        check(modal==null||folder!=null){"workspace folder changed"}
        val folderName=folder?.getString("name")
        val title=if(modal!=null)folderName!! else if(!path.isNullOrEmpty())path+(if(folderName!=null)" / $folderName" else "") else "Рабочая зона"
        val buttons=JSONArray()
        if(modal!=null)buttons.put(JSONObject().put("label","Закрыть папку").put("operation","closeCategory"))
        else {
            if(!path.isNullOrEmpty())buttons.put(JSONObject().put("label","← Назад").put("operation","closeCategory"))
            buttons.put(JSONObject().put("label",if(snapshot.getBoolean("editMode"))"Готово" else "Раскладка").put("operation","toggleEdit"))
        }
        return JSONObject().put("title",title).put("buttons",buttons).put("expected",JSONObject(snapshot.toString()))
            .put("folderModal",modal?.let{JSONObject(it.toString())}?:JSONObject.NULL)
    }
}
