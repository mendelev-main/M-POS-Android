package com.mendelev.mpos.print

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosPrintTransportTest {
    @Test fun realTcpWritesExactExistingTestBytesAndClosedTransportCannotSend(){
        ServerSocket(0).use{server->
            val received=CompletableFuture<ByteArray>();val reader=Thread{runCatching{server.accept().use{socket->socket.soTimeout=5000;socket.getInputStream().readBytes()}}.onSuccess{received.complete(it)}.onFailure{received.completeExceptionally(it)}};reader.start()
            val printer=EscPosPrinter({});val order=JSONObject().put("__networkPrinterIp","127.0.0.1").put("__networkPrinterPort",server.localPort).put("__networkTest",true)
            try{printer.send(order);val bytes=received.get(5,TimeUnit.SECONDS);assertArrayEquals(byteArrayOf(0x1b,0x40)+"\nM POS\nTEST PRINT\nLAN TCP 9100 OK\n\n\n".toByteArray()+byteArrayOf(0x1d,0x56,0x42,0),bytes);printer.close();try{printer.send(order);fail("closed transport sent")}catch(_:IllegalStateException){}}finally{printer.close();reader.join(5000)}
        }
    }
}
