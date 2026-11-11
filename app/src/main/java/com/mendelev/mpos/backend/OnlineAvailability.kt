package com.mendelev.mpos.backend

import java.math.BigDecimal
import org.json.JSONObject

/** Existing API: JSON null means unlimited web stock, never an invented large quantity. */
internal object OnlineAvailability {
    fun item(externalId: String,alwaysAvailable: Boolean,physical: ()->BigDecimal?): JSONObject {
        val quantity=if(alwaysAvailable) null else physical()?.max(BigDecimal.ZERO)
        return JSONObject().put("externalId",externalId).put("quantity",quantity ?: JSONObject.NULL)
    }
}
