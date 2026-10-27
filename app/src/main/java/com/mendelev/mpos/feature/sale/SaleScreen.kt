package com.mendelev.mpos.feature.sale

import androidx.activity.compose.BackHandler
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
@Composable fun SaleScreen(model: SaleViewModel,history: Boolean,onBack: ()->Unit,onHistory: ()->Unit,onShift: ()->Unit,refundModel: RefundViewModel) {
    val ui by model.state.collectAsStateWithLifecycle()
    val refund by refundModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(history) {model.refresh();refundModel.refresh()}
    LaunchedEffect(refund.result?.id) {if(refund.result!=null) model.refresh()}
    val locked=ui.pending || refund.pending || refund.unresolved!=null || ui.unresolved!=null || ui.splitUncertain || (ui.split?.paid?.isNotEmpty()==true && ui.completed==null)
    BackHandler {if(!ui.pending) {if(refund.review!=null || refund.result!=null) refundModel.dismiss() else if(ui.selected!=null && refund.unresolved==null && !refund.pending) model.closeDetails() else if(ui.partPreview!=null) model.dismissPart() else if(ui.preview!=null) model.dismissPreview() else if(!locked) onBack()}}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar(if(history) "M-POS · История чеков" else "M-POS · Оплата", navigationEnabled=!locked) {
            MposNavigationAction("Назад",{if(!locked) onBack()})
            if(!history) MposNavigationAction("История чеков",{if(!locked) onHistory()})
        }
        if(LocalMposTopNavigation.current != null) {
            Row(Modifier.padding(horizontal=20.dp, vertical=8.dp)) {
                MposButton("Назад", onBack, enabled=!locked, style=ActionStyle.Secondary)
            }
        }
        if(ui.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else if(ui.fatal) Column(Modifier.padding(20.dp)) {MposNotice(ui.message ?: "Не удалось открыть оплату",true);MposButton("Повторить открытие",model::refresh)}
        else if(history) {
            if(ui.message!=null) MposNotice(ui.message!!,true)
            if(refund.message!=null && ui.selected==null) MposNotice(refund.message!!,true)
            if(refund.blocked && refund.unresolved==null) MposButton("Прочитать возвраты",refundModel::refresh,busy=refund.pending,style=ActionStyle.Secondary)
            LazyColumn(Modifier.fillMaxSize().padding(20.dp).testTag("receiptHistory"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(ui.history.isEmpty()) item {Text("Проведённых чеков пока нет")}
                items(ui.history,key={it.id}) {r->Surface(shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Чек №${r.number} · ${money(r.total)}",style=MaterialTheme.typography.titleMedium)
                        refund.history.firstOrNull {it.receiptId==r.id}?.let {Text("Возвращён · ${date(it.createdAt)}",Modifier.testTag("returned:${r.id}"))}
                        Text(date(r.createdAt));Text("Наличные: ${money(r.cash)} · Карта: ${money(r.card)}")
                        MposButton("Открыть чек",{model.select(r.id)},Modifier.testTag("receipt:${r.commandId}"),enabled=!locked,style=ActionStyle.Secondary)
                    }
                }}
            }
        } else if(ui.completed!=null) {
            Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Продажа сохранена",Modifier.testTag("saleSuccess"),style=MaterialTheme.typography.headlineSmall)
                if(ui.message!=null) MposNotice(ui.message!!,true)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {ReceiptBody(ui.completed!!)}
                MposButton("Готово",{model.finish(onBack)},Modifier.fillMaxWidth().testTag("saleDone"),busy=ui.pending)
            }
        } else if(ui.split!=null) {
            SplitCheckout(ui,model)
        } else {
            val work=ui.work!!;val f=ui.form;val enabled=!locked
            if(ui.message!=null) MposNotice(ui.message!!,true)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=20.dp).testTag("checkoutFields"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
                item {
                    Text("Оплата заказа",style=MaterialTheme.typography.headlineSmall)
                    Text(listOf(work.cart.details.label,OrderRules.typeLabel(work.cart.details.type)).filter {it.isNotBlank()}.joinToString(" · "))
                    Text("Карта учитывается вручную. Банковский терминал не подключён.",style=MaterialTheme.typography.bodySmall)
                    if(work.cart.lines.isEmpty()) Text("Заказ пуст. Добавьте товары в рабочей зоне",Modifier.testTag("checkoutEmpty"))
                    if(ui.shift?.active==null) {MposNotice("Откройте смену перед оплатой",true);MposButton("Перейти к смене",onShift,enabled=enabled,style=ActionStyle.Secondary)}
                    if(work.cart.lines.any {CartEngine.stale(it,work.products)}) MposNotice("Каталог изменился. Вернитесь в рабочую зону и пересчитайте заказ",true)
                    if(ui.draft!=null) Text("Подготовленная оплата сохранена. Перед проведением проверьте итог заново.",Modifier.testTag("checkoutRestored"),style=MaterialTheme.typography.bodySmall)
                }
                items(work.cart.lines,key={it.id}) {line->
                    val discount=f.discounts[line.id] ?: DiscountField()
                    Surface(shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(line.name,style=MaterialTheme.typography.titleMedium)
                        Text("${line.unitPrice} BYN × ${line.quantity}")
                        if(line.options.isNotEmpty()) Text(line.options.joinToString {it.name})
                        MposSelect("Скидка строки",discount.type,listOf("none" to "Без скидки","percent" to "Процент","fixed" to "BYN за единицу"),{type->model.edit {it.copy(discounts=it.discounts+(line.id to discount.copy(type=type)))}},enabled)
                        if(discount.type!="none") MposField(discount.value,{value->model.edit {it.copy(discounts=it.discounts+(line.id to discount.copy(value=value)))}},if(discount.type=="percent") "Скидка, %" else "Скидка на единицу, BYN",Modifier.testTag("discount:${line.id}"),enabled,numeric=true)
                    }}
                }
                if(work.cart.details.type=="delivery") item {
                    Text("Доставка",style=MaterialTheme.typography.titleMedium)
                    MposField(f.tariff,{v->model.edit {it.copy(tariff=v)}},"Название тарифа",Modifier.testTag("deliveryName"),enabled)
                    MposField(f.fee,{v->model.edit {it.copy(fee=v)}},"Стоимость доставки, BYN (0 — бесплатно)",Modifier.testTag("deliveryFee"),enabled,numeric=true)
                    Text("Стоимость доставки сразу выдаётся из наличной кассы, в том числе при оплате картой.",style=MaterialTheme.typography.bodySmall)
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
    if(ui.preview!=null && !history) MposDialog("Подтверждение оплаты",model::dismissPreview,!ui.pending,wide=true) {
        val q=ui.quote!!;val p=ui.payment!!
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Итого: ${money(q.total)}",Modifier.testTag("salePreviewTotal"),style=MaterialTheme.typography.titleLarge)
            Text("Скидки: ${money(q.discount)}")
            Text("Наличные: ${money(p.cash)} · Карта: ${money(p.card)}")
            p.parts.filter {it.method=="cash"}.forEachIndexed {i,part->Text("Сдача ${i+1}: ${money(part.change!!)}",Modifier.testTag("saleChange:$i"))}
            if(q.delivery!=null) Text("Выдать за доставку: ${money(p.deliveryCashOut)}")
            Text("После продажи в кассе: ${money(p.expectedCashAfter)}")
            Text("Подтверждение сохранит чек и спишет товары. Карта — ручной учёт.",style=MaterialTheme.typography.bodySmall)
        }
        if(ui.message!=null) MposNotice(ui.message!!,true)
        MposButton("Подтвердить оплату",model::commit,Modifier.fillMaxWidth().testTag("saleConfirm"),busy=ui.pending)
    }
    ui.partPreview?.let {part->MposDialog("Подтверждение части",model::dismissPart,!ui.pending,wide=true) {
        Text("${if(part.payment.method=="cash") "Наличные" else "Карта"}: ${money(part.payment.amount)}",Modifier.testTag("splitPreviewAmount"),style=MaterialTheme.typography.titleLarge)
        if(part.payment.method=="cash") {
            val given=part.payment.given!!
            Text("Получено: ${money(given)}")
            Text("Сдача: ${money(given-part.payment.amount)}",Modifier.testTag("splitPreviewChange"))
        } else Text("Подтвердите только после оплаты на терминале. Приложение учитывает карту вручную.")
        Text("Подтверждённую часть нельзя изменить или отменить. Чек будет завершён после подтверждения всех частей.")
        MposButton("Подтвердить часть",model::confirmPart,Modifier.fillMaxWidth().testTag("splitConfirm"),busy=ui.pending)
    }}
    if(refund.review==null && refund.unresolved==null && refund.result==null) ui.selected?.let {selected->
        MposDialog("Чек №${selected.first.number}",{if(!refund.pending) {refundModel.dismiss();model.closeDetails()}},!refund.pending,wide=true) {
            Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()).testTag("receiptDetails")) {
                ReceiptBody(selected)
                refund.history.firstOrNull {it.receiptId==selected.first.id}?.let {RefundResultBody(it)}
            }
            if(refund.message!=null) MposNotice(refund.message!!,true)
            if(refund.history.none {it.receiptId==selected.first.id}) {
                if(ui.shift?.active==null) Text("Для возврата откройте смену")
                MposButton("Вернуть чек",{refundModel.prepare(selected.first.id,ui.shift!!.ownerId)},Modifier.fillMaxWidth().testTag("refundPrepare"),
                    enabled=!ui.pending && !refund.blocked && ui.shift?.active!=null && ui.split?.paid.isNullOrEmpty(),style=ActionStyle.Danger,busy=refund.pending)
            }
        }
    }
    refund.review?.let {submission->MposDialog("Полный возврат чека",refundModel::dismiss,!refund.pending,wide=true) {
        val p=submission.preview.amounts
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("К возврату: ${money(p.total)}",Modifier.testTag("refundPreviewTotal"),style=MaterialTheme.typography.titleLarge)
            Text("Выдать покупателю наличными: ${money(p.cash)}",Modifier.testTag("refundPreviewCash"))
            Text("Вернуть на карту вручную через терминал: ${money(p.card)}",Modifier.testTag("refundPreviewCard"))
            if(p.deliveryCashCredit>0) MposNotice("Отменяется оплата курьеру: ${money(p.deliveryCashCredit)}. Верните эти наличные в кассу.")
            Text("После возврата в кассе: ${money(p.expectedCashAfter)}",Modifier.testTag("refundPreviewDrawer"))
            Text("Будет возвращён весь чек. Товары восстановятся по сохранённому расходу продажи. Приложение не выполняет банковский возврат автоматически.")
        }
        MposButton("Подтвердить возврат",refundModel::confirm,Modifier.fillMaxWidth().testTag("refundConfirm"),style=ActionStyle.Danger,busy=refund.pending)
    }}
    if(refund.unresolved!=null) MposDialog("Проверка результата возврата",{},closable=false,wide=true) {
        Text(refund.message ?: "Сохранение возврата…")
        MposButton("Проверить результат возврата",refundModel::confirm,Modifier.fillMaxWidth().testTag("refundResolve"),busy=refund.pending)
    }
    refund.result?.let {result->MposDialog("Возврат сохранён",refundModel::dismiss,!refund.pending,wide=true) {
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()).testTag("refundSuccess")) {RefundResultBody(result)}
        if(refund.message!=null) MposNotice(refund.message!!,true)
        MposButton("Готово",refundModel::dismiss,Modifier.fillMaxWidth().testTag("refundDone"),busy=refund.pending)
    }}
}
@Composable private fun SplitCheckout(ui: SaleUi,model: SaleViewModel) {
    val progress=ui.split!!;val state=progress.editable();val enabled=!ui.pending && !ui.splitUncertain && ui.unresolved==null
    var editor by remember {mutableStateOf<Pair<String,Long>?>(null)}
    var input by remember {mutableStateOf("")}
    fun open(kind: String,id: Long,value: String) {input=value;editor=kind to id}
    Column(Modifier.fillMaxSize()) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=20.dp).testTag("splitParts"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)) {
            item {
                Text("Оплата по частям",style=MaterialTheme.typography.headlineSmall)
                Text("Итого: ${money(state.total)}",Modifier.testTag("splitTotal"))
                Text("Подтверждено: ${money(progress.paidAmount)} · Осталось: ${money(state.total-progress.paidAmount)}",Modifier.testTag("splitRemaining"))
                Text("Наличные по незавершённой оплате: ${money(progress.pendingCash)}",Modifier.testTag("splitPendingCash"))
                Text("В кассе по завершённым чекам: ${money(ui.shift!!.currentCash)}")
                Text(if(progress.complete) "Все части подтверждены. Сохраните чек, чтобы завершить продажу и списать товары." else "Подтверждённые части сохранены и недоступны для изменений. Склад и итог кассы обновятся при завершении чека.")
                if(!progress.complete) MposButton("Количество частей: ${state.parts.size}",{open("count",0,state.parts.size.toString())},Modifier.testTag("splitResize"),enabled,style=ActionStyle.Secondary)
            }
            items(state.parts,key={it.id}) {part->
                val p=part.payment;val editable=enabled && !part.paid && !progress.complete
                Surface(shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Часть ${state.parts.indexOf(part)+1} · ${if(part.paid) "Оплачено" else "Не оплачено"}",Modifier.testTag("splitStatus:${part.id}"),style=MaterialTheme.typography.titleMedium)
                    Text(money(p.amount),Modifier.testTag("splitAmount:${part.id}"),style=MaterialTheme.typography.titleLarge)
                    MposSelect("Способ части",p.method,listOf("cash" to "Наличные","card" to "Карта"),{model.changeSplit(SplitEdit.Method(part.id,it))},editable,Modifier.testTag("splitMethod:${part.id}"))
                    MposButton("Изменить сумму",{open("amount",part.id,java.math.BigDecimal.valueOf(p.amount,2).toPlainString())},Modifier.testTag("splitEditAmount:${part.id}"),editable,style=ActionStyle.Secondary)
                    if(p.method=="cash") {
                        Text("Получено: ${p.given?.let(::money) ?: "не введено"}",Modifier.testTag("splitGiven:${part.id}"))
                        if(p.given!=null && p.given>=p.amount) Text("Сдача: ${money(p.given-p.amount)}")
                        MposButton("Ввести наличные",{open("given",part.id,p.given?.let {java.math.BigDecimal.valueOf(it,2).toPlainString()} ?: "")},Modifier.testTag("splitEditGiven:${part.id}"),editable,style=ActionStyle.Secondary)
                    }
                    MposButton(if(part.paid) "Часть подтверждена" else "Оплатить часть",{model.reviewPart(part.id)},Modifier.fillMaxWidth().testTag("splitPay:${part.id}"),editable)
                }}
            }
            if(progress.paid.isEmpty()) item {MposButton("Вернуться к подготовке заказа",model::leaveSplit,Modifier.testTag("splitCancel"),enabled,style=ActionStyle.Secondary)}
        }
        if(ui.splitUncertain) MposButton("Прочитать сохранённую оплату",model::refresh,Modifier.fillMaxWidth().padding(16.dp).testTag("splitReload"),busy=ui.pending)
        else if(ui.unresolved!=null) MposButton("Проверить результат оплаты",model::commit,Modifier.fillMaxWidth().padding(16.dp).testTag("saleResolve"),busy=ui.pending)
        else if(progress.complete) MposButton("Завершить продажу",model::completeSplit,Modifier.fillMaxWidth().padding(16.dp).testTag("splitFinish"),busy=ui.pending)
    }
    editor?.let {(kind,id)->MposDialog(when(kind) {"count"->"Количество частей";"amount"->"Сумма части";else->"Получено наличными"},{if(!ui.pending) editor=null},!ui.pending) {
        MposField(input,{input=it},if(kind=="count") "Части (2–10)" else "Сумма, BYN",Modifier.testTag("splitEditValue"),!ui.pending,numeric=true)
        Text(if(kind=="count") "Подтверждённые части сохранятся. Неоплаченный остаток распределится поровну." else if(kind=="amount") "Остаток перераспределится между неоплаченными частями." else "Введите принятую сумму. Пустое поле очищает неподтверждённый ввод.")
        MposButton("Сохранить",{model.splitValue(kind,id,input);editor=null},Modifier.fillMaxWidth().testTag("splitEditSave"),enabled,busy=ui.pending)
    }}
}
@Composable private fun ReceiptBody(value: Pair<ReceiptEntity,ReceiptSnapshot>) {
    val (r,s)=value
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Чек №${r.number}",style=MaterialTheme.typography.titleLarge)
        Text(date(r.createdAt));Text("Смена: ${r.shiftId}",style=MaterialTheme.typography.bodySmall)
        Text(OrderRules.typeLabel(s.cart.details.type))
        if(s.cart.details.label.isNotBlank()) Text(s.cart.details.label)
        if(s.cart.details.comment.isNotBlank()) Text(s.cart.details.comment)
        if(s.cart.details.type=="delivery") {
            if(s.cart.details.address.isNotBlank()) Text(s.cart.details.address)
            if(s.cart.details.phone.isNotBlank()) Text(s.cart.details.phone)
        }
        s.cart.lines.forEach {line->
            Text(line.name,Modifier.testTag("receiptLine:${line.id}"),style=MaterialTheme.typography.titleMedium)
            Text("${line.unitPrice} BYN × ${line.quantity}")
            if(line.options.isNotEmpty()) Text(line.options.joinToString {it.name})
            val net=s.quote.lines.first {it.id==line.id};Text("Сумма строки: ${money(net.net)} · Скидка: ${money(net.discount)}")
        }
        Text("Итого: ${money(s.quote.total)}",Modifier.testTag("receiptTotal"),style=MaterialTheme.typography.titleLarge)
        s.quote.delivery?.let {Text("Доставка: ${it.name} · ${money(it.fee)}")}
        s.payment.parts.forEach {p->
            Text("${if(p.method=="cash") "Наличные" else "Карта"}: ${money(p.amount)}")
            if(p.given!=null) Text("Получено: ${money(p.given)} · Сдача: ${money(p.change!!)}")
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
