package com.mendelev.mpos.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosBridgeJsonTest {
    @Test fun emojiAndLegacyLoneSurrogatesSurviveAsciiSafeJsonTransport(){
        val text="Кофе \\uD83D\\uDE00".replace("\\uD83D\\uDE00","\uD83D\uDE00")+"\uD83D"+" end "+"\uDE00"
        val input=JSONObject().put("name",text).put("nested",JSONObject().put("quote","\"\\\n"))
        val encoded=MPosBridgeJson.serialize(input)
        assertFalse(encoded.any{it.code in 0xD800..0xDFFF})
        assertEquals(text,JSONObject(encoded).getString("name"))
        assertEquals(input.getJSONObject("nested").getString("quote"),JSONObject(encoded).getJSONObject("nested").getString("quote"))
    }
}
