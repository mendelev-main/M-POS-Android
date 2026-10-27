package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.workspace.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorkspaceHome(s: WorkspaceSnapshot,ui: WorkspaceUi,editing: Boolean,model: WorkspaceViewModel,photos: ProductPhotoStore,
    modifier: Modifier,onCategory: (String)->Unit,onProduct: (WorkspaceProduct)->Unit) {
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    var type by rememberSaveable {mutableStateOf("")};var id by rememberSaveable {mutableStateOf("")}
    var slot by rememberSaveable {mutableStateOf("")};var revision by rememberSaveable {mutableStateOf(0L)}
    val busy=ui.pending>0
    val state=rememberLazyGridState()
    val tiles=s.home.tiles.associateBy {it.slot}
    val drag=workspaceDrag(state,editing && !busy,s.homeRevision,"home:${s.home.tiles}") {from,to->
        tiles[from]?.let {model.home(HomeAction.Move(it.type,it.id,to),s.homeRevision)}
    }
    fun title(tile: HomeTile)=if(tile.type=="category") s.categories.find {it.id==tile.id}?.name ?: "Без категории"
        else s.products.find {it.id==tile.id}?.name ?: "Недоступен"
    fun manage(tile: HomeTile) {type=tile.type;id=tile.id;slot=(tile.slot+1).toString();revision=s.homeRevision;dialog="manage";model.dismiss()}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val geometry=LocalWorkspaceGeometry.current
            val scale=LocalDensity.current.fontScale
            val columns=if(scale<=1.1f) geometry.columns else ((maxWidth/(144*scale).dp).toInt()).coerceIn(1,geometry.columns)
            val rowHeight=((maxHeight-8.dp-12.dp*(geometry.homeRows-1))/geometry.homeRows).coerceAtLeast(128.dp)
            val last=tiles.keys.maxOrNull() ?: -1
            val count=if(editing) (((last/columns+2)*columns).coerceAtLeast(20)).coerceAtMost(HomeEngine.MAX_SLOTS) else last+1
            val dots=if(editing) Modifier.drawBehind {
                val step=18.dp.toPx();var y=0f
                while(y<size.height) {var x=0f;while(x<size.width) {drawCircle(Color(0xFF767C8C).copy(alpha=.2f),1.dp.toPx(),Offset(x,y));x+=step};y+=step}
            } else Modifier
            LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().then(dots).testTag("workspaceGrid").then(drag.modifier),state=state,contentPadding=PaddingValues(4.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(count,key={it}) {index->
                    val tile=tiles[index]
                    val highlight=if(drag.target==index) Modifier.border(2.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.large) else Modifier
                    if(tile==null) {
                        if(editing) Surface(Modifier.fillMaxWidth().height(rowHeight).then(highlight).testTag("homeSlot:$index"),shape=MaterialTheme.shapes.large,color=Color.Transparent) {
                            Box(Modifier.fillMaxSize()) {}
                        } else Spacer(Modifier.height(rowHeight))
                    } else {
                        val tag=if(tile.type=="category") "workspaceCategory:${tile.id}" else "workspaceHomeProduct:${tile.id}"
                        MposWorkspaceTile({if(editing) manage(tile) else if(tile.type=="category") onCategory(tile.id) else s.products.find {it.id==tile.id}?.let(onProduct)},
                            Modifier.height(rowHeight).testTag(tag).then(highlight),!busy,
                            categoryColor=if(tile.type=="category") s.categories.find {it.id==tile.id}?.color else null,
                            symbol=if(tile.type=="category") s.categories.find {it.id==tile.id}?.symbol else null) {
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(title(tile)+(if(tile.type=="category") "  ›" else ""),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall.copy(fontSize=14.5.sp,lineHeight=18.sp),maxLines=2,overflow=TextOverflow.Ellipsis)
                                if(tile.type=="product") s.products.find {it.id==tile.id}?.photoId?.let {ProductPhoto(it,photos,Modifier.size(32.dp))}
                            }
                            Spacer(Modifier.weight(1f))
                            if(editing) Text("Место ${index+1}",style=MaterialTheme.typography.bodySmall)
                            else if(tile.type=="category") Text("Категория",style=MaterialTheme.typography.bodySmall.copy(fontSize=11.5.sp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            else s.products.find {it.id==tile.id}?.let {p->WorkspaceTileFooter(ui.availability,p)}
                        }
                    }
                }
            }
            if(!editing && tiles.isEmpty()) Text("Нет плиток. Добавьте их в режиме раскладки или откройте все категории")
        }
        if(editing) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            PosAction("＋ Изменить содержимое",{revision=s.homeRevision;dialog="add";model.dismiss()},enabled=!busy,tone=PosTone.Primary,modifier=Modifier.testTag("homeAdd"))
            PosAction("Все категории",{dialog="categories"},enabled=!busy,tone=PosTone.Info)
            PosAction("Сбросить",{revision=s.homeRevision;dialog="reset";model.dismiss()},enabled=!busy)
        }
    }

    if(dialog!=null) MposDialog(when(dialog) {"add"->"Добавить плитку";"categories"->"Все категории";"reset"->"Сбросить раскладку?";"remove"->"Убрать плитку?";else->"Плитка главного экрана"},
        {dialog=null;model.dismiss()},!busy) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        when(dialog) {
            "categories","add"->{
                val entries=s.categories.map {HomeTile("category",it.id,0) to it.name}+listOf(HomeTile("category","",0) to "Без категории")+
                    if(dialog=="add") s.products.map {HomeTile("product",it.id,0) to it.name} else emptyList()
                LazyColumn(Modifier.heightIn(max=400.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(entries,key={it.first.type+":"+it.first.id}) {(tile,name)->
                    val exists=s.home.tiles.any {it.type==tile.type && it.id==tile.id}
                    MposButton((if(dialog=="add" && exists) "Уже добавлено: " else "")+name,{
                        if(dialog=="categories") {onCategory(tile.id);dialog=null}
                        else model.home(HomeAction.Add(tile.type,tile.id),revision) {dialog=null}
                    },enabled=!busy && (dialog=="categories" || !exists),style=ActionStyle.Secondary,modifier=Modifier.testTag("homeChoice:${tile.type}:${tile.id}"))
                }
                }
            }
            "manage"->{
                Text(title(HomeTile(type,id,0)))
                MposField(slot,{slot=it},"Место от 1 до 100",Modifier.testTag("homePosition"),!busy,numeric=true)
                MposButton("Переместить",{
                    val n=slot.toIntOrNull()
                    model.home(HomeAction.Move(type,id,if(n!=null && n in 1..100) n-1 else -1),revision) {dialog=null}
                },enabled=!busy,modifier=Modifier.testTag("homeMove"))
                MposButton("Убрать с главного экрана",{dialog="remove"},enabled=!busy,style=ActionStyle.Danger)
                MposButton("Вернуть стандартную раскладку",{dialog="reset"},enabled=!busy,style=ActionStyle.Secondary)
            }
            "remove"->{Text("Товар или категория останутся в каталоге")
                MposButton("Убрать плитку",{model.home(HomeAction.Remove(type,id),revision) {dialog=null}},enabled=!busy,style=ActionStyle.Danger)}
            "reset"->{Text("Показать категории в стандартном порядке? Настроенные места будут сброшены.")
                MposButton("Сбросить",{model.home(HomeAction.Reset,revision) {dialog=null}},enabled=!busy,style=ActionStyle.Danger)}
        }
    }
}
