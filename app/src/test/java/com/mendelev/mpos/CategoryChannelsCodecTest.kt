package com.mendelev.mpos
import com.mendelev.mpos.data.local.*
import org.junit.Assert.*
import org.junit.Test
class CategoryChannelsCodecTest {
    @Test fun roundTripAndStrictFlags() {
        val value=CategoryChannels(false,true)
        assertEquals(value,CategoryChannelsCodec.decode(CategoryChannelsCodec.encode("c",value),"c"))
        listOf("""{"version":1,"categoryId":"c","menu":"false","order":true}""",
            """{"version":1,"categoryId":"other","menu":false,"order":true}""").forEach {raw->
            try {CategoryChannelsCodec.decode(raw,"c");fail("Invalid flags accepted")} catch(_:IllegalArgumentException) {}
        }
    }
}
