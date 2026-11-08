package com.mendelev.mpos

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mendelev.mpos.core.ui.MposTheme
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.feature.products.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CatalogAppearanceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun searchClearCategoryCountAndProductOpenMatchTheCatalog() {
        val rows=listOf(ProductRow(ProductEntity("tea","drinks","Ёлка 2","елка 2","2","1",1),"3",1,"Напитки"),ProductRow(ProductEntity("cake","desserts","Пирог","пирог","5","2",1),"2",1,"Десерты"))
        var query by mutableStateOf("");var filter by mutableStateOf<String?>(null);var opened: String?=null
        compose.setContent {MposTheme {Box(Modifier.width(1280.dp).height(850.dp)) {CatalogPage(CatalogUi(loading=false,products=rows),query,{query=it},filter,{filter=it},true,{},{},{opened=it.product.id})}}}
        compose.onNodeWithTag("catalogCount").assertTextEquals("2")
        compose.onNodeWithTag("catalogSearch").performTextInput("елка")
        compose.onNodeWithTag("catalogProduct:cake").assertDoesNotExist()
        compose.onNodeWithTag("catalogProduct:tea").performScrollTo().performClick()
        assertEquals("tea",opened)
        compose.onNodeWithTag("catalogSearchClear").performClick()
        compose.onNodeWithTag("catalogCategory:category:desserts").performClick()
        compose.onNodeWithTag("catalogProduct:tea").assertDoesNotExist()
        compose.onNodeWithTag("catalogProduct:cake").assertExists()
        compose.onNodeWithTag("catalogCount").assertTextEquals("2")
    }
    @Test fun pickerSearchAndCancelDoNotModifyTheEditor() {
        val rows=listOf(ProductRow(ProductEntity("tea",null,"Ёлка","елка","2","1",1),"3",1,null))
        var picked: String?=null
        compose.setContent {MposTheme {ProductPickerField("Ингредиент","Выбрать товар",rows,true,"editor","pickerOpen") {picked=it.product.id}}}
        compose.onNodeWithTag("pickerOpen").performClick()
        compose.onNodeWithTag("productPickerSearch").performTextInput("елка")
        compose.onNodeWithTag("productPicker:tea").assertExists()
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        assertNull(picked)
        compose.onNodeWithTag("pickerOpen").performClick()
        compose.onNodeWithTag("productPicker:tea").performScrollTo().performClick()
        assertEquals("tea",picked)
    }

}
