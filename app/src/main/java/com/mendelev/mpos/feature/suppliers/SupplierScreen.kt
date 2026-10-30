package com.mendelev.mpos.feature.suppliers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.CatalogRules

/** Pinned suppliers.js modal: name + searchable checklist of simple products. */
@Composable fun SupplierScreen(model: SupplierViewModel,onBack: ()->Unit) {
    val administration by com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle()
    val locked=ui.pending || ui.unresolved!=null
    val form=ui.form
    var query by rememberSaveable(form?.id) {mutableStateOf("")}
    LaunchedEffect(model,administration.generation) {model.refresh()}
    val close={if(form?.discarding==true) model.change {it.copy(discarding=false)} else if(form?.deleting==true) model.change {it.copy(deleting=false)} else model.dismiss()}
    BackHandler {if(!locked) {if(form!=null) close() else onBack()}}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("Поставщики",navigationEnabled=!locked && form==null) {}
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton("Настройки",onBack,enabled=!locked,style=ActionStyle.Secondary)
                Text("Поставщики",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                MposButton("Новый поставщик",{model.edit()},enabled=!locked && !ui.blocked && ui.canEdit,modifier=Modifier.testTag("supplierNew"))
            }
            if(ui.message!=null && form==null) MposNotice(ui.message!!,ui.blocked)
            if(ui.unresolved!=null && form==null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("supplierRetry"))
            if(ui.blocked && ui.unresolved==null) MposButton("Повторить чтение",model::refresh,enabled=!ui.pending)
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).testTag("supplierList"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(ui.snapshot?.suppliers.orEmpty(),key={it.id}) {s->
                    Surface(onClick={model.edit(s.id)},enabled=!locked && !ui.blocked && ui.canEdit,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().testTag("supplier:${s.id}")) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(s.name,style=MaterialTheme.typography.titleMedium)
                            Text("Товаров: ${s.productIds.size}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(!ui.pending && !ui.blocked && ui.canEdit && ui.snapshot?.suppliers?.isEmpty()==true) item {Text("Поставщиков пока нет")}
            }
        }
    }
    if(form!=null) MposDialog(if(form.discarding) "Закрыть без сохранения?" else if(form.deleting) "Удалить поставщика?" else if(form.revision==null) "Новый поставщик" else "Товары поставщика",close,!locked,wide=true,maxWidth=700.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        if(form.discarding) {
            Text("Изменения поставщика ещё не сохранены.")
            MposButton("Продолжить редактирование",{model.change {it.copy(discarding=false)}},enabled=!locked,style=ActionStyle.Secondary)
            MposButton("Закрыть без сохранения",model::discard,enabled=!locked,style=ActionStyle.Danger)
        } else if(form.deleting) {
            Text("Поставщик «${form.name}» будет убран из списка. Товары и остатки останутся без изменений.")
            MposButton("Отмена",{model.change {it.copy(deleting=false)}},enabled=!locked,style=ActionStyle.Secondary)
            MposButton("Удалить",model::save,enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("supplierDeleteConfirm"))
        } else {
            MposField(form.name,{value->model.change {it.copy(name=value)}},"Название поставщика",Modifier.testTag("supplierName"),!locked)
            Text("Товары на поставщике",style=MaterialTheme.typography.titleMedium)
            Text("Выберите товары, которые обычно закупаются у этого поставщика. В приёмке они будут показываться первыми.",style=MaterialTheme.typography.bodySmall)
            MposField(query,{query=it},"Поиск товара или категории",Modifier.testTag("supplierSearch"),!locked)
            val products=ui.snapshot?.products.orEmpty().filter {CatalogRules.key(it.name+" "+it.category).contains(CatalogRules.key(query))}
            LazyColumn(Modifier.heightIn(max=260.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                items(products,key={it.id}) {p->
                    val toggle={model.change {f->f.copy(productIds=if(p.id in f.productIds) f.productIds-p.id else f.productIds+p.id)}}
                    Row(Modifier.fillMaxWidth().clickable(enabled=!locked,onClick=toggle).padding(8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Checkbox(p.id in form.productIds,{toggle()},enabled=!locked,modifier=Modifier.testTag("supplierProduct:${p.id}"))
                        Column(Modifier.weight(1f)) {Text(p.name);Text(p.category,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
                if(products.isEmpty()) item {Text(if(ui.snapshot?.products?.isEmpty()==true) "Сначала создайте простые товары" else "Товары не найдены")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton("Отмена",model::dismiss,enabled=!locked,style=ActionStyle.Secondary)
                MposButton(if(form.revision==null) "Создать" else "Сохранить",model::save,enabled=!locked,modifier=Modifier.testTag("supplierSave"))
                if(form.revision!=null) MposButton("Удалить",{model.change {it.copy(deleting=true)}},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("supplierDelete"))
            }
        }
        if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("supplierRetry"))
        }
    }
}
