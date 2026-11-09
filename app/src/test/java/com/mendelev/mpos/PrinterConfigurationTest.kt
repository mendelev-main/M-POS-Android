package com.mendelev.mpos

import com.mendelev.mpos.data.local.PrinterConfiguration
import org.junit.Assert.*
import org.junit.Test

class PrinterConfigurationTest {
    @Test fun trimsAddressAndRetainsReceiptConfiguration() {
        val p=PrinterConfiguration(name=" Касса ",ip=" 192.168.1.100 ",phrases="Спасибо!\nДо встречи",printOrders=true).validated()
        assertEquals("Касса",p.name);assertEquals("192.168.1.100",p.ip)
        assertTrue(p.printOrders);assertEquals("Спасибо!\nДо встречи",p.phrases)
    }
    @Test fun rejectsInvalidAddressCopiesAndCommands() {
        val valid=PrinterConfiguration(name="Касса",ip="192.168.1.10")
        listOf(valid.copy(ip="256.1.1.1"),valid.copy(ip="1.2.3"),valid.copy(ip="host"),valid.copy(name=" "),valid.copy(copies="0"),valid.copy(copies="11"),valid.copy(initCommand="ZZ"),valid.copy(paperWidth="72")).forEach {
            try {it.validated();fail("Expected invalid configuration: $it")} catch(_:IllegalArgumentException) {}
        }
    }
}
