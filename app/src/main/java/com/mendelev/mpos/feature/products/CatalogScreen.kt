package com.mendelev.mpos.feature.products

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.CategoryEntity
import com.mendelev.mpos.domain.catalog.CatalogRules
import java.util.UUID

@Composable fun CatalogScreen(model: CatalogViewModel, onSettings: () -> Unit, onGallery: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var categories by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryName by rememberSaveable { mutableStateOf("") }
    var categoryRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryCommand by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteCategory by rememberSaveable { mutableStateOf(false) }
    fun closeEditor() { if(!state.busy) { if(state.draft?.dirty == true) confirmDiscard=true else model.discard() } }
    BackHandler(enabled=state.draft!=null) { closeEditor() }
    val d = state.draft
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Surface(color=androidx.compose.ui.graphics.Color(0xFF22273A), contentColor=androidx.compose.ui.graphics.Color.White) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("M-POS", style=MaterialTheme.typography.titleLarge)
                Text("Локальный владелец", Modifier.weight(1f), style=MaterialTheme.typography.bodySmall, color=androidx.compose.ui.graphics.Color.White.copy(alpha=0.62f))
                if(d==null) {
                    if(com.mendelev.mpos.BuildConfig.DEBUG) MposNavigationAction("Компоненты", onGallery)
                    MposNavigationAction("Настройки", onSettings)
                }
            }
        }
        if(state.loading) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        else if(state.fatal) Column(Modifier.padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            MposNotice(state.message ?: "Каталог недоступен", true); MposButton("Повторить", model::retryOpen)
        } else if(d!=null) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton("Назад", { closeEditor() }, enabled=!state.busy, style=ActionStyle.Secondary)
                Column(Modifier.weight(1f)) {
                    Text(if(d.expectedRevision==null) "Новый товар" else "Карточка товара", style=MaterialTheme.typography.titleLarge)
                    Text("Простой товар · BYN · шт", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MposButton("Сохранить", model::save, busy=state.busy, modifier=Modifier.testTag("saveProduct"))
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > 840.dp
                Row(Modifier.fillMaxSize().padding(horizontal=24.dp), horizontalArrangement=Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom=32.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        if(state.message!=null) MposNotice(state.message!!, state.error)
                        if(state.field=="conflict") MposButton("Перечитать карточку", model::reloadProduct, enabled=!state.busy, style=ActionStyle.Secondary)
                        Surface(shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            Column(Modifier.padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text("Основное", style=MaterialTheme.typography.titleMedium)
                                MposField(d.name, { v -> model.change { it.copy(name=v) } }, "Название", Modifier.testTag("productName"), !state.busy,
                                    error=state.message.takeIf { state.field=="name" })
                                Text("Категория", style=MaterialTheme.typography.labelMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    MposButton(state.categories.find { it.id==d.categoryId }?.name ?: "Без категории", { categories=true }, enabled=!state.busy, style=ActionStyle.Secondary)
                                    if(d.categoryId!=null) MposButton("Убрать", { model.change { it.copy(categoryId=null) } }, enabled=!state.busy, style=ActionStyle.Secondary)
                                }
                                MposField(d.price, { v -> model.change { it.copy(price=v) } }, "Цена продажи, BYN", Modifier.testTag("productPrice"), !state.busy, true,
                                    state.message.takeIf { state.field=="Цена" })
                                MposField(d.cost, { v -> model.change { it.copy(cost=v) } }, "Себестоимость, BYN за шт", Modifier.testTag("productCost"), !state.busy, true,
                                    state.message.takeIf { state.field=="Себестоимость" })
                                MposField(d.stock, { v -> model.change { it.copy(stock=v,stockEdited=true) } }, "Остаток, шт", Modifier.testTag("productStock"), !state.busy, true,
                                    state.message.takeIf { state.field=="Остаток" })
                                Text("Остаток можно изменить без указания причины. История изменений сохраняется автоматически.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if(d.expectedRevision!=null) MposButton("Удалить товар", { confirmDelete=true }, enabled=!state.busy, style=ActionStyle.Danger)
                    }
                    if(wide) Surface(Modifier.width(220.dp), shape=RoundedCornerShape(16.dp), color=MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("Цена продажи", style=MaterialTheme.typography.labelMedium)
                            Text("${d.price} BYN", style=MaterialTheme.typography.titleLarge)
                            Text(if(d.price.trim() in listOf("0","0,00","0.00")) "Ручная цена при добавлении в заказ" else "Цена за одну штуку", style=MaterialTheme.typography.bodySmall)
                            Text("Сохранение выполняется локально", style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.padding(horizontal=24.dp,vertical=20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Товары", Modifier.weight(1f), style=MaterialTheme.typography.titleLarge)
                    MposButton("Категории", { categories=true }, style=ActionStyle.Secondary)
                    MposButton("Добавить товар", { model.edit() })
                }
                if(state.message!=null) MposNotice(state.message!!, state.error)
                MposField(search, { search=it }, "Поиск по названию или категории")
                if(filter!=null) MposButton("Все категории", { filter=null }, style=ActionStyle.Secondary)
                val q = CatalogRules.key(search)
                val rows = state.products.filter { (filter==null || it.product.categoryId==filter) &&
                    (q.isEmpty() || CatalogRules.key(it.product.name+" "+(it.categoryName ?: "Без категории")).contains(q)) }
                if(rows.isEmpty()) MposNotice(if(state.products.isEmpty()) "Пока нет товаров. Создайте первый товар." else "По запросу ничего не найдено")
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.weight(1f)) {
                    items(rows,key={ it.product.id }) { row ->
                        Surface(Modifier.fillMaxWidth().clickable { model.edit(row) }, shape=RoundedCornerShape(16.dp), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            Row(Modifier.padding(18.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.product.name, fontWeight=FontWeight.Bold)
                                    Text(row.categoryName ?: "Без категории", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${row.quantity} шт", style=MaterialTheme.typography.bodyMedium)
                                Text("${row.product.price} BYN", fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if(categories && categoryId==null) MposDialog("Категории", { categories=false }, !state.busy) {
        LazyColumn(Modifier.heightIn(max=300.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(state.categories,key={it.id}) { c ->
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(c.name, Modifier.weight(1f).clickable {
                        if(d!=null) model.change { it.copy(categoryId=c.id) } else filter=c.id
                        categories=false
                    }.padding(vertical=16.dp))
                    MposButton("Изменить", { categoryId=c.id;categoryName=c.name;categoryRevision=c.revision;categoryCommand=UUID.randomUUID().toString() }, enabled=!state.busy, style=ActionStyle.Secondary)
                }
            }
        }
        if(state.categories.isEmpty()) Text("Категорий пока нет")
        MposButton("Создать категорию", { categoryId=UUID.randomUUID().toString();categoryRevision=null;categoryName="";categoryCommand=UUID.randomUUID().toString() })
    }
    if(categoryId!=null) MposDialog(if(categoryRevision==null) "Новая категория" else "Изменить категорию", { categoryId=null }, !state.busy) {
        if(state.error && state.message!=null) MposNotice(state.message!!, true)
        MposField(categoryName, { categoryName=it;categoryCommand=UUID.randomUUID().toString(); model.dismissMessage() }, "Название категории", Modifier.testTag("categoryName"), !state.busy)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена", { categoryId=null;model.dismissMessage() }, enabled=!state.busy, style=ActionStyle.Secondary)
            MposButton("Сохранить категорию", { model.categorySave(categoryId!!,categoryRevision,categoryName,categoryCommand) { categoryId=null } }, busy=state.busy)
        }
        if(categoryRevision!=null) MposButton("Удалить категорию", { deleteCategory=true }, enabled=!state.busy, style=ActionStyle.Danger)
    }
    if(confirmDiscard) MposDialog("Несохранённые изменения", { confirmDiscard=false }) {
        Text("Отбросить изменения карточки?")
        MposButton("Продолжить редактирование", { confirmDiscard=false }, style=ActionStyle.Secondary)
        MposButton("Отбросить", { confirmDiscard=false; model.discard() }, style=ActionStyle.Danger)
    }
    if(confirmDelete) MposDialog("Удаление товара", { confirmDelete=false }) {
        Text("Удалить товар без складской истории?")
        MposButton("Удалить", { confirmDelete=false;model.deleteProduct() }, style=ActionStyle.Danger)
    }
    if(deleteCategory) MposDialog("Удаление категории", { deleteCategory=false }) {
        Text("Удалить пустую категорию? Категория с товарами будет сохранена.")
        MposButton("Удалить", {
            deleteCategory=false
            state.categories.find { it.id==categoryId }?.let { model.categoryDelete(it) { categoryId=null } }
        }, style=ActionStyle.Danger)
    }
}

@Composable fun ComponentGallery(onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        MposButton("Назад", onBack, style=ActionStyle.Secondary)
        Text("Компоненты M-POS", style=MaterialTheme.typography.titleLarge)
        MposButton("Основная кнопка", {})
        MposButton("Вторичная кнопка", {}, style=ActionStyle.Secondary)
        MposButton("Опасное действие", {}, style=ActionStyle.Danger)
        MposButton("Недоступно", {}, enabled=false)
        MposButton("Сохранение", {}, busy=true)
        MposField(text,{text=it},"Поле ввода")
        MposNotice("Изменения сохранены")
        MposNotice("Пример сообщения об ошибке",true)
        MposButton("Пример окна", {dialog=true})
    }
    if(dialog) MposDialog("Общее окно", {dialog=false}) {
        Text("Одинаковые заголовок, кнопка закрытия и действия во всех разделах.")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена",{dialog=false},style=ActionStyle.Secondary)
            MposButton("Подтвердить",{dialog=false})
        }
    }
}
