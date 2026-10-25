package com.mendelev.mpos.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import java.util.UUID

private fun money(value: String)=value.replace('.',',')+" BYN"

@Composable fun WorkspaceScreen(model: WorkspaceViewModel, photos: ProductPhotoStore, onCatalog: () -> Unit, onSettings: () -> Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val s=ui.snapshot
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    var configId by rememberSaveable { mutableStateOf<String?>(null) }
    var configOwner by rememberSaveable { mutableStateOf("") }
    var configRevision by rememberSaveable { mutableStateOf(0L) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var manual by rememberSaveable { mutableStateOf("") }
    var manageId by rememberSaveable { mutableStateOf<String?>(null) }
    var manageType by rememberSaveable { mutableStateOf("") }
    var manageName by rememberSaveable { mutableStateOf("") }
    var moveFolder by rememberSaveable { mutableStateOf("") }
    var manageRevision by rememberSaveable { mutableStateOf(0L) }
    var confirmFolderDelete by rememberSaveable { mutableStateOf(false) }
    var quantityId by rememberSaveable { mutableStateOf<String?>(null) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var quantityOwner by rememberSaveable { mutableStateOf("") }
    var clearOwner by rememberSaveable { mutableStateOf("") }
    var quantityRevision by rememberSaveable { mutableStateOf(0L) }
    var revalidationToken by rememberSaveable { mutableStateOf<String?>(null) }
    var revalidationOwner by rememberSaveable { mutableStateOf("") }
    var revalidationRevision by rememberSaveable { mutableStateOf(0L) }
    var revalidationOpen by rememberSaveable { mutableStateOf(false) }
    var ordersDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var clearRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    val busy=ui.pending>0
    val modal=revalidationOpen || configId!=null || manageId!=null || quantityId!=null || clearRevision!=null || ordersDialog!=null
    fun configure(p: WorkspaceProduct) {
        if(editing) {
            manageId=p.id;manageType="product";manageName=p.name
            moveFolder=s?.layout?.find { it.type=="product" && it.id==p.id }?.folderId ?: ""
            manageRevision=s?.layoutRevision ?: 0L
        } else if(p.groups.isNotEmpty() || p.price.toBigDecimal().signum()==0) {
            configId=p.id;configRevision=p.revision;configOwner=s?.cart?.sessionId ?: "";selected=emptyList();manual="";model.dismiss()
        } else model.add(p,expectedOwner=s?.cart?.sessionId)
    }
    fun manage(item: LayoutItem) {
        manageId=item.id;manageType=item.type;manageName=item.name;moveFolder=item.folderId
        manageRevision=s?.layoutRevision ?: 0L;confirmFolderDelete=false;model.dismiss()
    }
    LaunchedEffect(s?.layoutRevision,s?.products,s?.categories) {
        if(category!=null && category!="" && s!=null && s.categories.none { it.id==category }) { category=null;folder=null }
        if(folder!=null && s!=null && s.layout.none { it.type=="folder" && it.id==folder }) folder=null
    }
    BackHandler(enabled=!modal && (folder!=null || category!=null || editing)) {
        when { folder!=null -> folder=null; editing -> editing=false;else -> {category=null;search=""} }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("M-POS · Рабочая зона") {
            MposNavigationAction("Товары",onCatalog)
            MposNavigationAction("Настройки",onSettings)
        }
        if(ui.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        else if(ui.fatal || s==null) MposNotice(ui.message ?: "Рабочая зона недоступна",true)
        else {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(category!=null) MposButton("Все категории",{category=null;folder=null;search=""},style=ActionStyle.Secondary)
                Text(if(category==null) "Категории" else s.categories.find { it.id==category }?.name ?: "Без категории",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                MposButton(if(editing) "Завершить раскладку" else "Изменить раскладку",{editing=!editing},enabled=!busy,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspaceEdit"))
            }
            MposField(search,{search=it},"Поиск товара",Modifier.padding(horizontal=20.dp).testTag("workspaceSearch"),!busy)
            if(editing) Text("Режим раскладки: нажатие на товар открывает настройку размещения",Modifier.padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall)
            if(ui.message!=null && !modal && folder==null) MposNotice(ui.message!!,true)
            BoxWithConstraints(Modifier.weight(1f).padding(20.dp)) {
                val grid: @Composable (Modifier)->Unit = { modifier ->
                    Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(editing && category!=null && search.isBlank()) MposButton("Создать папку",{
                            manageId=UUID.randomUUID().toString();manageType="folder";manageName="";manageRevision=s.layoutRevision;confirmFolderDelete=false
                        },enabled=!busy,style=ActionStyle.Secondary)
                        WorkspaceGrid(s,category,null,search,editing,photos,Modifier.weight(1f),ui.pending<100,
                            {category=it;search=""},{folder=it.id},::configure,::manage)
                    }
                }
                val cart: @Composable (Modifier)->Unit = { modifier ->
                    CartPanel(s,ui.pending,modifier,{id -> quantityId=id;quantity=s.cart.lines.first { it.id==id }.quantity;quantityRevision=s.cartRevision;quantityOwner=s.cart.sessionId;model.dismiss()},
                        {id,delta -> model.step(id,delta,s.cart.sessionId)},{id -> model.remove(id,s.cart.sessionId)},{clearRevision=s.cartRevision;clearOwner=s.cart.sessionId},{ordersDialog="details"},{ordersDialog="park"},{ordersDialog="list"},{
                            revalidationOwner=s.cart.sessionId;revalidationRevision=s.cartRevision
                            revalidationToken=runCatching { CartRevalidator.preview(s.cart,s.products,s.graph,s.balances,OrderReservations.total(s.parked.map {it.cart})).token }.getOrNull()
                            revalidationOpen=true;model.dismiss()
                        })
                }
                if(maxWidth>=840.dp) Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) { grid(Modifier.weight(1f));cart(Modifier.width(340.dp).fillMaxHeight()) }
                else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) { grid(Modifier.weight(1f));cart(Modifier.fillMaxWidth().height(300.dp)) }
            }
        }
    }
    if(s!=null) {
        if(revalidationOpen) WorkspaceRevalidationDialog(s,ui,revalidationOwner,revalidationRevision,revalidationToken,
            {revalidationOpen=false;model.dismiss()},{token -> model.revalidate(revalidationRevision,revalidationOwner,token) {revalidationOpen=false}})
        WorkspaceOrdersDialogs(ordersDialog,{ordersDialog=it},s,ui,model)
        val folderItem=s.layout.find { it.type=="folder" && it.id==folder }
        if(folderItem!=null && !modal) {
            val count=s.layout.count {it.type=="product" && it.folderId==folderItem.id}
            val columns=count.coerceIn(1,4)
            val width=listOf(280,448,660,872)[columns-1].dp
            MposDialog(folderItem.name,{folder=null},!busy,maxWidth=width) {
                if(ui.message!=null) MposNotice(ui.message!!,true)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fittingColumns=((maxWidth+12.dp)/162.dp).toInt().coerceAtLeast(1).coerceAtMost(columns)
                    val rows=(count+fittingColumns-1)/fittingColumns
                    val gridHeight=if(count==0) 72 else (rows*144+(rows-1)*12).coerceAtMost(420)
                    WorkspaceGrid(s,folderItem.categoryId,folderItem.id,"",editing,photos,Modifier.heightIn(max=gridHeight.dp),ui.pending<100,
                        {},{},::configure,::manage,folderColumns=fittingColumns)
                }
            }
        }
        val product=s.products.find { it.id==configId }
        if(configId!=null) MposDialog(product?.name ?: "Товар недоступен",{configId=null;model.dismiss()},!busy) {
            if(ui.message!=null) MposNotice(ui.message!!,true)
            if(product==null || product.revision!=configRevision) MposNotice("Товар изменился. Закройте окно и выберите его заново",true)
            else {
                Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    product.groups.forEach { g ->
                        Text("${g.name}: от ${g.minimum} до ${g.maximum}",style=MaterialTheme.typography.titleSmall)
                        g.options.forEach { o -> MposFlag("${o.posName.ifBlank { s.products.find { it.id==o.productId }?.name ?: "Недоступен" }} (${money(o.priceDelta)})",
                            o.id in selected,{ checked ->
                                selected=if(!checked) selected-o.id else if(g.maximum=="1") selected.filterNot { id -> g.options.any { it.id==id } }+o.id else selected+o.id
                                model.dismiss()
                            },!busy) }
                    }
                    if(product.price.toBigDecimal().signum()==0) MposField(manual,{manual=it;model.dismiss()},"Цена продажи, BYN",Modifier.testTag("workspaceManualPrice"),!busy,numeric=true)
                    val pricePreview=runCatching {
                        val base=if(product.price.toBigDecimal().signum()==0) CatalogRules.decimal(manual,2,"Цена").toBigDecimal().also {
                            if(it.signum()<=0) throw CatalogFailure("price","Укажите цену больше 0")
                        } else product.price.toBigDecimal()
                        ModifierRules.selectionPrice(base,product.groups,selected.toSet(),requireComplete=false)
                    }
                    Text(pricePreview.getOrNull()?.let {"Итого: ${money(CatalogRules.canonical(it))}"} ?: "Укажите корректную цену для расчёта суммы",
                        style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("workspaceConfigurationTotal"))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Отмена",{configId=null;model.dismiss()},enabled=!busy,style=ActionStyle.Secondary)
                    MposButton("Добавить в заказ",{model.add(product,selected.toSet(),manual.takeIf { product.price.toBigDecimal().signum()==0 },configOwner) {configId=null}},busy=busy)
                }
            }
        }
        if(manageId!=null) MposDialog(if(confirmFolderDelete) "Удалить папку?" else if(manageType=="folder") "Папка" else "Размещение товара",
            {if(!busy) {manageId=null;confirmFolderDelete=false;model.dismiss()}},!busy) {
            if(ui.message!=null) MposNotice(ui.message!!,true)
            val id=manageId!!
            if(confirmFolderDelete) {
                Text("Товары вернутся в корень категории")
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Отмена",{confirmFolderDelete=false},enabled=!busy,style=ActionStyle.Secondary)
                    MposButton("Удалить папку",{model.layout(LayoutAction.DeleteFolder(id),manageRevision){manageId=null;confirmFolderDelete=false}},busy=busy,style=ActionStyle.Danger)
                }
            } else {
                if(manageType=="folder") MposField(manageName,{manageName=it;model.dismiss()},"Название папки",Modifier.testTag("workspaceFolderName"),!busy)
                else { Text(manageName);MposSelect("Папка",moveFolder,listOf("" to "Корень категории")+s.layout.filter { it.type=="folder" && it.categoryId==category }.map {it.id to it.name},{moveFolder=it},!busy) }
                val item=s.layout.find { it.id==id && it.type==manageType }
                if(item!=null) {
                    val siblings=s.layout.filter { it.categoryId==item.categoryId && it.folderId==item.folderId }
                    val index=siblings.indexOf(item)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        MposButton("Раньше",{model.layout(LayoutAction.Step(item.type,id,-1),manageRevision){manageId=null}},enabled=!busy && index>0,style=ActionStyle.Secondary)
                        MposButton("Позже",{model.layout(LayoutAction.Step(item.type,id,1),manageRevision){manageId=null}},enabled=!busy && index<siblings.lastIndex,style=ActionStyle.Secondary)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Отмена",{manageId=null;model.dismiss()},enabled=!busy,style=ActionStyle.Secondary)
                    MposButton(if(manageType=="folder") "Сохранить папку" else "Переместить",{
                        val action=if(manageType=="folder") LayoutAction.SaveFolder(id,category ?: "",manageName) else LayoutAction.MoveProduct(id,moveFolder)
                        model.layout(action,manageRevision){manageId=null}
                    },busy=busy)
                }
                if(manageType=="folder" && item!=null) MposButton("Открыть папку",{folder=id;manageId=null},enabled=!busy,style=ActionStyle.Secondary)
                if(manageType=="folder" && item!=null) MposButton("Удалить папку",{confirmFolderDelete=true},enabled=!busy,style=ActionStyle.Danger)
                if(manageType=="product") MposButton("Сбросить размещение",{model.layout(LayoutAction.ResetProduct(id),manageRevision){manageId=null}},enabled=!busy,style=ActionStyle.Secondary)
            }
        }
        if(quantityId!=null) MposDialog("Количество",{quantityId=null;model.dismiss()},!busy) {
            if(ui.message!=null) MposNotice(ui.message!!,true)
            MposField(quantity,{quantity=it;model.dismiss()},"Количество в базовых единицах",Modifier.testTag("workspaceQuantity"),!busy,numeric=true)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MposButton("Отмена",{quantityId=null;model.dismiss()},enabled=!busy,style=ActionStyle.Secondary)
                MposButton("Применить количество",{model.quantity(quantityId!!,quantity,quantityRevision,quantityOwner){quantityId=null}},busy=busy)
            }
        }
        if(clearRevision!=null) MposDialog("Очистить текущий заказ?",{clearRevision=null},!busy) {
            Text("Все позиции будут удалены из текущего заказа")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MposButton("Отмена",{clearRevision=null},enabled=!busy,style=ActionStyle.Secondary)
                MposButton("Очистить заказ",{model.clear(clearRevision!!,clearOwner);clearRevision=null},busy=busy,style=ActionStyle.Danger)
            }
        }
    }
}

