package com.mendelev.mpos.feature.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalDensity

private fun money(value: String)=value.replace('.',',')+" BYN"

@Composable fun WorkspaceScreen(model: WorkspaceViewModel, photos: ProductPhotoStore, onCatalog: () -> Unit, onSettings: () -> Unit, onShift: () -> Unit = {}, onCheckout: () -> Unit = {}, onHistory: () -> Unit = {}) {
    val administration by com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle()
    val s=ui.snapshot
    var comingSoon by rememberSaveable {mutableStateOf<String?>(null)}
    var searchOpen by rememberSaveable {mutableStateOf(false)}
    val navigation=LocalMposTopNavigation.current
    val shiftOpen=navigation?.shiftOpen ?: true
    var compactOrder by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable(administration.generation) { mutableStateOf(false) }
    var configId by rememberSaveable { mutableStateOf<String?>(null) }
    var configOwner by rememberSaveable { mutableStateOf("") }
    var configRevision by rememberSaveable { mutableStateOf(0L) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var manual by rememberSaveable { mutableStateOf("") }
    var manageId by rememberSaveable(administration.generation) { mutableStateOf<String?>(null) }
    var manageType by rememberSaveable(administration.generation) { mutableStateOf("") }
    var manageName by rememberSaveable(administration.generation) { mutableStateOf("") }
    var moveFolder by rememberSaveable(administration.generation) { mutableStateOf("") }
    var manageRevision by rememberSaveable(administration.generation) { mutableStateOf(0L) }
    var confirmFolderDelete by rememberSaveable(administration.generation) { mutableStateOf(false) }
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
    val modal=comingSoon!=null || revalidationOpen || configId!=null || manageId!=null || quantityId!=null || clearRevision!=null || ordersDialog!=null
    // Lazy item callbacks can outlive a composition; always target the current persisted owner.
    val currentSnapshot by rememberUpdatedState(s)
    fun configure(p: WorkspaceProduct) {
        val active=currentSnapshot
        if(editing) {
            manageId=p.id;manageType="product";manageName=p.name
            moveFolder=active?.layout?.find { it.type=="product" && it.id==p.id }?.folderId ?: ""
            manageRevision=active?.layoutRevision ?: 0L
        } else if(p.groups.isNotEmpty() || p.price.toBigDecimal().signum()==0) {
            configId=p.id;configRevision=p.revision;configOwner=active?.cart?.sessionId ?: "";selected=emptyList();manual="";model.dismiss()
        } else model.add(p,expectedOwner=active?.cart?.sessionId)
    }
    fun manage(item: LayoutItem) {
        manageId=item.id;manageType=item.type;manageName=item.name;moveFolder=item.folderId
        manageRevision=s?.layoutRevision ?: 0L;confirmFolderDelete=false;model.dismiss()
    }
    LaunchedEffect(s?.layoutRevision,s?.products,s?.categories) {
        if(category!=null && category!="" && s!=null && s.categories.none { it.id==category }) { category=null;folder=null }
        if(folder!=null && s!=null && s.layout.none { it.type=="folder" && it.id==folder }) folder=null
    }
    BackHandler(enabled=!modal && (folder!=null || category!=null || editing || searchOpen)) {
        when { folder!=null -> folder=null; searchOpen -> {searchOpen=false;search=""}; editing -> editing=false;else -> {category=null;search=""} }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("M-POS · Рабочая зона", navigationEnabled=!busy && !modal) {
            MposNavigationAction("Товары",onCatalog)
            MposNavigationAction("Смена",onShift)
            MposNavigationAction("Настройки",onSettings)
        }
        if(ui.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        else if(ui.fatal || s==null) MposNotice(ui.message ?: "Рабочая зона недоступна",true)
        else {
            BoxWithConstraints(Modifier.weight(1f).padding(16.dp).testTag("workspaceBody")) {
                val config=LocalConfiguration.current
                val viewportWidth=config.screenWidthDp
                val portrait=config.screenHeightDp>viewportWidth
                val cols=when {viewportWidth<=600->2;viewportWidth<=700->3;portrait->4;viewportWidth>=900->5;else->4}
                val geometry=WorkspaceGeometry(cols,if(portrait && viewportWidth>=700) 5 else 4)
                val grid: @Composable (Modifier)->Unit = { modifier ->
                    CompositionLocalProvider(LocalWorkspaceGeometry provides geometry) {
                    Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(navigation!=null && !navigation.shiftLoading && !navigation.shiftUnavailable && !shiftOpen) {
                            Surface(shape=RoundedCornerShape(16.dp),color=if(androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF3A3022) else Color(0xFFFFF4E9)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Text("Чтобы принимать оплату, откройте кассовую смену.",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                                    PosAction("Открыть смену",onShift,tone=PosTone.Warning,enabled=!busy)
                                }
                            }
                        }
                        WorkspaceToolbar(if(category==null) "Рабочая зона" else s.categories.find {it.id==category}?.name ?: "Без категории",
                            category!=null,editing,busy,s.parked.size,{category=null;folder=null;search=""},
                            {if(administration.active) editing=!editing else comingSoon="Для изменения раскладки войдите в администрирование в настройках"},{ordersDialog="list"},{searchOpen=!searchOpen;if(!searchOpen) search=""},{comingSoon=it})
                        if(searchOpen) MposField(search,{search=it},"Поиск товара",Modifier.testTag("workspaceSearch"),!busy)
                        if(ui.message!=null && !modal && folder==null) MposNotice(ui.message!!,true)
                        if(editing && category!=null && search.isBlank()) PosAction("Создать папку",{
                            manageId=UUID.randomUUID().toString();manageType="folder";manageName="";manageRevision=s.layoutRevision
                        },enabled=!busy,tone=PosTone.Primary)
                        if(category==null && search.isBlank()) WorkspaceHome(s,ui,editing,model,photos,Modifier.weight(1f),{category=it;search=""},{configure(it)})
                        else WorkspaceGrid(s,category,null,search,editing,photos,Modifier.weight(1f),ui.pending<100,
                            {category=it;search=""},{folder=it.id},{configure(it)},{manage(it)},availabilityIndex=ui.availability,onReorder={tile,index->model.layout(LayoutAction.Reorder(tile.type,tile.id,index),s.layoutRevision)})
                    }
                    }
                }
                val cart: @Composable (Modifier)->Unit = { modifier ->
                    WorkspaceCartPanel(s,ui.pending,modifier,{id -> quantityId=id;quantity=s.cart.lines.first {it.id==id}.quantity;quantityRevision=s.cartRevision;quantityOwner=s.cart.sessionId;model.dismiss()},
                        {id,delta -> model.step(id,delta,s.cart.sessionId)},{id -> model.remove(id,s.cart.sessionId)},
                        {clearRevision=s.cartRevision;clearOwner=s.cart.sessionId},{ordersDialog="details"},{ordersDialog="park"},{
                            revalidationOwner=s.cart.sessionId;revalidationRevision=s.cartRevision
                            revalidationToken=runCatching {CartRevalidator.preview(s.cart,s.products,s.graph,s.balances,OrderReservations.total(s.parked.map {it.cart})).token}.getOrNull()
                            revalidationOpen=true;model.dismiss()
                        },onCheckout,{comingSoon=it},shiftOpen)
                }
                val scale=LocalDensity.current.fontScale.coerceIn(1f,2f)
                val cartWidth=(380*scale).dp
                if(maxWidth>=cartWidth+(420*scale).dp+16.dp) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    grid(Modifier.weight(1f));cart(Modifier.width(cartWidth).fillMaxHeight())
                } else if(maxHeight>=(650*scale).dp) Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    grid(Modifier.weight(1f));cart(Modifier.fillMaxWidth().height((300*scale).dp))
                } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        MposButton("Товары",{compactOrder=false},style=if(compactOrder) ActionStyle.Secondary else ActionStyle.Primary,modifier=Modifier.testTag("workspaceShowCatalog"))
                        MposButton("Заказ (${s.cart.lines.size})",{compactOrder=true},style=if(compactOrder) ActionStyle.Primary else ActionStyle.Secondary,modifier=Modifier.testTag("workspaceShowOrder"))
                    }
                    if(compactOrder) cart(Modifier.weight(1f).fillMaxWidth()) else grid(Modifier.weight(1f))
                }
            }
        }
    }
    if(comingSoon!=null) MposDialog("Скоро будет подключено",{comingSoon=null},!busy) {
        Text("${comingSoon}. Скоро будет подключено.")
        MposButton("Понятно",{comingSoon=null},style=ActionStyle.Secondary)
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
                        {},{},{configure(it)},{manage(it)},availabilityIndex=ui.availability,folderColumns=fittingColumns,onReorder={tile,index->model.layout(LayoutAction.Reorder(tile.type,tile.id,index),s.layoutRevision)})
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
        if(manageId!=null && administration.active) MposDialog(if(confirmFolderDelete) "Удалить папку?" else if(manageType=="folder") "Папка" else "Размещение товара",
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
    onProduct: (WorkspaceProduct)->Unit,onManage: (LayoutItem)->Unit,availabilityIndex: WorkspaceAvailability? = null,folderColumns: Int? = null,onReorder: ((LayoutItem,Int)->Unit)? = null) {
    val query=CatalogRules.key(search)
    val products=remember(s.products) {s.products.associateBy {it.id}}
    val projectionKey=remember(s.products,s.layout,query,category,folder) {Any()}
    val result by produceState<Pair<Any,List<LayoutItem>>?>(null,projectionKey) {
        value=projectionKey to withContext(Dispatchers.Default) {
            if(query.isNotEmpty()) s.products.filter {(category==null || it.categoryId==category) && CatalogRules.key(it.name).contains(query)}.map {LayoutItem("product",it.id,it.categoryId)}
            else s.layout.filter {it.categoryId==category && it.folderId==(folder ?: "")}
        }
    }
    val projection=result?.takeIf {it.first===projectionKey}?.second
    val tiles=projection.orEmpty()
    val gridState=rememberLazyGridState()
    val drag=workspaceDrag(gridState,editing && enabled && query.isEmpty() && category!=null && onReorder!=null,s.layoutRevision,"$category/$folder/${tiles.map {it.type to it.id}}") {from,to->
        tiles.getOrNull(from)?.let {onReorder?.invoke(it,to.coerceIn(0,tiles.lastIndex))}
    }
    LazyVerticalGrid(if(folderColumns==null) GridCells.Fixed(LocalWorkspaceGeometry.current.columns) else GridCells.Fixed(folderColumns),modifier.then(drag.modifier).testTag(if(folder==null) "workspaceGrid" else "workspaceFolderGrid"),state=gridState,
        contentPadding=PaddingValues(4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(category==null && query.isEmpty()) {
            items(s.categories,key={"category:${it.id}"}) { c -> MposWorkspaceTile({onCategory(c.id)},Modifier.height(144.dp).testTag("workspaceCategory:${c.id}"),categoryColor=c.color,symbol=c.symbol) {
                Text(c.name+"  ›",style=MaterialTheme.typography.titleSmall.copy(fontSize=14.5.sp),maxLines=2)
                Spacer(Modifier.weight(1f));Text("Категория",style=MaterialTheme.typography.bodySmall.copy(fontSize=11.5.sp))
            } }
            if(s.products.any { it.categoryId.isEmpty() }) item { MposWorkspaceTile({onCategory("")},Modifier.testTag("workspaceCategory:")) { Text("Без категории",style=MaterialTheme.typography.titleMedium) } }
            if(s.categories.isEmpty() && s.products.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Text("Добавьте товары в каталоге") }
        } else {
            if(folder!=null && tiles.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {Text("В папке пока нет товаров")}
            items(tiles,key={"${it.type}:${it.id}"}) { tile ->
                val highlight=if(drag.target==tiles.indexOf(tile)) Modifier.border(2.dp,MaterialTheme.colorScheme.primary,MaterialTheme.shapes.large) else Modifier
                if(tile.type=="folder") MposWorkspaceTile({if(editing) onManage(tile) else onFolder(tile)},Modifier.height(144.dp).testTag("workspaceFolder:${tile.id}").then(highlight),enabled) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Text(tile.name,style=MaterialTheme.typography.titleMedium.copy(fontSize=19.sp,fontWeight=FontWeight.ExtraBold),textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2)}
                } else products[tile.id]?.let { p ->
                    MposWorkspaceTile({onProduct(p)},Modifier.height(144.dp).testTag("workspaceProduct:${p.id}").then(highlight),enabled,compact=folder!=null) {
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(p.name,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall.copy(fontSize=if(folder!=null) 13.5.sp else 14.5.sp,lineHeight=18.sp),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if(p.photoId!=null && folder==null) ProductPhoto(p.photoId,photos,Modifier.size(32.dp))
                        }
                        if(editing) Text("Настроить размещение",style=MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        WorkspaceTileFooter(availabilityIndex,p)
                    }
                }
            }
            if(tiles.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Text(if(projection==null) "Ищем товары…" else if(query.isEmpty()) "Здесь пока нет товаров" else "Ничего не найдено") }
        }
    }
}
