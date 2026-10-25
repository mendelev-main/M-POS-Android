package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            MposButton("Все категории",{dialog="categories"},enabled=!busy,style=ActionStyle.Secondary)
            if(editing) MposButton("Добавить плитку",{revision=s.homeRevision;dialog="add";model.dismiss()},enabled=!busy,style=ActionStyle.Secondary,modifier=Modifier.testTag("homeAdd"))
            if(editing) MposButton("Сбросить",{revision=s.homeRevision;dialog="reset";model.dismiss()},enabled=!busy,style=ActionStyle.Secondary)
        }
        if(editing) Text("До 20 плиток. Удерживайте и перетащите плитку. Нажатие — перемещение по номеру места или удаление.",style=MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.weight(1f)) {
            val columns=(((maxWidth+12.dp)/162.dp).toInt()).coerceIn(1,5)
            val last=tiles.keys.maxOrNull() ?: -1
            val count=if(editing) (((last/columns+2)*columns).coerceAtLeast(20)).coerceAtMost(HomeEngine.MAX_SLOTS) else last+1
            LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("workspaceGrid").then(drag.modifier),state=state,
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(count,key={it}) {index->
                    val tile=tiles[index]
                    val highlight=if(drag.target==index) Modifier.border(2.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.large) else Modifier
                    if(tile==null) {
                        if(editing) Surface(Modifier.fillMaxWidth().height(144.dp).then(highlight).testTag("homeSlot:$index"),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant) {
                            Box(Modifier.padding(12.dp)) {Text("Свободное место ${index+1}",style=MaterialTheme.typography.bodySmall)}
                        } else Spacer(Modifier.height(144.dp))
                    } else {
                        val tag=if(tile.type=="category") "workspaceCategory:${tile.id}" else "workspaceHomeProduct:${tile.id}"
                        MposWorkspaceTile({if(editing) manage(tile) else if(tile.type=="category") onCategory(tile.id) else s.products.find {it.id==tile.id}?.let(onProduct)},
                            Modifier.testTag(tag).then(highlight),!busy) {
                            if(tile.type=="category") s.categories.find {it.id==tile.id}?.let {MposCategoryBadge(it.color,it.symbol)}
                            if(tile.type=="product") s.products.find {it.id==tile.id}?.photoId?.let {ProductPhoto(it,photos,Modifier.fillMaxWidth().height(72.dp))}
                            Text(title(tile),style=MaterialTheme.typography.titleSmall,maxLines=3)
                            if(editing) Text("Место ${index+1}",style=MaterialTheme.typography.bodySmall)
                            else if(tile.type=="category") Text("Товаров: ${s.products.count {it.categoryId==tile.id}}",style=MaterialTheme.typography.bodySmall)
                            else s.products.find {it.id==tile.id}?.let {p->Text(if(p.price.toBigDecimal().signum()==0) "Ввести цену" else p.price.replace('.',',')+" BYN")}
                        }
                    }
                }
            }
            if(!editing && tiles.isEmpty()) Text("Нет плиток. Добавьте их в режиме раскладки или откройте все категории")
        }
    }
    if(dialog!=null) MposDialog(when(dialog) {"add"->"Добавить плитку";"categories"->"Все категории";"reset"->"Сбросить раскладку?";"remove"->"Убрать плитку?";else->"Плитка главного экрана"},
        {dialog=null;model.dismiss()},!busy) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        when(dialog) {
            "categories","add"->Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val entries=s.categories.map {HomeTile("category",it.id,0) to it.name}+listOf(HomeTile("category","",0) to "Без категории")+
                    if(dialog=="add") s.products.map {HomeTile("product",it.id,0) to it.name} else emptyList()
                entries.forEach {(tile,name)->
                    val exists=s.home.tiles.any {it.type==tile.type && it.id==tile.id}
                    MposButton((if(dialog=="add" && exists) "Уже добавлено: " else "")+name,{
                        if(dialog=="categories") {onCategory(tile.id);dialog=null}
                        else model.home(HomeAction.Add(tile.type,tile.id),revision) {dialog=null}
                    },enabled=!busy && (dialog=="categories" || !exists),style=ActionStyle.Secondary,modifier=Modifier.testTag("homeChoice:${tile.type}:${tile.id}"))
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
