package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import org.junit.Assert.*
import org.junit.Test

class WebCartAdoptionTest {
    private fun source()=WebBackupReader.read("""{
      "version":1,"products":[
        {"id":"raw","name":"Сырьё","type":"simple","stockUnit":"kg","price":1,"cost":1,"stock":10},
        {"id":"meal","name":"Рецепт","type":"composite","recipeUnit":"piece","recipeYield":1,"price":2,
         "components":[{"productId":"raw","qty":1.333333333333}]}
      ],"employees":[],"shifts":[],"orders":[],
      "parked":[{"id":"order","createdAt":1,"timestamp":2,"label":"Заказ","orderType":"На месте",
       "items":[{"productId":"meal","name":"Рецепт","qty":0.0000001,"price":2},{"productId":"meal","name":"Рецепт","qty":0.0000001,"price":2}]}]
    }""".byteInputStream())
    private fun products(source: WebBackupDocument)=WebCatalogMapping.map(source).products.map {p->
        ProductEntity(p.id,p.categoryId,p.name,p.name,p.price,p.cost ?: "0",1,unit=p.unit!!,type=p.type,
            displayUnit=p.displayUnit!!,recipeYield=p.recipeYield)
    }
    @Test fun adoptionUsesSameSourceToleranceWithoutRelaxingNativeSnapshotOrMoney() {
        val source=source();val bound=WebRecipeBinding.bind(source).carts.single();val products=products(source)
        assertNotEquals(bound.exactSourceConsumption,bound.nativeConsumption)
        val adopted=WebCartAdoption.map(bound,products,source)
        assertEquals(bound.operation.comparedTotal,adopted.quote.total)
        assertEquals(bound.nativeConsumption,com.mendelev.mpos.domain.workspace.CartEngine.reserved(adopted.cart)
            .mapValues {com.mendelev.mpos.domain.catalog.CatalogRules.canonical(it.value)})
        val key=bound.nativeConsumption!!.keys.single()
        assertThrows(BackupFailure::class.java) {WebCartAdoption.map(bound.copy(exactSourceConsumption=mapOf(key to "0.01")),products,source)}
        assertThrows(BackupFailure::class.java) {WebCartAdoption.map(bound.copy(nativeConsumption=mapOf(key to "0.01")),products,source)}
        assertThrows(BackupFailure::class.java) {WebCartAdoption.map(bound.copy(operation=bound.operation.copy(comparedTotal=123)),products,source)}
    }
}
