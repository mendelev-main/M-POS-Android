package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.MPosJsonNumbers
import com.mendelev.mpos.data.MPosNavigationEngine
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONObject

/** Ephemeral workspace transitions. Does not touch cart, shift, stock or persisted layout. */
object MPosWorkspaceRouteEngine {
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val state = input.getJSONObject("state")
        val patch = JSONObject()
        val result = JSONObject().put("ok", true).put("authoritative", true)
            .put("source", "native-workspace-route").put("allowed", true).put("patch", patch)
        fun effect(name: String) = result.put("effect", name)
        when (input.getString("operation")) {
            "openCategory" -> {
                val value = MPosJsonNumbers.fallback(input.opt("value"))
                require(value is String) // Unusual legacy String(...) coercions retain reviewed fallback.
                val name = MPosOrderContextEngine.trim(value)
                if (name.isEmpty()) return result.put("allowed", false)
                patch.put("posFolder", "").put("posPath", name).put("search", "").put("editMode", false)
                effect("render")
            }
            "closeCategory" -> {
                if (MPosJsonNumbers.truthy(input.opt("folderModal"))) effect("closeModal")
                else if (MPosJsonNumbers.truthy(state.opt("posFolder"))) {
                    patch.put("posFolder", "").put("search", "")
                    effect("render")
                } else {
                    patch.put("posPath", JSONObject.NULL).put("search", "").put("editMode", false)
                    effect("render")
                }
            }
            "toggleEdit" -> {
                patch.put("editMode", !MPosJsonNumbers.truthy(state.opt("editMode"))).put("search", "")
                effect("render")
                result.put("setupDrag", patch.getBoolean("editMode"))
            }
            "openFolder" -> {
                val category = state.opt("posPath") as? String ?: return result.put("allowed", false)
                if (!MPosNavigationEngine.hasFolder(category, input.opt("navigation"), input.getJSONArray("products"), input.opt("value")))
                    return result.put("allowed", false)
                patch.put("posFolder", "").put("search", "")
                result.put("folderModal", JSONObject().put("category", category).put("id", input.get("value")))
                effect("renderFolder")
            }
            else -> throw IllegalArgumentException("unknown workspace transition")
        }
        return result
    }
}
