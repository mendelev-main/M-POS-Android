package com.mendelev.mpos.feature.purchasing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*

@Composable internal fun PurchaseAction(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,style: ActionStyle=ActionStyle.Primary,busy: Boolean=false) {
    MposPosControl(label,onClick,modifier,enabled && !busy,when(style) {
        ActionStyle.Primary->PosControl.Cash
        ActionStyle.Danger->PosControl.Delete
        else->PosControl.Outline
    },height=54.dp,radius=14.dp)
}
@Composable internal fun PurchaseNumber(value: String,onChange: (String)->Unit,label: String,modifier: Modifier=Modifier,enabled: Boolean=true,numeric: Boolean=true,productName: String="",editing: Boolean=false) {
    var open by rememberSaveable {mutableStateOf(false)}
    var raw by rememberSaveable {mutableStateOf("")}
    var replace by rememberSaveable {mutableStateOf(true)}
    Column(modifier) {
        Text(label,fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(onClick={raw=value;replace=true;open=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp),shape=RoundedCornerShape(10.dp),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
            Text(value.ifBlank {"0"}.replace('.',','),Modifier.padding(10.dp),fontSize=14.sp)
        }
    }
    if(open) MposDialog(if(label=="Количество") "Заказать" else label,{open=false},maxWidth=320.dp) {
        if(productName.isNotBlank()) Text(productName,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background) {
            Text(raw.ifBlank {"0"}.replace('.',','),Modifier.fillMaxWidth().heightIn(min=64.dp).padding(16.dp).testTag("purchaseKeypadValue"),fontSize=28.sp,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.End)
        }
        listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf(",","0","⌫")).forEach {row->
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach {key->MposPosControl(key,{
                    raw=when(key) {
                        "⌫"->raw.dropLast(1)
                        ","->if(replace) "0," else if(raw.contains(',') || raw.contains('.')) raw else raw.ifBlank {"0"}+","
                        else->if(replace || raw=="0") key else if(raw.length<12) raw+key else raw
                    };replace=false
                },Modifier.weight(1f).testTag("purchaseKey:$key"),height=54.dp,radius=12.dp,fontSize=23f)}
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(editing) MposPosControl("Отмена",{open=false},Modifier.weight(1f),kind=PosControl.Secondary)
            MposPosControl("Очистить",{raw="";replace=false},Modifier.weight(1f),kind=PosControl.Outline)
            MposPosControl("Готово",{onChange(raw);open=false},Modifier.weight(1f).testTag("purchaseKeypadDone"),kind=PosControl.Cash)
        }
    }
}

