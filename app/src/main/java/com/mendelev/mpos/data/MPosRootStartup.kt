package com.mendelev.mpos.data

import org.json.JSONObject

/** Root ordering only. Recovery and domain hydration adapters remain replaceable by native commands. */
class MPosRootStartup {
    private var generation = 0L
    private var step = "idle"

    fun execute(input: JSONObject): JSONObject {
        if (input.getString("operation") == "begin") {
            generation++
            step = "recover"
        } else {
            require(input.getString("operation") == "advance")
            check(input.getLong("generation") == generation && input.getString("completed") == step)
            step = when (step) {
                "recover" -> "hydrate"
                "hydrate" -> "activate"
                "activate" -> "ready"
                else -> error("root startup already finished")
            }
        }
        return JSONObject().put("generation", generation).put("step", step)
    }
}
