package com.mendelev.mpos.feature.products

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.CategoryEntity
import com.mendelev.mpos.data.local.CategoryChannels
import com.mendelev.mpos.domain.catalog.CategoryAppearance
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.catalog.unitLabel
import com.mendelev.mpos.domain.catalog.quantityUnit
import java.util.UUID

@Composable fun CatalogScreen(model: CatalogViewModel, onSettings: () -> Unit, onGallery: () -> Unit, onWorkspace: () -> Unit = {}, onShift: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val notice by model.notices.collectAsStateWithLifecycle()
    val administration by com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    LaunchedEffect(model,administration.generation) {model.reopenForms()}
    var photoSession by rememberSaveable(administration.generation) { mutableStateOf<String?>(null) }
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val session=photoSession
        photoSession=null
        if(uri!=null && session!=null) model.importPhoto(session) {
            context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Photo stream unavailable")
        }
    }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var categories by rememberSaveable(administration.generation) { mutableStateOf(false) }
    var categoryId by rememberSaveable(administration.generation) { mutableStateOf<String?>(null) }
    var categoryMenu by rememberSaveable(administration.generation) {mutableStateOf(true)}
    var categoryOrder by rememberSaveable(administration.generation) {mutableStateOf(true)}
    var categoryName by rememberSaveable(administration.generation) { mutableStateOf("") }
    var categoryColor by rememberSaveable(administration.generation) { mutableStateOf(CategoryAppearance.DEFAULT_COLOR) }
    var categorySymbol by rememberSaveable(administration.generation) { mutableStateOf("") }
    var categoryRevision by rememberSaveable(administration.generation) { mutableStateOf<Long?>(null) }
    var categoryCommand by rememberSaveable(administration.generation) { mutableStateOf("") }
    var nextProduct by rememberSaveable(administration.generation) {mutableStateOf<String?>(null)}
    var confirmDiscard by rememberSaveable(administration.generation) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(administration.generation) { mutableStateOf(false) }
    var deleteCategory by rememberSaveable(administration.generation) { mutableStateOf(false) }
    fun closeEditor() { nextProduct=null;if(!state.busy) { if(state.draft?.dirty == true) confirmDiscard=true else model.discard() } }
    BackHandler(enabled=state.draft!=null) { closeEditor() }
    val d = state.draft.takeIf {administration.active}
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar(subtitle="Локальный владелец", navigationEnabled=!state.busy) {
            if(d==null) {
                MposNavigationAction("Рабочая зона",onWorkspace)
                MposNavigationAction("Смена",onShift)
                if(com.mendelev.mpos.BuildConfig.DEBUG) MposNavigationAction("Компоненты",onGallery)
                MposNavigationAction("Настройки",onSettings)
            }
        }
        if(state.loading) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        else if(state.fatal) Column(Modifier.padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            MposNotice(state.message ?: "Каталог недоступен", true); MposButton("Повторить", model::retryOpen)
        } else if(d!=null) {
            ProductEditorScreen(model,state,d,{closeEditor()},{confirmDelete=true},onOpenProduct={id->
                nextProduct=id
                if(d.dirty) confirmDiscard=true else model.discardAndContinue {state.products.find {it.product.id==id}?.let {model.edit(it)};nextProduct=null}
            }) {
                photoSession=d.sessionId;photoPicker.launch("image/*")
            }
        } else {
            CatalogPage(state,search,{search=it},filter,{filter=it},administration.active,{categories=true},{model.edit()},{model.edit(it)})
        }
    }
    MposTopNotice(notice?.takeUnless {state.error})
    }
    fun openCategory(c: CategoryEntity?) {
        categoryMenu=state.categoryChannels[c?.id]?.menu ?: true;categoryOrder=state.categoryChannels[c?.id]?.order ?: true
        categoryId=c?.id ?: UUID.randomUUID().toString();categoryRevision=c?.revision;categoryName=c?.name.orEmpty();categoryColor=c?.color ?: CategoryAppearance.DEFAULT_COLOR;categorySymbol=c?.symbol.orEmpty();categoryCommand=UUID.randomUUID().toString();model.dismissMessage()
    }
    if(categories && categoryId==null) CategoryListWindow(state.categories,state.products.groupingBy {it.product.categoryId}.eachCount(),!state.busy,administration.active,state.message.takeIf {state.error},
        {categories=false},{openCategory(null)},{openCategory(it)},{openCategory(it);deleteCategory=true},{id,direction->model.moveCategory(id,direction)},state.categoryChannels,model::categoryChannel)
    if(administration.active && categoryId!=null && !deleteCategory) MposWebModal(if(categoryRevision==null) "Новая категория" else "Изменить категорию", { categoryId=null }, !state.busy) {
        if(state.error && state.message!=null) MposNotice(state.message!!, true)
        Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            MposWebField(categoryName,{categoryName=it;categoryCommand=UUID.randomUUID().toString();model.dismissMessage()},"Название категории","categoryName",!state.busy,placeholder="Например, Напитки")
            MposWebField(categorySymbol,{categorySymbol=it;categoryCommand=UUID.randomUUID().toString();model.dismissMessage()},"Обозначение на плитке","categorySymbol",!state.busy,placeholder="До 3 символов: ☕ / К / КФ")
            Text("Например: ☕, К или КФ. Пустое обозначение допустимо.",style=MaterialTheme.typography.bodySmall)
            Text("Цвет категории",style=MaterialTheme.typography.titleSmall)
            MposColorPicker(CategoryAppearance.palette,categoryColor,{
                categoryColor=it;categoryCommand=UUID.randomUUID().toString();model.dismissMessage()
            },enabled=!state.busy && administration.active)
            listOf("Онлайн меню" to "Показывать категорию в меню для просмотра.","Онлайн заказ" to "Разрешить товары категории для онлайн-заказа.").forEach {(title,note)->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {Text(title,fontWeight=FontWeight.Bold);Text(note,style=MaterialTheme.typography.bodySmall)}
                    val menu=title=="Онлайн меню"
                    Switch(if(menu) categoryMenu else categoryOrder,{if(menu) categoryMenu=it else categoryOrder=it;categoryCommand=UUID.randomUUID().toString()},enabled=!state.busy)
                }
            }
            if(categoryRevision!=null) Text("При переименовании категория изменится у всех товаров, которые к ней относятся.",style=MaterialTheme.typography.bodySmall)
        }
        MposWebActions({categoryId=null;model.dismissMessage()},"Сохранить",{model.categorySave(categoryId!!,categoryRevision,categoryName,categoryCommand,categoryColor,categorySymbol,CategoryChannels(categoryMenu,categoryOrder)) {categoryId=null}},!state.busy,tag="categorySave")
        if(categoryRevision!=null) TextButton({deleteCategory=true},enabled=!state.busy,modifier=Modifier.testTag("categoryEditorDelete")) {Text("Удалить категорию",color=MaterialTheme.colorScheme.error)}
    }
    if(administration.active && confirmDiscard) MposWebModal("Сохранить изменения?", { confirmDiscard=false }) {
        Text("В карточке есть несохранённые изменения.")
        MposPosControl("Сохранить и продолжить",{confirmDiscard=false;val target=nextProduct;model.saveAndContinue {target?.let {id->state.products.find {it.product.id==id}?.let {model.edit(it)}};nextProduct=null}},Modifier.fillMaxWidth(),!state.busy,PosControl.Cash)
        MposPosControl("Не сохранять",{confirmDiscard=false;val target=nextProduct;model.discardAndContinue {target?.let {id->state.products.find {it.product.id==id}?.let {model.edit(it)}};nextProduct=null}},Modifier.fillMaxWidth(),!state.busy,PosControl.Secondary)
        MposPosControl("Остаться в карточке",{confirmDiscard=false},Modifier.fillMaxWidth(),!state.busy,PosControl.Outline)
    }
    if(administration.active && confirmDelete) MposWebModal("Удаление товара",{confirmDelete=false},!state.busy) {
        Text("Удалить «${d?.name.orEmpty()}»?")
        Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposPosControl("Отмена",{confirmDelete=false},Modifier.weight(1f),!state.busy,PosControl.Secondary)
            MposPosControl("Удалить",{confirmDelete=false;model.deleteProduct()},Modifier.weight(1f),!state.busy,PosControl.Delete)
        }
    }
    if(administration.active && deleteCategory) MposWebModal("Удаление категории",{deleteCategory=false;categoryId=null},!state.busy) {
        Text("Удалить «$categoryName»?")
        Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposPosControl("Отмена",{deleteCategory=false;categoryId=null},Modifier.weight(1f),!state.busy,PosControl.Secondary)
            MposPosControl("Удалить",{
                deleteCategory=false
                state.categories.find {it.id==categoryId}?.let {model.categoryDelete(it) {categoryId=null}}
            },Modifier.weight(1f),!state.busy,PosControl.Delete)
        }
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
