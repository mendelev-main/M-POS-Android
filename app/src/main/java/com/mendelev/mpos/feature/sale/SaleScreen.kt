package com.mendelev.mpos.feature.sale

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.OrderRules
import com.mendelev.mpos.domain.workspace.CartEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun money(value: Long)=ShiftRules.money(value)
private fun date(value: Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
private fun saleNavigationLocked(ui: SaleUi,refund: RefundUi)=ui.pending || refund.pending || refund.unresolved!=null || ui.unresolved!=null || ui.splitUncertain || (ui.split?.paid?.isNotEmpty()==true && ui.completed==null)

/** The history shell survives loading and selection; only its body can show a spinner. */
@Composable fun SaleScreen(model: SaleViewModel,history: Boolean,onBack: ()->Unit,onHistory: ()->Unit,onShift: ()->Unit,refundModel: RefundViewModel) {
    if(history) {
        val ui by model.state.collectAsStateWithLifecycle()
        val refund by refundModel.state.collectAsStateWithLifecycle()
        ReceiptHistoryShell(onBack,!saleNavigationLocked(ui,refund)) {SaleScreenContent(model,true,onBack,onHistory,onShift,refundModel)}
    } else SaleScreenContent(model,false,onBack,onHistory,onShift,refundModel)
}
@Composable internal fun ReceiptHistoryShell(onBack: ()->Unit,navigationEnabled: Boolean=true,content: @Composable ()->Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("M-POS · История чеков",navigationEnabled=navigationEnabled) {MposNavigationAction("Назад",{if(navigationEnabled)onBack()})}
        Box(Modifier.weight(1f)) {content()}
    }
}
@Composable private fun SaleScreenContent(model: SaleViewModel,history: Boolean,onBack: ()->Unit,onHistory: ()->Unit,onShift: ()->Unit,refundModel: RefundViewModel) {
    val presets by com.mendelev.mpos.feature.settings.settingsCatalogModel().state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle()
    val printerContext=androidx.compose.ui.platform.LocalContext.current
    val printer=remember {com.mendelev.mpos.print.PrintRuntime.get(printerContext)}
    var entryReady by remember(model,history) {mutableStateOf(false)}
    LaunchedEffect(model,history) {
        if(history) model.openHistory() else model.open()
        refundModel.refresh()
        model.state.first {!it.loading && !it.pending}
        entryReady=true
    }
    if(!history && (!entryReady || ui.loading)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        return
    }
    LaunchedEffect(ui.completed?.first?.id) {ui.completed?.let {printer.autoSale(it)}}
    val refund by refundModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(refund.result?.id) {refund.result?.let {if(it.cash>0) printer.drawer("refund:${it.id}",automatic=true);model.refresh()}}
    val locked=saleNavigationLocked(ui,refund)
    BackHandler {if(!ui.pending) {if(refund.review!=null || refund.result!=null) refundModel.dismiss() else if(!history && ui.selected!=null && refund.unresolved==null && !refund.pending) model.closeDetails() else if(ui.partPreview!=null) model.dismissPart() else if(!history && ui.split!=null && !ui.splitUncertain && ui.unresolved==null) model.leaveSplit() else if(ui.preview!=null) model.dismissPreview() else if(!history && ui.completed!=null) model.finish(onBack) else if(!locked) onBack()}}
    val webPayment=!history && !ui.fatal
    var options by androidx.compose.runtime.saveable.rememberSaveable(ui.work?.cart?.sessionId) {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if(!webPayment && !history) {
            MposAppBar(if(history) "M-POS · История чеков" else "M-POS · Оплата", navigationEnabled=!locked) {
                MposNavigationAction("Назад",{if(!locked) onBack()})
                if(!history) MposNavigationAction("История чеков",{if(!locked) onHistory()})
            }
            if(LocalMposTopNavigation.current != null && !history) {
                Row(Modifier.padding(horizontal=20.dp, vertical=8.dp)) {
                    MposButton("Назад", onBack, enabled=!locked, style=ActionStyle.Secondary)
                }
            }
        }
        if(ui.loading && !history) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else if(ui.fatal) Column(Modifier.padding(20.dp)) {MposNotice(ui.message ?: "Не удалось открыть оплату",true);MposButton("Повторить открытие",model::refresh)}
        else if(history) {
            if(ui.message!=null) MposNotice(ui.message!!,true)
            if(refund.message!=null) MposNotice(refund.message!!,true)
            if(refund.blocked && refund.unresolved==null) MposButton("Прочитать возвраты",refundModel::refresh,busy=refund.pending,style=ActionStyle.Secondary)
            var picked by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(ui.selected?.first?.id.orEmpty())}
            var printNotice by remember {mutableStateOf(false)}
            val chosen=picked.takeIf {id->ui.history.any {it.id==id}} ?: ui.history.firstOrNull()?.id.orEmpty()
            LaunchedEffect(chosen,ui.selected?.first?.id,ui.history,ui.pending,ui.loading) {
                if(!ui.pending && !ui.loading && chosen.isNotEmpty() && ui.selected?.first?.id!=chosen) model.select(chosen)
            }
            ReceiptHistoryPage(ui.history,ui.receiptTypes,ui.receiptMethods,chosen,ui.selected?.takeIf {it.first.id==chosen},(ui.historyRefunds+refund.history).distinctBy {it.id},!locked,
                ui.shift?.active!=null && !refund.blocked && ui.split?.paid.isNullOrEmpty(),
                {id->picked=id;model.select(id)},
                {ui.selected?.takeIf {it.first.id==chosen}?.let {refundModel.prepare(it.first.id,ui.shift!!.active!!.actorId)}},
                {printNotice=true},ui.message,web=ui.webHistory,more=ui.historyMore,loadingMore=ui.historyLoading,onMore=model::moreHistory,onChangePayment=model::editReceiptPayment,initialLoading=ui.loading || !entryReady)
            if(printNotice) ui.selected?.let {com.mendelev.mpos.print.ReceiptPrintMenu(it) {printNotice=false}}
        } else if(ui.split!=null) {
            SplitPaymentPage(ui,model)
        } else {
            val work=ui.work!!;val enabled=!locked
            PaymentPage(work.cart,model.estimate(),ui.form.given,enabled,work.cart.lines.isNotEmpty() && ui.shift?.active!=null,
                ui.message,ui.draft!=null,{value->model.edit {it.copy(given=value)}},onBack,
                {model.equal(2);model.startSplit()},
                {method->model.edit {it.copy(mode=method,given=it.given.ifBlank {"0"}.trimEnd(',','.'))};model.prepare(autoCommit=method=="cash")},
                {options=true},notice={
                    if(ui.loyaltyBlocked) MposButton(if(work.cart.loyalty?.selected.isNullOrEmpty()) "Продолжить без лояльности" else "Продолжить без подарка",model::continueWithoutGift,enabled=enabled,style=ActionStyle.Secondary,modifier=Modifier.testTag("payWithoutLoyaltyGift"))
                    if(work.cart.lines.isEmpty()) Text("Заказ пуст. Добавьте товары в рабочей зоне",Modifier.testTag("checkoutEmpty"))
                    if(ui.shift?.active==null) {MposNotice("Откройте смену перед оплатой",true);MposButton("Перейти к смене",onShift,enabled=enabled,style=ActionStyle.Secondary)}
                },resolve=if(ui.unresolved!=null) {{MposButton("Проверить результат оплаты",model::commit,Modifier.fillMaxWidth().testTag("saleResolve"),busy=ui.pending)}} else null)
            if(options) MposDialog("Параметры оплаты",{if(!ui.pending) options=false},!ui.pending,wide=true) {
                val work=ui.work!!;val f=ui.form;val enabled=!locked
                if(ui.message!=null) MposNotice(ui.message!!,true)
                LazyColumn(Modifier.heightIn(max=420.dp).fillMaxWidth().testTag("checkoutOptionsFields"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                    item {
                        Text("Оплата заказа",style=MaterialTheme.typography.headlineSmall)
                        Text(listOf(work.cart.details.label,OrderRules.typeLabel(work.cart.details.type)).filter {it.isNotBlank()}.joinToString(" · "))
                        Text("Карта учитывается вручную. Банковский терминал не подключён.",style=MaterialTheme.typography.bodySmall)
                        if(work.cart.lines.isEmpty()) Text("Заказ пуст. Добавьте товары в рабочей зоне",Modifier.testTag("checkoutEmpty"))
                        if(ui.shift?.active==null) {MposNotice("Откройте смену перед оплатой",true);MposButton("Перейти к смене",onShift,enabled=enabled,style=ActionStyle.Secondary)}
                    }
                    items(work.cart.lines,key={it.id}) {line->
                        val discount=f.discounts[line.id] ?: DiscountField()
                        Surface(shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(line.name,style=MaterialTheme.typography.titleMedium)
                            Text("${line.unitPrice} BYN × ${line.quantity}")
                            if(line.options.isNotEmpty()) Text(line.options.joinToString {it.name})
                            val discounts=presets.catalog.rows(com.mendelev.mpos.domain.settings.SettingKind.DISCOUNT)
                            if(discounts.isNotEmpty()) MposSelect("Шаблон скидки",discounts.find {it.name==discount.name && it.type==discount.type && it.value==discount.value}?.id.orEmpty(),listOf("" to "Своя скидка / без скидки")+discounts.map {it.id to it.name},{id->
                                val preset=discounts.find {it.id==id}
                                model.edit {it.copy(discounts=it.discounts+(line.id to if(preset==null) DiscountField() else DiscountField(preset.type,preset.value,preset.name)))}
                            },enabled,Modifier.testTag("discountPreset:${line.id}"))
                            MposSelect("Скидка строки",discount.type,listOf("none" to "Без скидки","percent" to "Процент","fixed" to "BYN за единицу"),{type->model.edit {it.copy(discounts=it.discounts+(line.id to discount.copy(type=type)))}},enabled)
                            if(discount.type!="none") MposField(discount.value,{value->model.edit {it.copy(discounts=it.discounts+(line.id to discount.copy(value=value)))}},if(discount.type=="percent") "Скидка, %" else "Скидка на единицу, BYN",Modifier.testTag("discount:${line.id}"),enabled,numeric=true)
                        }}
                    }
                    if(work.cart.details.type=="delivery") item {
                        Text("Доставка",style=MaterialTheme.typography.titleMedium)
                        val tariffs=presets.catalog.rows(com.mendelev.mpos.domain.settings.SettingKind.DELIVERY)
                        if(tariffs.isNotEmpty()) MposSelect("Тариф доставки",tariffs.find {it.name==f.tariff && it.value==f.fee}?.id.orEmpty(),listOf("" to "Своя стоимость")+tariffs.map {it.id to "${it.name} — ${it.value} BYN"},{id->
                            tariffs.find {it.id==id}?.let {rate->model.edit {it.copy(tariff=rate.name,fee=rate.value)}}
                        },enabled,Modifier.testTag("deliveryPreset"))
                        MposField(f.tariff,{v->model.edit {it.copy(tariff=v)}},"Название тарифа",Modifier.testTag("deliveryName"),enabled)
                        MposField(f.fee,{v->model.edit {it.copy(fee=v)}},"Стоимость доставки, BYN (0 — бесплатно)",Modifier.testTag("deliveryFee"),enabled,numeric=true)
                    }
                    item {
                        Text("Способ оплаты",style=MaterialTheme.typography.titleMedium)
                        MposSelect("Оплата",f.mode,listOf("cash" to "Наличные","card" to "Карта","split" to "Разделить / смешанная"),{v->model.edit {it.copy(mode=v)}},enabled,Modifier.testTag("saleMode"))
                        if(f.mode=="cash") MposField(f.given,{v->model.edit {it.copy(given=v)}},"Получено наличными, BYN",Modifier.testTag("cashGiven"),enabled,numeric=true)
                        if(f.mode=="split") {
                            Text("Сначала сохраните части. Затем подтвердите каждую отдельно; наличные вводятся при оплате части.",style=MaterialTheme.typography.bodySmall)
                            var count by remember {mutableStateOf("2")}
                            MposField(count,{count=it},"Количество равных частей (2–10)",Modifier.testTag("splitCount"),enabled,numeric=true)
                            MposButton("Разделить поровну",{model.equal(count.toIntOrNull() ?: 0)},Modifier.testTag("splitEqual"),enabled=enabled,style=ActionStyle.Secondary)
                        }
                    }
                    if(f.mode=="split") items(f.parts.indices.toList(),key={"part$it"}) {i->
                        val p=f.parts[i]
                        fun change(next: PartField)=model.edit {it.copy(parts=it.parts.mapIndexed {n,old->if(n==i) next else old})}
                        Surface(shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Часть ${i+1}",style=MaterialTheme.typography.titleMedium)
                            MposSelect("Способ части",p.method,listOf("cash" to "Наличные","card" to "Карта"),{change(p.copy(method=it))},enabled)
                            MposField(p.amount,{change(p.copy(amount=it))},"Сумма части, BYN",Modifier.testTag("partAmount:$i"),enabled,numeric=true)
                            if(p.method=="cash") MposField(p.given,{change(p.copy(given=it))},"Получено, BYN",Modifier.testTag("partGiven:$i"),enabled,numeric=true)
                        }}
                    }
                    item {MposButton("Обновить заказ и смену",model::refresh,enabled=enabled,style=ActionStyle.Secondary)}
                }
                model.estimate()?.let {Text("Итого: ${money(it.total)}",Modifier.padding(horizontal=20.dp),style=MaterialTheme.typography.titleMedium)}
                if(ui.unresolved!=null) MposButton("Проверить результат оплаты",model::commit,Modifier.fillMaxWidth().padding(16.dp).testTag("saleResolve"),busy=ui.pending)
                else if(f.mode=="split") MposButton("Начать оплату по частям",model::startSplit,Modifier.fillMaxWidth().padding(16.dp).testTag("splitStart"),enabled=work.cart.lines.isNotEmpty() && ui.shift?.active!=null,busy=ui.pending)
                else MposButton("Проверить оплату",model::prepare,Modifier.fillMaxWidth().padding(16.dp).testTag("salePrepare"),enabled=work.cart.lines.isNotEmpty() && ui.shift?.active!=null,busy=ui.pending)
            }

        }
    }
    var printing by remember {mutableStateOf(false)}
    val paid=ui.completed
    if(!history && (paid!=null || ui.preview!=null && ui.form.mode=="card")) MposDialog(if(paid!=null) "Чек оплачен" else "Оплата картой",{if(paid!=null) model.finish(onBack) else model.dismissPreview()},!ui.pending,maxWidth=560.dp) {
        if(paid!=null) {
            Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()).testTag("saleSuccess")) {ReceiptBody(paid)}
            ui.message?.let {MposNotice(it,true)}
            Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                MposPosControl("Печать",{printing=true},Modifier.weight(1f).testTag("receiptPrint"),!ui.pending,PosControl.Secondary,height=48.dp,radius=16.dp)
                MposPosControl("Готово",{model.finish(onBack)},Modifier.weight(1f).testTag("saleDone"),!ui.pending,PosControl.Cash,height=48.dp,radius=16.dp)
            }
        } else {
            Text("Проведите оплату на терминале",style=MaterialTheme.typography.bodyLarge)
            Text(money(ui.quote!!.total),Modifier.fillMaxWidth().padding(vertical=24.dp).testTag("salePreviewTotal"),style=MaterialTheme.typography.displaySmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            ui.message?.let {MposNotice(it,true)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposPosControl("Отмена",model::dismissPreview,Modifier.weight(1f),!ui.pending,PosControl.Secondary,height=52.dp,radius=16.dp)
                MposPosControl("Оплата прошла",model::commit,Modifier.weight(1f).testTag("saleConfirm"),!ui.pending,PosControl.Card,height=52.dp,radius=16.dp)
            }
        }
    }
    if(printing && paid!=null) com.mendelev.mpos.print.ReceiptPrintWindow(paid) {printing=false}
    if(ui.preview!=null && !history && ui.form.mode!="card" && !ui.autoCommitting) MposDialog("Подтверждение оплаты",model::dismissPreview,!ui.pending,wide=true) {
        val q=ui.quote!!;val p=ui.payment!!
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Итого: ${money(q.total)}",Modifier.testTag("salePreviewTotal"),style=MaterialTheme.typography.titleLarge)
            Text("Скидки: ${money(q.discount)}")
            Text("Наличные: ${money(p.cash)} · Карта: ${money(p.card)}")
            p.parts.filter {it.method=="cash"}.forEachIndexed {i,part->Text("Сдача ${i+1}: ${money(part.change!!)}",Modifier.testTag("saleChange:$i"))}
            if(p.deliveryCashOut>0) Text("Выдать за доставку: ${money(p.deliveryCashOut)}")
            Text("После продажи в кассе: ${money(p.expectedCashAfter)}")
            Text("Подтверждение сохранит чек и спишет товары. Карта — ручной учёт.",style=MaterialTheme.typography.bodySmall)
        }
        if(ui.message!=null) MposNotice(ui.message!!,true)
        MposButton("Подтвердить оплату",model::commit,Modifier.fillMaxWidth().testTag("saleConfirm"),busy=ui.pending)
    }
    ui.paymentEditing?.let {selected->ReceiptPaymentEditWindow(selected,ui.pending,ui.paymentEditError,model::dismissPaymentEdit,model::confirmReceiptPayment)}
    ui.partPreview?.let {part->MposDialog("Оплата картой",model::dismissPart,!ui.pending) {
        Text("Оплатите картой на терминале",Modifier.align(Alignment.CenterHorizontally))
        Text(money(part.payment.amount),Modifier.align(Alignment.CenterHorizontally),style=MaterialTheme.typography.headlineLarge)
        ui.message?.let {MposNotice(it,true)}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposButton("Отмена",model::dismissPart,Modifier.weight(1f),!ui.pending,ActionStyle.Secondary)
            MposButton("Оплата прошла",model::confirmPart,Modifier.weight(1f).testTag("splitConfirm"),busy=ui.pending)
        }
    }}
    if(!history && refund.review==null && refund.unresolved==null && refund.result==null) ui.selected?.let {selected->
        MposDialog("Чек №${selected.first.number}",{if(!refund.pending) {refundModel.dismiss();model.closeDetails()}},!refund.pending,wide=true) {
            Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()).testTag("receiptDetails")) {
                ReceiptBody(selected)
                refund.history.firstOrNull {it.receiptId==selected.first.id}?.let {RefundResultBody(it)}
            }
            if(refund.message!=null) MposNotice(refund.message!!,true)
            if(refund.history.none {it.receiptId==selected.first.id}) {
                if(ui.shift?.active==null) Text("Для возврата откройте смену")
                MposButton("Вернуть чек",{refundModel.prepare(selected.first.id,ui.shift!!.active!!.actorId)},Modifier.fillMaxWidth().testTag("refundPrepare"),
                    enabled=!ui.pending && !refund.blocked && ui.shift?.active!=null && ui.split?.paid.isNullOrEmpty(),style=ActionStyle.Danger,busy=refund.pending)
            }
        }
    }
    refund.review?.let {submission->MposDialog("Возврат товара",refundModel::dismiss,!refund.pending,maxWidth=560.dp) {
        val p=submission.preview.amounts
        Text(ui.selected?.let {"Чек №"+it.first.number+" · "+receiptDate(it.first.createdAt)} ?: "Чек",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(),shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("К возврату",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(p.total),Modifier.testTag("refundPreviewTotal"),style=MaterialTheme.typography.headlineLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                ReceiptMoneyRow("Наличными",p.cash,amountTag="refundPreviewCash")
                if(p.card>0) ReceiptMoneyRow("На карту",p.card,amountTag="refundPreviewCard")
                if(p.deliveryCashCredit>0) ReceiptMoneyRow("Возврат оплаты курьеру в кассу",p.deliveryCashCredit,amountTag="refundPreviewDeliveryCredit")
            }
        }
        Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Полный возврат. Товары вернутся на остатки.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(p.deliveryCashCredit>0) Text("Отменяется оплата курьеру: "+money(p.deliveryCashCredit)+". Верните эти наличные в кассу.",style=MaterialTheme.typography.bodySmall)
            if(p.card>0) Text("Возврат на карту выполните через банковский терминал.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        refund.message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposPosControl("Отмена",refundModel::dismiss,Modifier.weight(1f),!refund.pending,PosControl.Secondary,48.dp,16.dp)
            MposPosControl("Вернуть "+money(p.total),refundModel::confirm,Modifier.weight(1f).testTag("refundConfirm"),!refund.pending,PosControl.Delete,48.dp,16.dp)
        }
    }}
    refund.result?.let {result->MposDialog("Возврат выполнен",refundModel::dismiss,!refund.pending,maxWidth=560.dp) {
        Text("Товары возвращены на остатки.",style=MaterialTheme.typography.bodyMedium)
        Text(money(result.total),Modifier.testTag("refundSavedTotal"),style=MaterialTheme.typography.headlineLarge)
        ReceiptMoneyRow("Наличными",result.cash)
        if(result.card>0) {ReceiptMoneyRow("Через терминал",result.card);Text("Карточная часть возвращается через банковский терминал.",style=MaterialTheme.typography.bodySmall)}
        if(result.deliveryCashCredit>0) ReceiptMoneyRow("Расход на доставку возвращён в кассу",result.deliveryCashCredit)
        refund.message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        MposPosControl("Готово",refundModel::dismiss,Modifier.fillMaxWidth().testTag("refundDone"),!refund.pending,PosControl.Cash,48.dp,16.dp)
    }}

}
@Composable private fun ReceiptBody(value: Pair<ReceiptEntity,ReceiptSnapshot>) {
    val (r,s)=value
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(s.cart.details.label.isNotBlank()) Text(s.cart.details.label,style=MaterialTheme.typography.titleLarge)
        Text("${receiptDate(r.createdAt)} · ${receiptPaymentLabel(s)} · ${OrderRules.typeLabel(s.cart.details.type)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val customer=listOf(s.cart.details.customerName,s.cart.details.phone,s.cart.details.address).filter {it.isNotBlank()}
        if(customer.isNotEmpty()) Text(customer.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
        s.cart.lines.forEach {line->
            val net=s.quote.lines.first {it.id==line.id}
            ReceiptMoneyRow("${line.name} × ${line.quantity.replace('.',',')}",net.net,"receiptLine:${line.id}")
            if(net.discount>0) ReceiptMoneyRow("↳ Скидка",-net.discount,accent=true)
            if(line.options.isNotEmpty()) Text("↳ "+line.options.joinToString(" · ") {it.name},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(line.comment.isNotBlank()) Text("Комментарий: ${line.comment}",style=MaterialTheme.typography.bodySmall)
        }
        if(s.quote.discount>0) ReceiptMoneyRow("Скидки на товары",-s.quote.discount,accent=true)
        s.quote.delivery?.let {ReceiptMoneyRow("Доставка",it.fee)}
        Text("Платежи",Modifier.padding(top=10.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        s.payment.parts.forEachIndexed {i,p->
            ReceiptMoneyRow("Платёж ${i+1} · ${if(p.method=="cash") "Наличные" else "Карта"}",p.amount)
            if(p.given!=null) {
                ReceiptMoneyRow("Наличные · внесено",p.given!!,small=true)
                ReceiptMoneyRow("Сдача",p.change!!,small=true)
            }
        }
        HorizontalDivider(Modifier.padding(top=8.dp))
        Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Итого",style=MaterialTheme.typography.titleMedium)
            Text(money(r.total),Modifier.testTag("receiptTotal"),style=MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable private fun RefundResultBody(refund: RefundEntity) {
    Text("Полный возврат · ${date(refund.createdAt)}",style=MaterialTheme.typography.titleMedium)
    Text("Возвращено: ${money(refund.total)}",Modifier.testTag("refundSavedTotal"))
    Text("Наличные покупателю: ${money(refund.cash)}")
    Text("Карта — вручную через терминал: ${money(refund.card)}")
    if(refund.deliveryCashCredit>0) Text("Отменён расход курьеру: ${money(refund.deliveryCashCredit)}. Наличные должны быть возвращены в кассу.")
    Text("Товары восстановлены по историческому расходу. Повторный возврат этого чека недоступен.")
}
