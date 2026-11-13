package com.mendelev.mpos.feature.receiving

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.catalog.unitLabel
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.purchasing.purchaseDate
import com.mendelev.mpos.feature.purchasing.units
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReceivingScreen(model: ReceivingViewModel,posting: ReceivingPostingViewModel,requestedOrder: String?,consumeRequest: ()->Unit,
    onSection: ((MposFolderTab)->Unit)?=null,onBack: ()->Unit) {
    MposSupplyTheme {
    val administration by com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle();val d=ui.form;val snapshot=ui.snapshot
    // Capture nullable content before Dialog creates a separate composition.
    val preview=ui.preview
    val post by posting.state.collectAsStateWithLifecycle()
    val locked=ui.pending || ui.unresolved!=null || post.pending || post.unresolved!=null
    var expanded by rememberSaveable {mutableStateOf(false)}
    var requestedSection by rememberSaveable {mutableStateOf<MposFolderTab?>(null)}
    LaunchedEffect(d?.id,requestedSection) {
        if(d==null) requestedSection?.let {tab->requestedSection=null;if(onSection!=null) onSection(tab) else onBack()}
    }
    LaunchedEffect(d?.id) {if(d==null) expanded=false else if(d.order==null) expanded=true}
    var showHistory by rememberSaveable {mutableStateOf(false)}
    var detail by rememberSaveable {mutableStateOf<String?>(null)}
    var search by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(post.committed,post.pending) {
        if(post.committed!=null && !post.pending) {model.dismissPreview();model.refresh();expanded=false;posting.acknowledge();if(onSection!=null) onSection(MposFolderTab.History) else onBack()}
    }
    val order=snapshot?.orders?.find {it.id==d?.order?.id}
    val editable=!locked && !ui.blocked && ui.canEdit && (d?.order==null || order?.status=="pending")
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(model) {model.refresh();posting.refresh()}
    LaunchedEffect(administration.generation,post.pending,post.unresolved) {posting.reopenForms()}
    // A request is acknowledged only after the form is actually opened. Refresh may
    // set pending before this effect runs, even when its captured UI was still idle.
    LaunchedEffect(requestedOrder,d?.order?.id) {
        if(requestedOrder!=null && d?.order?.id==requestedOrder) consumeRequest()
    }
    LaunchedEffect(requestedOrder,ui.pending,ui.snapshot,locked,ui.blocked,d?.order?.id) {
        if(requestedOrder!=null && d?.order?.id!=requestedOrder && snapshot!=null && !locked && !ui.blocked && ui.canEdit) {
            posting.acknowledge();showHistory=false;model.open(requestedOrder)
        }
    }
    fun back() {if(!locked) {if(d==null) onBack() else if(d.order!=null && order?.status!="pending") {model.discard();model.close();onBack()} else {requestedSection=MposFolderTab.Supplies;model.saveAndExit()}}}
    BackHandler {back()}
    val notice: @Composable ()->Unit={
        if(post.message!=null) Text(post.message!!,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
        if(ui.message!=null) Text(ui.message!!,color=if(ui.blocked || ui.unresolved!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
        ui.autoError?.let {
            Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
            MposButton("Повторить сохранение",model::retryAutosave,enabled=!locked,style=ActionStyle.Secondary)
        }
        if(ui.pending || post.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(ui.blocked) MposButton("Перечитать",model::refresh,enabled=!locked)
        if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("receivingRetry"))
        if(post.unresolved!=null) MposButton("Проверить проведение",posting::retry,enabled=!post.pending,modifier=Modifier.testTag("receivingPostRetry"))
    }
    val document: @Composable ()->Unit={
        if(d!=null) ReceivingDocument(d,snapshot?.suppliers.orEmpty(),editable,
            onExit=::back,onNext=model::next,onSupplier=model::supplier,onMeta={next->model.change {next}},
            onLine=model::line,onUnit=model::unit,onMode=model::priceMode,onAdd={search="";dialog="product"},onRemove=model::remove,
            navigationEnabled=!locked,suggested=ui.suggestedAmounts,onReviewAmount=model::reviewAmount,onAmount=model::amount)
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().semantics {testTagsAsResourceId=true}.testTag("receivingScreen")) {
        MposAppBar("Приёмка",navigationEnabled=!locked && dialog==null && preview==null) {}
            MposFolderPanel(MposFolderTab.Supplies,{tab->
                if(!locked) {if(d==null) {if(onSection!=null) onSection(tab) else onBack()} else {requestedSection=tab;model.saveAndExit()}}
            },Modifier.weight(1f).fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),enabled=!locked && preview==null) {
                notice()
                if(d!=null) document()
                else if(!locked && requestedOrder==null && requestedSection==null) {
                    MposSupplyAction("Поставки",{if(onSection!=null) onSection(MposFolderTab.Supplies) else onBack()},kind=PosControl.Outline)
                }
            }
    }
    MposTopNotice(ui.transientNotice)
    }
    post.history.find {it.id==detail}?.let {ReceivingRecordWindow(it) {detail=null}}
    if(preview!=null && dialog==null) ReceivingConfirmationWindow(preview,locked,ui.dirty,model::dismissPreview) {
        posting.post(snapshot!!.ownerId,snapshot.records.single {it.orderId==d!!.key}.revision,d!!,preview.hasOverage)
    }
    if(dialog!=null) MposDialog("Добавить товар",{if(!locked) dialog=null},!locked) {
        when(dialog) {
            "product" -> {
                MposField(search,{search=it},"Поиск товара",Modifier.fillMaxWidth().testTag("receivingSearch"))
                Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    snapshot?.products.orEmpty().filter {CatalogRules.key(it.name).contains(CatalogRules.key(search)) && d?.lines?.none {l->l.product.id==it.id}==true}.take(100).forEach {p->
                        MposButton("${p.name} · ${unitLabel(p.unit)}",{model.add(p.id);dialog=null},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.fillMaxWidth().testTag("receivingPick:${p.id}"))
                    }
                    Text("Показано до 100 товаров; уточните поиск")
                }
            }
        }
    }
    }
}
