package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.report.*
import org.json.JSONObject

/** Selected events only, all quantities and names from original receipts, no live catalogue reads. */
internal fun consumptionReport(events: List<ReportEvent>,snapshots: Map<String,ReceiptSnapshot>): List<ConsumptionRow> {
    val facts=events.map {it.receiptId}.distinct().associateWith {id->
        val snapshot=snapshots.getValue(id)
        val metadata=snapshot.products.map {JSONObject(it)}
        require(metadata.map {it.getString("id")}.distinct().size==metadata.size)
        ConsumptionReports.usage(snapshot.cart,metadata.associate {it.getString("id") to it.getString("name")})
    }
    return ConsumptionReports.build(events,facts)
}
