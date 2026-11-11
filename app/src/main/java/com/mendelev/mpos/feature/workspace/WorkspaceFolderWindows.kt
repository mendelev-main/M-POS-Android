package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.CategoryAppearance
import com.mendelev.mpos.domain.workspace.*

@Composable internal fun WorkspaceFolderEditor(id: String,item: LayoutItem?,busy: Boolean,error: String?,
    onClose: ()->Unit,onSave: (String,FolderAppearance)->Unit,onDelete: ()->Unit,onOpen: ()->Unit) {
    var name by rememberSaveable(id) {mutableStateOf(item?.name.orEmpty())}
    var color by rememberSaveable(id) {mutableStateOf(item?.appearance?.color.orEmpty())}
    var symbol by rememberSaveable(id) {mutableStateOf(item?.appearance?.symbol.orEmpty())}
    var columns by rememberSaveable(id) {mutableIntStateOf(item?.appearance?.columns ?: 0)}
    var height by rememberSaveable(id) {mutableIntStateOf(item?.appearance?.tileHeight ?: 144)}
    var deleting by rememberSaveable(id) {mutableStateOf(false)}
    MposWebModal(if(deleting)"Удалить папку?" else if(item==null)"Новая папка" else "Изменить папку",onClose,!busy) {
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        if(deleting) {
            Text("Товары вернутся в корень категории.")
            Row(Modifier.fillMaxWidth().padding(top=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposPosControl("Отмена",{deleting=false},Modifier.weight(1f),!busy,PosControl.Secondary,height=48.dp,radius=16.dp)
                MposPosControl("Удалить",onDelete,Modifier.weight(1f),!busy,PosControl.Delete,height=48.dp,radius=16.dp,icon="delete")
            }
        } else {
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                MposWebField(name,{name=it},"Название","workspaceFolderName",!busy)
                MposWebField(symbol,{symbol=it},"Символ папки","workspaceFolderSymbol",!busy,placeholder="До 3 символов")
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Цвет папки",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                    MposPosControl("Без цвета",{color=""},enabled=!busy,kind=if(color.isEmpty())PosControl.Cash else PosControl.Secondary,height=40.dp,radius=10.dp)
                }
                MposColorPicker(CategoryAppearance.palette,color,{color=it},enabled=!busy)
                Text("Колонки внутри папки",fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    (0..4).forEach {n->MposPosControl(if(n==0)"Авто" else n.toString(),{columns=n},Modifier.weight(if(n==0)1.5f else 1f).testTag("folderColumns:$n"),
                        !busy,if(columns==n)PosControl.Cash else PosControl.Secondary,height=44.dp,radius=10.dp,horizontalPadding=0.dp)}
                }
                Text("Размер плиток",fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(120 to "Меньше",144 to "Обычные",168 to "Крупнее").forEach {(value,label)->
                        MposPosControl(label,{height=value},Modifier.weight(1f).testTag("folderHeight:$value"),!busy,
                            if(height==value)PosControl.Cash else PosControl.Secondary,height=44.dp,radius=10.dp,horizontalPadding=6.dp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    MposPosControl("Отмена",onClose,Modifier.weight(1f),!busy,PosControl.Secondary,height=48.dp,radius=16.dp)
                    MposPosControl("Сохранить",{onSave(name,FolderAppearance(color,symbol,columns,height))},Modifier.weight(1f).testTag("workspaceFolderSave"),!busy,PosControl.Cash,height=48.dp,radius=16.dp)
                }
                if(item!=null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    MposPosControl("Открыть",onOpen,Modifier.weight(1f),!busy,PosControl.Secondary,height=44.dp,radius=16.dp)
                    MposPosControl("Удалить",{deleting=true},Modifier.weight(1f),!busy,PosControl.Delete,height=44.dp,radius=16.dp,icon="delete")
                }
            }
        }
    }
}

/** Source folder window: widths280/448/660/872, padding18, header44, grid gap10, up to three visible rows. */
@Composable internal fun WorkspaceFolderWindow(item: LayoutItem,count: Int,busy: Boolean,
    onClose: ()->Unit,content: @Composable (Int,Int,Int)->Unit) {
    val config=LocalConfiguration.current
    val columns=if(item.appearance.columns==0) count.coerceIn(1,4) else item.appearance.columns
    val rowHeight=item.appearance.tileHeight.coerceAtMost(((config.screenHeightDp-176)/3).coerceAtLeast(120))
    val rows=((count+columns-1)/columns).coerceAtLeast(1)
    val gridHeight=(rows*rowHeight+(rows-1)*10+8).coerceAtMost(rowHeight*3+28).coerceAtMost((config.screenHeightDp-180).coerceAtLeast(120))
    Dialog({if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        MposDialogStatusBar()
        Surface(Modifier.padding(20.dp).widthIn(max=listOf(280,448,660,872)[columns-1].dp).fillMaxWidth()
            .heightIn(max=(config.screenHeightDp-40).dp).testTag("workspaceFolderWindow"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(item.name,Modifier.weight(1f),fontSize=20.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    MposPosControl("Закрыть",onClose,enabled=!busy,kind=PosControl.Secondary,height=44.dp,radius=10.dp,horizontalPadding=12.dp)
                }
                content(columns,rowHeight,gridHeight)
            }
        }
    }
}


/** In layout mode a tap edits the folder; the explicit top-right action opens its editable contents. */
@Composable internal fun WorkspaceFolderTile(item: LayoutItem,editing: Boolean,enabled: Boolean,
    modifier: Modifier=Modifier,onManage: ()->Unit,onOpen: ()->Unit) {
    MposWorkspaceTile({if(editing)onManage() else onOpen()},modifier,enabled,
        categoryColor=item.appearance.color.takeIf {it.isNotBlank()},symbol=item.appearance.symbol,editing=editing) {
        Box(Modifier.fillMaxSize()) {
            Text(item.name,Modifier.align(Alignment.Center).padding(top=if(editing)20.dp else 0.dp),
                fontSize=19.sp,lineHeight=22.8.sp,fontWeight=FontWeight.ExtraBold,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(editing) MposPosControl("Открыть",onOpen,Modifier.align(Alignment.TopEnd).testTag("workspaceFolderOpen:"+item.id),
                enabled,PosControl.Secondary,height=32.dp,radius=10.dp,fontSize=11f,horizontalPadding=8.dp)
        }
    }
}
