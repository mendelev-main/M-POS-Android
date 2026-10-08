package com.mendelev.mpos.workspace

import org.json.JSONObject

/** Existing Android Back priority. Category/tab history is deliberately not added. */
object MPosSystemBackPolicy {
    enum class Action { CANCEL_IMPORT, CLOSE_MODAL, CLOSE_WAREHOUSE, FINISH_RECEIVING, BACKGROUND }
    fun choose(input:JSONObject):Action {
        require(input.getInt("version")==1)
        val flags=listOf("pendingImport","modal","warehouse","receiving")
        require(flags.all{input.get(it) is Boolean})
        return when {
            input.getBoolean("pendingImport") -> Action.CANCEL_IMPORT
            input.getBoolean("modal") -> Action.CLOSE_MODAL
            input.getBoolean("warehouse") -> Action.CLOSE_WAREHOUSE
            input.getBoolean("receiving") -> Action.FINISH_RECEIVING
            else -> Action.BACKGROUND
        }
    }
}
