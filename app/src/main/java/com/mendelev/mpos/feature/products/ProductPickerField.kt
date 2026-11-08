package com.mendelev.mpos.feature.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.ProductRow
import com.mendelev.mpos.domain.catalog.CatalogPresentationRules
import com.mendelev.mpos.domain.catalog.unitLabel

/** Picker shared by recipe and modifier fields; selection only changes the owning editor draft. */
@Composable internal fun ProductPickerField(label: String,value: String,choices: List<ProductRow>,enabled: Boolean,session: String,tag: String,onPick: (ProductRow)->Unit) {
    var opened by rememberSaveable(session,tag) {mutableStateOf(false)}
    var query by rememberSaveable(session,tag) {mutableStateOf("")}
    Column(Modifier.fillMaxWidth()) {
        MposWebLabel(label)
        Surface(onClick={query="";opened=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
            Row(Modifier.padding(horizontal=13.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(value,Modifier.weight(1f),fontSize=15.sp,lineHeight=20.sp)
                com.mendelev.mpos.feature.workspace.PosIcon("search",MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(opened) MposWebModal("Выберите товар",{opened=false},enabled,wide=true) {
        Text("Найдите товар, который будет использоваться в составе или как модификатор.",Modifier.padding(bottom=14.dp),fontSize=13.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        MposWebField(query,{query=it},"Поиск","productPickerSearch",enabled,placeholder="Название товара")
        val visible=remember(choices,query) {choices.filter {CatalogPresentationRules.matches(it.product.name,it.categoryName ?: "Без категории",query)}.sortedWith {a,b->CatalogPresentationRules.compareNames(a.product.name,b.product.name)}}
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp).testTag("productPickerList"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(visible.isEmpty()) item {Text("Товары не найдены.",Modifier.padding(vertical=20.dp),fontSize=13.sp)}
            items(visible,key={it.product.id}) {row->
                Surface(onClick={opened=false;onPick(row)},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=58.dp).testTag("productPicker:${row.product.id}"),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(horizontal=14.dp,vertical=11.dp)) {
                        Text(row.product.name,fontSize=14.sp,lineHeight=19.sp,fontWeight=FontWeight.Bold)
                        Text("${row.categoryName ?: "Без категории"} · ${unitLabel(row.product.unit)}",fontSize=11.sp,lineHeight=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        MposPosControl("Отмена",{opened=false},Modifier.fillMaxWidth(),enabled,PosControl.Secondary,height=48.dp,radius=16.dp)
    }
}