@Composable internal fun PurchaseRequestRow(product: com.mendelev.mpos.domain.warehouse.PurchaseProduct,line: com.mendelev.mpos.domain.warehouse.PurchaseLine,enabled: Boolean,prefix: String="purchase",onActivate: ()->Unit={},onChange: (com.mendelev.mpos.domain.warehouse.PurchaseLine)->Unit) {
    val included=com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.included(line)
    fun include(next: com.mendelev.mpos.domain.warehouse.PurchaseLine)=onChange(com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.activate(next))
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if(included) {if(isMposDarkTheme()) Color(0xFF254237) else Color(0xFFD9EEE3)} else Color.Transparent)
        .clickable(enabled=enabled) {
            onChange(if(included) line.copy(quantity="0") else com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.activate(line))
        }.padding(horizontal=10.dp,vertical=10.dp)) {
        val wide=maxWidth>=820.dp
        @Composable fun productName(modifier: Modifier) {
            Column(modifier) {
                Text(line.productName,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Text("Остаток: ${product.stock.replace('.',',')} ${com.mendelev.mpos.domain.catalog.unitLabel(product.unit)}",Modifier.padding(top=5.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        @Composable fun fields(modifier: Modifier) {
            Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposSupplyField("Количество",line.quantity,{onChange(line.copy(quantity=it))},if(wide) Modifier.width(110.dp) else Modifier.weight(1f),enabled,tag="${prefix}Qty:${product.id}",included=false,onTouch=onActivate)
                    PurchaseSelect("Единица заказа",line.unit,units(product.unit)+com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.packs.map {it to com.mendelev.mpos.domain.warehouse.purchaseUnitLabel(it)},{include(line.copy(unit=it))},enabled,(if(wide) Modifier.width(130.dp) else Modifier.weight(1f)).testTag("${prefix}Unit:${product.id}"),included=false,onTouch=onActivate)
                    if(line.unit in com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.packs && wide) {
                        MposSupplyField("В единице",line.packSize,{include(line.copy(packSize=it))},Modifier.width(110.dp),enabled,tag="${prefix}Pack:${product.id}",included=false,onTouch=onActivate)
                        PurchaseSelect("Единица внутри",line.contentUnit,units(product.unit),{include(line.copy(contentUnit=it))},enabled,Modifier.width(120.dp).testTag("${prefix}Content:${product.id}"),included=false,onTouch=onActivate)
                    }
                }
                if(line.unit in com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.packs && !wide) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposSupplyField("В единице",line.packSize,{include(line.copy(packSize=it))},Modifier.weight(1f),enabled,included=false,onTouch=onActivate)
                    PurchaseSelect("Единица внутри",line.contentUnit,units(product.unit),{include(line.copy(contentUnit=it))},enabled,Modifier.weight(1f).testTag("${prefix}Content:${product.id}"),included=false,onTouch=onActivate)
                }
            }
        }
        if(wide) Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            productName(Modifier.weight(1f));fields(Modifier.weight(2.7f))
            Column(Modifier.width(92.dp),horizontalAlignment=androidx.compose.ui.Alignment.End) {
                Text("Поступит",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(runCatching {com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.expected(line)?.let {com.mendelev.mpos.domain.catalog.CatalogRules.canonical(it).replace('.',',')} ?: "—"}.getOrDefault("—")+" "+com.mendelev.mpos.domain.catalog.unitLabel(line.stockUnit),fontSize=14.sp,fontWeight=FontWeight.Bold)
            }
        }
        else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {productName(Modifier.fillMaxWidth());fields(Modifier.fillMaxWidth())}
    }
    HorizontalDivider()
}
@Composable internal fun PurchaseSelect(label: String,value: String,options: List<Pair<String,String>>,onChange: (String)->Unit,enabled: Boolean,modifier: Modifier,included: Boolean=false,onTouch: ()->Unit={}) {
    var open by remember {mutableStateOf(false)}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,fontSize=12.sp,lineHeight=16.sp,maxLines=1,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Surface(onClick={onTouch();open=true},enabled=enabled,modifier=Modifier.fillMaxWidth().height(44.dp),shape=RoundedCornerShape(10.dp),color=if(included) {if(isMposDarkTheme()) androidx.compose.ui.graphics.Color(0xFF254237) else androidx.compose.ui.graphics.Color(0xFFD9EEE3)} else MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,if(included) MaterialTheme.colorScheme.primary.copy(alpha=.5f) else MaterialTheme.colorScheme.outline)) {
                Row(Modifier.fillMaxSize().padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text(options.find {it.first==value}?.second ?: value,Modifier.weight(1f),fontSize=14.sp,lineHeight=20.sp,maxLines=1)
                }
            }
            }
            DropdownMenu(open,{open=false},shape=RoundedCornerShape(12.dp)) {options.forEach {(id,name)->DropdownMenuItem(text={Text(name)},onClick={open=false;onChange(id)})}}
        }
    }
}


@Composable internal fun PurchaseSupplierSelect(label: String,value: String,options: List<Pair<String,String>>,onChange: (String)->Unit,enabled: Boolean,modifier: Modifier=Modifier) {
    var open by remember {mutableStateOf(false)}
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,fontSize=13.sp,fontWeight=FontWeight.Bold)
        Box {
            Surface(onClick={open=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.background,border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline)) {
                Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(options.find {it.first==value}?.second.orEmpty(),Modifier.weight(1f),fontSize=15.sp)
                }
            }
            DropdownMenu(open,{open=false},modifier=Modifier.heightIn(max=300.dp),shape=RoundedCornerShape(12.dp)) {
                options.forEach {(id,name)->DropdownMenuItem(text={Text(name)},onClick={open=false;onChange(id)})}
            }
        }
    }
}