@Composable private fun WorkspaceGrid(s: WorkspaceSnapshot,category: String?,folder: String?,search: String,editing: Boolean,
    photos: ProductPhotoStore,modifier: Modifier,enabled: Boolean,onCategory: (String)->Unit,onFolder: (LayoutItem)->Unit,
    onProduct: (WorkspaceProduct)->Unit,onManage: (LayoutItem)->Unit,folderColumns: Int? = null) {
    val query=CatalogRules.key(search)
    val products=s.products.associateBy { it.id }
    val availability=remember(s.products,s.cart,s.graph,s.balances,s.parked) {
        s.products.associate { p -> p.id to runCatching {
            if(s.cart.lines.any { CartEngine.stale(it,s.products) }) "Проверьте текущий заказ"
            else if(RecipeCalculator(s.graph).shortages(CartEngine.requests(s.cart)+RecipeRequest(p.id,java.math.BigDecimal.ONE,quantityUnit(p.unit)),s.balances,OrderReservations.total(s.parked.map {it.cart})).isEmpty()) "Доступен"
            else "Недостаточно для 1 ${unitLabel(p.unit)}"
        }.getOrDefault("Требуется проверка состава") }
    }
    val tiles=if(query.isNotEmpty()) s.products.filter { (category==null || it.categoryId==category) && CatalogRules.key(it.name).contains(query) }.map { LayoutItem("product",it.id,it.categoryId) }
        else s.layout.filter { it.categoryId==category && it.folderId==(folder ?: "") }
    LazyVerticalGrid(if(folderColumns==null) GridCells.Adaptive(150.dp) else GridCells.Fixed(folderColumns),modifier.testTag(if(folder==null) "workspaceGrid" else "workspaceFolderGrid"),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(category==null && query.isEmpty()) {
            items(s.categories,key={"category:${it.id}"}) { c -> MposWorkspaceTile({onCategory(c.id)},Modifier.testTag("workspaceCategory:${c.id}")) {
                MposCategoryBadge(c.color,c.symbol);Text(c.name,style=MaterialTheme.typography.titleMedium)
                Text("Товаров: ${s.products.count { it.categoryId==c.id }}",style=MaterialTheme.typography.bodySmall)
            } }
            if(s.products.any { it.categoryId.isEmpty() }) item { MposWorkspaceTile({onCategory("")},Modifier.testTag("workspaceCategory:")) { Text("Без категории",style=MaterialTheme.typography.titleMedium) } }
            if(s.categories.isEmpty() && s.products.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Text("Добавьте товары в каталоге") }
        } else {
            if(folder!=null && tiles.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {Text("В папке пока нет товаров")}
            items(tiles,key={"${it.type}:${it.id}"}) { tile ->
                if(tile.type=="folder") MposWorkspaceTile({if(editing) onManage(tile) else onFolder(tile)},Modifier.testTag("workspaceFolder:${tile.id}"),enabled) {
                    Text("Папка",style=MaterialTheme.typography.labelMedium);Text(tile.name,style=MaterialTheme.typography.titleMedium)
                    Text("Товаров: ${s.layout.count { it.type=="product" && it.folderId==tile.id }}",style=MaterialTheme.typography.bodySmall)
                } else products[tile.id]?.let { p ->
                    MposWorkspaceTile({onProduct(p)},Modifier.testTag("workspaceProduct:${p.id}"),enabled) {
                        if(p.photoId!=null && folder==null) ProductPhoto(p.photoId,photos,Modifier.fillMaxWidth().height(72.dp))
                        Text(p.name,style=MaterialTheme.typography.titleSmall)
                        Text(if(p.price.toBigDecimal().signum()==0) "Ввести цену" else money(p.price),style=MaterialTheme.typography.bodyMedium)
                        if(editing) Text("Настроить размещение",style=MaterialTheme.typography.bodySmall)
                        else {
                            Text(availability[p.id] ?: "",style=MaterialTheme.typography.bodySmall)
                            if(p.groups.isNotEmpty()) Text("Выбор модификаторов",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if(tiles.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Text(if(query.isEmpty()) "Здесь пока нет товаров" else "Ничего не найдено") }
        }
    }
}

@Composable private fun CartPanel(s: WorkspaceSnapshot,pending: Int,modifier: Modifier,onQuantity: (String)->Unit,
    onStep: (String,Int)->Unit,onRemove: (String)->Unit,onClear: ()->Unit, onDetails: ()->Unit,onPark: ()->Unit,onParked: ()->Unit,onRevalidate: ()->Unit) {
    Surface(modifier,shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Текущий заказ",style=MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f).testTag("workspaceCart"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item(key="details") {
                    Text(listOf(s.cart.details.label,OrderRules.typeLabel(s.cart.details.type)).filter {it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                    if(s.cart.details.comment.isNotBlank()) Text(s.cart.details.comment,style=MaterialTheme.typography.bodySmall,maxLines=2)
                }
                items(s.cart.lines,key={it.id}) { line ->
                    Column(Modifier.testTag("workspaceLine:${line.id}"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(line.name,style=MaterialTheme.typography.titleSmall)
                        if(line.options.isNotEmpty()) Text(line.options.joinToString { it.name },style=MaterialTheme.typography.bodySmall)
                        Text("${money(line.unitPrice)} × ${line.quantity} ${unitLabel(line.unit)}")
                        if(CartEngine.stale(line,s.products)) Text("Требуется проверка: каталог изменился",color=MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            MposButton("−",{onStep(line.id,-1)},enabled=pending<100,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspaceMinus:${line.id}"))
                            MposButton(line.quantity,{onQuantity(line.id)},enabled=pending==0,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspaceEditQuantity:${line.id}"))
                            MposButton("+",{onStep(line.id,1)},enabled=pending<100,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspacePlus:${line.id}"))
                        }
                        MposButton("Удалить позицию",{onRemove(line.id)},enabled=pending==0,style=ActionStyle.Danger,modifier=Modifier.testTag("workspaceRemove:${line.id}"))
                        HorizontalDivider()
                    }
                }
                if(s.cart.lines.isEmpty()) item { Text("Заказ пуст. Выберите товар на плитке") }
            }
            Text("Предварительно: ${money(CatalogRules.canonical(CartEngine.total(s.cart)))}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("workspaceTotal"))
            if(pending>0) Text("Сохраняем…",style=MaterialTheme.typography.bodySmall)
            if(s.cart.lines.any {CartEngine.stale(it,s.products)}) MposButton("Пересчитать заказ",onRevalidate,enabled=pending==0,modifier=Modifier.testTag("workspaceRevalidate"))
            OrderButtons(pending,s,onDetails,onPark,onParked,onClear)
        }
    }
}
