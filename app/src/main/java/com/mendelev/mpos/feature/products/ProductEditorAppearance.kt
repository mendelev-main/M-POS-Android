package com.mendelev.mpos.feature.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.ActionStyle

internal val productEditorSections=listOf("main" to "Основное","stock" to "Состав и остатки","modifiers" to "Модификаторы","usage" to "Где используется","web" to "Онлайн-меню")

/** Fullscreen pinned .product-editor; each panel uses the same frame and save/close guards. */
@Composable internal fun ProductEditorFrame(name: String,type: String,dirty: Boolean,editing: Boolean,busy: Boolean,
    section: String,onSection: (String)->Unit,onBack: ()->Unit,onSave: ()->Unit,onDelete: ()->Unit,
    summary: @Composable ()->Unit,content: @Composable ColumnScope.()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("productEditor")) {
        val small=maxWidth<=700.dp;val desktop=maxWidth>1100.dp
        Column(Modifier.fillMaxSize()) {
            Surface(color=MaterialTheme.colorScheme.surface) {
                Column {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=if(small) 12.dp else 28.dp,vertical=if(small) 12.dp else 18.dp)) {
                        val base=(maxWidth-94.dp)/3
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(if(small) 10.dp else 22.dp)) {
                            ProductEditorButton("← Назад",onBack,Modifier.width(if(small) 90.dp else base+26.dp).height(49.dp).testTag("productEditorBack"),!busy)
                            Column(Modifier.weight(1f)) {
                                Text(if(dirty) "ЕСТЬ ИЗМЕНЕНИЯ" else "КАРТОЧКА ТОВАРА",fontSize=11.sp,lineHeight=15.sp,letterSpacing=.88.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(name.ifBlank {"Новый товар"},Modifier.padding(top=4.dp),fontSize=if(small) 17.sp else 22.sp,lineHeight=27.5.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            ProductEditorButton("Сохранить",onSave,Modifier.width(if(small) 100.dp else base+24.dp).height(48.dp).testTag("saveProduct"),!busy,ActionStyle.Primary,busy)
                        }
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outline)
                }
            }
            @Composable fun nav(modifier: Modifier) {
                Surface(modifier,color=MaterialTheme.colorScheme.surface) {
                    if(small) Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        productEditorSections.forEach {(id,label)->ProductSectionTab(id,label,section,!busy,onSection)}
                        if(editing) ProductEditorButton("Удалить товар",onDelete,enabled=!busy,style=ActionStyle.Danger)
                    } else Column(Modifier.fillMaxHeight().padding(horizontal=16.dp,vertical=28.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(if(type=="simple") "ПРОСТОЙ ТОВАР" else "СОСТАВНОЙ ТОВАР",Modifier.padding(start=12.dp,end=12.dp,bottom=18.dp),fontSize=11.sp,lineHeight=15.sp,letterSpacing=.66.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        productEditorSections.forEach {(id,label)->ProductSectionTab(id,label,section,!busy,onSection,Modifier.fillMaxWidth())}
                        Spacer(Modifier.weight(1f))
                        if(editing) ProductEditorButton("Удалить товар",onDelete,Modifier.fillMaxWidth().padding(top=28.dp).testTag("productDelete"),!busy,ActionStyle.Danger)
                    }
                }
            }
            @Composable fun workspace(modifier: Modifier) {
                val scroll=rememberScrollState()
                LaunchedEffect(section) {scroll.scrollTo(0)}
                Column(modifier.verticalScroll(scroll).padding(if(small) 14.dp else if(desktop) 28.dp else 22.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    if(desktop) Row(Modifier.widthIn(max=1250.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(22.dp),verticalAlignment=Alignment.Top) {
                        ProductEditorCard(Modifier.weight(1f),content)
                        Box(Modifier.width(240.dp).testTag("productSummary")) {summary()}
                    } else Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                        ProductEditorCard(Modifier.fillMaxWidth(),content)
                        Box(Modifier.fillMaxWidth().testTag("productSummary")) {summary()}
                    }
                }
            }
            if(small) Column(Modifier.weight(1f)) {nav(Modifier.fillMaxWidth());workspace(Modifier.weight(1f).fillMaxWidth())}
            else Row(Modifier.weight(1f)) {
                nav(Modifier.width(if(desktop) 210.dp else 190.dp).fillMaxHeight().testTag("productEditorNav"))
                VerticalDivider(color=MaterialTheme.colorScheme.outline)
                workspace(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
@Composable private fun ProductSectionTab(id: String,label: String,current: String,enabled: Boolean,onSection: (String)->Unit,modifier: Modifier=Modifier) {
    val c=MaterialTheme.colorScheme;val active=id==current
    Surface(onClick={onSection(id)},enabled=enabled,modifier=modifier.heightIn(min=48.dp).testTag("productSection:$id").semantics {selected=active},shape=RoundedCornerShape(12.dp),color=if(active) c.primaryContainer else c.surface,contentColor=if(active) c.primary else c.onSurfaceVariant) {
        Box(Modifier.padding(14.dp),contentAlignment=Alignment.CenterStart) {Text(label,fontSize=14.sp,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun ProductEditorCard(modifier: Modifier,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.testTag("productEditorCard"),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(27.dp),content=content)
    }
}
@Composable internal fun ProductEditorNote(text: String,modifier: Modifier=Modifier) {Text(text,modifier,fontSize=12.sp,lineHeight=19.8.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable internal fun ProductSectionHeading(text: String,badge: String?=null) {
    Row(Modifier.fillMaxWidth().padding(bottom=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(text,Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.Bold)
        if(badge!=null) Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.background) {
            Text(badge,Modifier.padding(horizontal=9.dp,vertical=5.dp),fontSize=11.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
