package com.mendelev.mpos

import com.mendelev.mpos.telegram.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class TelegramHttpTest {
    private val s=TelegramSettings(botToken="123456:"+"B".repeat(32),chatId="-1001234567890",threadId="12")
    @Test fun multiplePagesUseOneAlbumRequestWithOneCaptionAndOrderedAttachments() {
        val server=MockWebServer();server.start()
        try {
            server.enqueue(MockResponse().setBody("{\"ok\":true,\"result\":[]}"))
            val message=OwnerReportBundle.message("<b>Аналитика</b>",3,{byteArrayOf((it+1).toByte())},{error("Unexpected PDF")})
            TelegramHttpTransport(baseUrl=server.url("/")).send(s,message)
            val request=checkNotNull(server.takeRequest(2,TimeUnit.SECONDS));val body=request.body.readUtf8()
            assertTrue(request.path!!.endsWith("/sendMediaGroup"));assertEquals(1,server.requestCount)
            assertTrue(body.contains("attach://page_0"));assertTrue(body.contains("attach://page_1"));assertTrue(body.contains("attach://page_2"))
            assertEquals(1,Regex("\\\"caption\\\"").findAll(body).count())
            assertTrue(body.indexOf("analytics-1.png")<body.indexOf("analytics-2.png"))
            assertTrue(body.contains("message_thread_id"))
        } finally {server.shutdown()}
    }
    @Test fun reportFormatPreservesEveryPageAndNeverSplitsLargeReportIntoRequests() {
        val rendered=mutableListOf<Int>();var pdfs=0
        fun message(count: Int)=OwnerReportBundle.message("Report",count,{rendered+=it;byteArrayOf(1)},{pdfs++;byteArrayOf(2)})
        assertEquals("image/png",message(1).attachment!!.mediaType)
        rendered.clear();assertEquals(10,message(10).album.size);assertEquals((0..9).toList(),rendered)
        rendered.clear();assertEquals("application/pdf",message(11).attachment!!.mediaType);assertTrue(rendered.isEmpty());assertEquals(1,pdfs)
    }
    @Test fun failedAlbumIsNotRetriedByPageOrAfterDispatcherReopen() {
        val states=mutableMapOf<String,String>();var sends=0;var renders=0
        val journal=object: TelegramDeliveryJournal {override fun state(key: String)=states[key];override fun put(key: String,state: String) {states[key]=state}}
        val transport=TelegramTransport {_,m->sends++;assertEquals(3,m.album.size);throw TelegramFailure("unknown")}
        fun payload()=OwnerReportBundle.message("Report",3,{renders++;byteArrayOf(1)},{error("Unexpected PDF")})
        assertFalse(TelegramDelivery(journal,transport).send("bundle",s) {payload()}.sent)
        assertTrue(TelegramDelivery(journal,transport).send("bundle",s) {payload()}.skipped)
        assertEquals(1,sends);assertEquals(3,renders);assertEquals("unknown",states["bundle"])
    }
    @Test fun textPhotoAndPdfUseExpectedMethodsAndTopic() {
        val server=MockWebServer();server.start()
        try {
            val transport=TelegramHttpTransport(baseUrl=server.url("/"))
            listOf(TelegramMessage("<b>Тест</b>"),TelegramMessage("Чек",TelegramAttachment("shift-report.png","image/png",byteArrayOf(1,2,3))),TelegramMessage("Склад",TelegramAttachment("warehouse.pdf","application/pdf",byteArrayOf(4,5,6)))).forEach {m->
                server.enqueue(MockResponse().setBody("{\"ok\":true}"));transport.send(s,m)
                val request=checkNotNull(server.takeRequest(2,TimeUnit.SECONDS));val body=request.body.readUtf8()
                assertEquals("POST",request.method)
                assertTrue(request.path!!.endsWith(if(m.attachment==null) "/sendMessage" else if(m.attachment.mediaType=="image/png") "/sendPhoto" else "/sendDocument"))
                assertTrue(body.contains("message_thread_id"));assertTrue(body.contains("12"));assertTrue(body.contains("parse_mode"));assertTrue(body.contains("HTML"))
            }
            assertEquals(3,server.requestCount)
        } finally {server.shutdown()}
    }
    @Test fun redirectIsNotFollowedAndTelegramErrorIsSanitized() {
        val server=MockWebServer();server.start()
        try {
            val transport=TelegramHttpTransport(baseUrl=server.url("/"))
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","https://api.telegram.org/"))
            try {transport.send(s,TelegramMessage("test"));fail()} catch(e:TelegramFailure) {assertFalse(e.message!!.contains(s.botToken))}
            assertEquals(1,server.requestCount)
            server.enqueue(MockResponse().setResponseCode(429).setBody("{\"ok\":false,\"error_code\":429,\"description\":\"${s.botToken}\",\"parameters\":{\"retry_after\":30}}"))
            try {transport.send(s,TelegramMessage("test"));fail()} catch(e:TelegramFailure) {assertTrue(e.safeMessage.contains("30"));assertFalse(e.safeMessage.contains(s.botToken))}
            assertEquals(2,server.requestCount)
        } finally {server.shutdown()}
    }
}
