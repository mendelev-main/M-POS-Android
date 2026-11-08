package com.mendelev.mpos.domain.backup

import org.junit.Assert.*
import org.junit.Test

class WebSupplierMappingTest {
    private fun source()=WebBackupReader.read("""{
        "version":1,"products":[
          {"id":1,"type":"simple","name":"Сырьё","price":2,"cost":1,"stock":10,"stockUnit":"piece"},
          {"id":"recipe","type":"composite","name":"Рецепт","price":3,"recipeYield":1,"components":[{"productId":1,"qty":1}]}
        ],"employees":[],"shifts":[],"orders":[],
        "suppliers":[{"id":"s","name":"Поставщик","productIds":[1,"1","deleted","recipe"]},
          {"id":"empty","name":"Архивный поставщик","productIds":["deleted"]}]
    }""".byteInputStream())
    @Test fun staleLinksDoNotDiscardSupplierOrValidLinksAndSourceSurvives() {
        val source=source();val suppliers=WebSupplierMapping.map(source)
        assertEquals(2,suppliers.size)
        assertEquals(setOf(WebIdentity.mapped("product","1")),suppliers.first().productIds)
        assertEquals(WebIdentity.mapped("supplier","s"),suppliers.first().id)
        assertEquals("Поставщик",suppliers.first().name)
        assertTrue(suppliers.last().productIds.isEmpty())
        val retained=WebSourceDocuments.decode(WebSourceDocuments.encode(source))!!
        assertArrayEquals(source.original,retained.original)
        assertEquals(suppliers,WebSupplierMapping.map(retained))
        assertEquals(4,WebValues.rows(WebValues.obj(WebValues.rows(retained.root.fields["suppliers"]).first())["productIds"]).size)
    }
}
