package com.mendelev.mpos

import com.mendelev.mpos.data.local.WorkspaceCodec
import com.mendelev.mpos.domain.workspace.CurrentCart
import com.mendelev.mpos.domain.workspace.OrderDetails
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReportFulfillmentCodecTest {
    @Test fun absentHistoricalTypeDefaultsHereButUnknownIsNotSilentlyReclassified() {
        val original=CurrentCart(sessionId="historical",details=OrderDetails(label="Заказ",type="takeaway"))
        val json=JSONObject(WorkspaceCodec.cart(original))
        json.getJSONObject("details").remove("type")
        val raw=json.toString()
        val restored=WorkspaceCodec.cart(raw)
        assertEquals("here",restored.details.type)
        assertEquals("Заказ",restored.details.label)
        assertEquals(raw,json.toString())
        json.getJSONObject("details").put("type","unknown")
        assertThrows(Exception::class.java) {WorkspaceCodec.cart(json.toString())}
    }
}
