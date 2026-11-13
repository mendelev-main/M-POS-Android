package com.mendelev.mpos.domain.report

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min

/** One long PDF page, uniformly scaled to common PDF viewer limits, never cropped or paginated. */
data class ReportExportLayout(val width: Int,val height: Int,val scale: Float) {
    companion object {
        const val MAX_DIMENSION=14_400
        fun fit(contentWidth: Int,contentHeight: Int): ReportExportLayout {
            require(contentWidth>0 && contentHeight>0)
            val scale=min(1.0,MAX_DIMENSION.toDouble()/maxOf(contentWidth,contentHeight)).toFloat()
            return ReportExportLayout(ceil(contentWidth*scale.toDouble()).toInt().coerceIn(1,MAX_DIMENSION),
                ceil(contentHeight*scale.toDouble()).toInt().coerceIn(1,MAX_DIMENSION),scale)
        }
    }
}
object ReportPeriodSelection {
    fun preset(from: String,through: String,today: LocalDate): Long?=runCatching {
        val end=LocalDate.parse(through);val start=LocalDate.parse(from)
        if(end!=today) null else listOf(1L,7L,30L).firstOrNull {end.minusDays(it-1)==start}
    }.getOrNull()
}
