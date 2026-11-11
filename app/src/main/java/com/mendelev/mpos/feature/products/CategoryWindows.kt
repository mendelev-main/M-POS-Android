package com.mendelev.mpos.feature.products

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.CategoryEntity
import com.mendelev.mpos.data.local.CategoryChannels
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun CategoryListWindow(categories: List<CategoryEntity>,counts: Map<String?,Int>,enabled: Boolean,admin: Boolean,error: String?,onClose: ()->Unit,onAdd: ()->Unit,onEdit: (CategoryEntity)->Unit,onDelete: (CategoryEntity)->Unit,onMove: (String,Int)->Unit,channels: Map<String,CategoryChannels> = emptyMap(),onChannel: (CategoryEntity,Boolean)->Unit = {_,_->}) {
    var menu by remember {mutableStateOf<String?>(null)}
    MposWebModal("Категории",onClose,enabled) {
        Text("Добавляйте, переименовывайте, настраивайте цвет и удаляйте категории.",Modifier.padding(bottom=12.dp),fontSize=13.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(bottom=10.dp),horizontalArrangement=Arrangement.End) {
            MposPosControl("+ Добавить категорию",onAdd,Modifier.testTag("categoryAdd"),enabled && admin,PosControl.Cash,radius=16.dp)
        }
        error?.let {MposNotice(it,true)}
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.52f).dp).testTag("categoryList")) {
            if(categories.isEmpty()) item {Text("Категорий пока нет.",Modifier.padding(vertical=20.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            itemsIndexed(categories,key={_,c->c.id}) {index,c->
                Box {
                    Row(Modifier.fillMaxWidth().testTag("categoryRow:${c.id}").combinedClickable(enabled=enabled && admin,onClick={onEdit(c)},onLongClick={menu=c.id}).padding(horizontal=4.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Surface(Modifier.size(14.dp),shape=RoundedCornerShape(4.dp),color=Color(android.graphics.Color.parseColor(c.color))) {}
                                Text(c.name,fontSize=14.5.sp,lineHeight=20.sp,fontWeight=FontWeight.ExtraBold)
                            }
                            Text("${counts[c.id] ?: 0} товаров",Modifier.padding(top=3.dp),fontSize=12.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CategoryChannelToggle("МЕНЮ",channels[c.id]?.menu ?: true,enabled && admin) {onChannel(c,true)}
                        CategoryChannelToggle("ЗАКАЗ",channels[c.id]?.order ?: true,enabled && admin) {onChannel(c,false)}
                        MposPosControl("",{onEdit(c)},Modifier.width(44.dp).testTag("categoryEdit:${c.id}").semantics {contentDescription="Изменить категорию ${c.name}"},enabled && admin,height=44.dp,radius=10.dp,fontSize=18f,horizontalPadding=0.dp,icon="edit")
                        MposPosControl("",{onDelete(c)},Modifier.width(44.dp).testTag("categoryDelete:${c.id}").semantics {contentDescription="Удалить категорию ${c.name}"},enabled && admin,PosControl.Delete,height=44.dp,radius=10.dp,fontSize=22f,horizontalPadding=0.dp,icon="close")
                    }
                    DropdownMenu(menu==c.id,{menu=null}) {
                        DropdownMenuItem(text={Text("Выше")},onClick={menu=null;onMove(c.id,-1)},enabled=index>0 && enabled,modifier=Modifier.testTag("categoryUp:${c.id}"))
                        DropdownMenuItem(text={Text("Ниже")},onClick={menu=null;onMove(c.id,1)},enabled=index<categories.lastIndex && enabled,modifier=Modifier.testTag("categoryDown:${c.id}"))
                    }
                }
                if(index<categories.lastIndex) HorizontalDivider()
            }
        }
        Spacer(Modifier.height(18.dp))
        MposPosControl("Закрыть",onClose,Modifier.fillMaxWidth(),enabled,PosControl.Secondary,height=48.dp,radius=16.dp)
    }
}

@Composable private fun CategoryChannelToggle(label: String,on: Boolean,enabled: Boolean,onClick: ()->Unit) {
    val c=MaterialTheme.colorScheme
    Surface(Modifier.clipForCategoryToggle(on,enabled,onClick,label),shape=RoundedCornerShape(999.dp),border=BorderStroke(1.dp,if(on)c.primary else c.outline),color=if(on)c.primaryContainer else c.background) {
        Row(Modifier.padding(horizontal=7.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(22.dp,14.dp).background(if(on)c.primary else c.outline,RoundedCornerShape(10.dp))) {
                Box(Modifier.align(if(on)Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal=2.dp).size(10.dp).background(Color.White,RoundedCornerShape(999.dp)))
            }
            Text(label,fontSize=10.sp,lineHeight=12.sp,fontWeight=FontWeight.ExtraBold,color=if(on)c.primary else c.onSurfaceVariant)
        }
    }
}


private fun Modifier.clipForCategoryToggle(on: Boolean,enabled: Boolean,onClick: ()->Unit,label: String)=
    this.semantics {contentDescription=label}.toggleable(value=on,enabled=enabled,role=Role.Switch,onValueChange={onClick()})
