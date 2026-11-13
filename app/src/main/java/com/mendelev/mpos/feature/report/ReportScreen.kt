package com.mendelev.mpos.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.File
import com.mendelev.mpos.domain.report.ReportPeriodSelection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import java.math.BigDecimal
import java.math.BigInteger

fun reportMoney(minor: BigInteger)=reportNumber(minor)+" BYN"

@Composable fun ReportScreen(model: ReportViewModel,onSettings: ()->Unit,onBack: ()->Unit,onStock: (()->Unit)?=null) {
    PanoramaTheme { ReportScreenBody(model,onSettings,onBack) }
}

@Composable private fun ReportScreenBody(model: ReportViewModel,onSettings: ()->Unit,onBack: ()->Unit) {
    androidx.activity.compose.BackHandler(onBack=onBack)
    val ui by model.state.collectAsStateWithLifecycle()
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val notices=remember(scope) {TransientMessages(scope)}
    val feedback by notices.state.collectAsStateWithLifecycle()
    val dark=isMposDarkTheme()
    var sharing by remember {mutableStateOf(false)}
    var exportJob by remember {mutableStateOf<Job?>(null)}
    var profitChart by rememberSaveable {mutableStateOf(false)}
    var periodDialog by rememberSaveable(admin.generation) {mutableStateOf(false)}
    var from by rememberSaveable(admin.generation) {mutableStateOf("")}
    var through by rememberSaveable(admin.generation) {mutableStateOf("")}
    LaunchedEffect(model,admin.generation) {model.open()}
    val report=ui.report?.takeIf {admin.active && ui.epoch==admin.generation}
    DisposableEffect(report,admin.generation) {onDispose {exportJob?.cancel()}}
    fun sharePdf(width: Int) {
        val captured=report ?: return
        if(sharing || ui.pending || captured.details==null || captured.consumption==null) return
        val session=Administration.session;val authorization=session.state.value
        if(!authorization.active) return
        sharing=true
        val options=AnalyticsPdf.Options(dark,width,profitChart)
        exportJob=scope.launch {
            var file: File?=null;var launched=false
            try {
                val job=currentCoroutineContext()
                withContext(Dispatchers.IO) {
                    file=AnalyticsPdf.newFile(context,captured)
                    AnalyticsPdf.write(context,requireNotNull(file),captured,options) {
                        job.ensureActive()
                        if(Administration.session!==session || session.state.value!=authorization) throw CancellationException("Administrative session changed")
                    }
                }
                ensureActive()
                if(session.state.value!=authorization || model.state.value.report!==captured) throw CancellationException("Report changed")
                context.startActivity(android.content.Intent.createChooser(AnalyticsPdf.shareIntent(context,requireNotNull(file)),"Поделиться отчётом"))
                launched=true
            } catch(e: CancellationException) {throw e}
            catch(_:Exception) {notices.show("Не удалось подготовить PDF. Повторите отправку")}
            finally {
                if(!launched) withContext(NonCancellable+Dispatchers.IO) {file?.delete()}
                sharing=false
            }
        }
    }
    val editPeriod={from=ui.from;through=ui.through;model.clearFilterError();periodDialog=true}
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        MposAppBar("Учёт и аналитика") {MposButton("Назад",onBack,style=ActionStyle.Secondary)}
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val effectiveWidth=maxWidth.value/LocalDensity.current.fontScale
            val exportWidth=maxWidth.value.toInt()
            LazyColumn(Modifier.fillMaxSize().testTag("reportScroll"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                item {
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("Учёт и аналитика",fontSize=25.sp,fontWeight=FontWeight.ExtraBold)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            val format=DateTimeFormatter.ofPattern("dd.MM.yyyy")
                            val dateLabel=runCatching {"${LocalDate.parse(ui.from).format(format)} — ${LocalDate.parse(ui.through).format(format)}"}.getOrDefault("Выбрать период")
                            val today=LocalDate.now(java.time.ZoneId.of(ui.zone))
                            val chosen=ReportPeriodSelection.preset(ui.from,ui.through,today)
                            ReportPeriodButton(dateLabel,periodDialog || chosen==null,admin.active,editPeriod,Modifier.testTag("reportFrom"))
                            listOf(1L to "Сегодня",7L to "7 дней",30L to "30 дней").forEach {(days,label)->
                                ReportPeriodButton(label,chosen==days,admin.active,{model.preset(days)},Modifier.testTag("reportPreset:$days"))
                            }
                            Surface(onClick={model.refresh()},enabled=admin.active && !ui.pending,shape=RoundedCornerShape(11.dp),
                                border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline),modifier=Modifier.size(40.dp).semantics {contentDescription="Обновить отчёт"}) {
                                Box(contentAlignment=Alignment.Center) {MposPosIcon("refresh",Modifier.size(17.dp))}
                            }
                            Surface(onClick={sharePdf(exportWidth)},enabled=report!=null && !ui.pending && !sharing && admin.active,
                                shape=RoundedCornerShape(11.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
                                modifier=Modifier.size(40.dp).semantics {contentDescription=if(sharing) "Подготовка PDF" else "Поделиться PDF"}) {
                                Box(contentAlignment=Alignment.Center) {
                                    if(sharing) CircularProgressIndicator(Modifier.size(17.dp),strokeWidth=2.dp)
                                    else MposPosIcon("share",Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
                if(!admin.active) item {
                    MposAnalyticsCard(Modifier.fillMaxWidth()) {
                        Text("Войдите в администрирование для просмотра денежных показателей",Modifier.testTag("reportLocked"))
                        MposButton("Открыть настройки",onSettings,Modifier.testTag("reportSettings"))
                    }
                } else if(ui.pending) item {Text("Загружаем отчёт…",Modifier.testTag("reportLoading"))}
                else if(ui.error!=null) item {
                    MposNotice(ui.error!!,true)
                    MposButton("Повторить чтение",{model.refresh()},Modifier.testTag("reportRetry"))
                } else if(report!=null) {
                    item { AnalyticsSummary(report,effectiveWidth>=900) }
                    item { AnalyticsPanorama(report,effectiveWidth>=900,profitChart,{profitChart=it}) }
                    item {
                        if(report.saleCount==0L && report.refundCount==0L) Text("За выбранный период продаж и возвратов нет",Modifier.testTag("reportEmpty"))
                    }
                }
                if(report!=null && !ui.pending && ui.error==null) {
                    val details=report.details
                    if(details==null) item {Text("Историческая детализация недоступна")}
                    else {
                        item { AnalyticsTables(report) }

                    }
                }
            }
        }
    }
    feedback?.let {MposTransientNotice(it,Modifier.align(Alignment.TopCenter).padding(top=90.dp))}
    }
    if(periodDialog && admin.active) MposDialog("Выбор периода",{periodDialog=false;model.clearFilterError()}) {
        Text("Введите даты в формате ГГГГ-ММ-ДД")
        MposField(from,{from=it},"Дата начала",Modifier.testTag("reportPeriodFrom"))
        MposField(through,{through=it},"Дата окончания",Modifier.testTag("reportPeriodThrough"))
        ui.filterError?.let {MposNotice(it,true)}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MposButton("Отмена",{periodDialog=false;model.clearFilterError()},style=ActionStyle.Secondary)
            MposButton("Применить",{if(model.dates(from,through)) periodDialog=false},Modifier.testTag("reportApply"))
        }
    }
}

@Composable private fun ReportPeriodButton(label: String,active: Boolean,enabled: Boolean,onClick: ()->Unit,modifier: Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=40.dp).semantics {selected=active},
        shape=RoundedCornerShape(11.dp),color=if(active) c.primaryContainer else c.surface,
        contentColor=if(active) c.primary else c.onSurface,
        border=androidx.compose.foundation.BorderStroke(1.dp,if(active) c.primary else c.outline)) {
        Box(Modifier.padding(horizontal=13.dp,vertical=9.dp),contentAlignment=Alignment.Center) {Text(label,fontSize=13.sp,fontWeight=if(active) FontWeight.Bold else FontWeight.Medium)}
    }
}
