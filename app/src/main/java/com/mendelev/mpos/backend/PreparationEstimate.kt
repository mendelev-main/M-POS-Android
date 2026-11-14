package com.mendelev.mpos.backend

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Converts a preparation duration at submission time to the existing Web accept protocol. */
internal object PreparationEstimate {
    fun encode(minutes: Int?,now: ZonedDateTime=ZonedDateTime.now()): String? {
        if(minutes==null || minutes !in 1..1440) return null
        return if(minutes in listOf(5,15,30,40)) "${minutes}m"
        else "at:"+now.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}
