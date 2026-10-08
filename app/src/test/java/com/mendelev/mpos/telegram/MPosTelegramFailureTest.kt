package com.mendelev.mpos.telegram

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosTelegramFailureTest {
    @Test fun rejectionReasonsAreActionableAndNeverEchoResponseOrUrl() {
        for((status,description,expected) in listOf(Triple(401,"secret-token","токен"),Triple(403,"secret-token","запрещено"),Triple(400,"Bad Request: chat not found secret-token","чат"),Triple(400,"message thread not found secret-token","тема"),Triple(429,"secret-token","ограничил"))) {
            val message=MPosTelegramFailure.fromResponse(status,"{\"description\":\"$description\"}").safeMessage
            assertTrue(message.contains(expected));assertFalse(message.contains("secret-token"))
        }
        assertFalse(MPosTelegramFailure.message(Exception("https://api.telegram.org/bot-secret-token")).contains("secret-token"))
    }
}
