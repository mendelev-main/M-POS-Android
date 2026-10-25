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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun money(value: Long)=ShiftRules.money(value)
private fun date(value: Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
@Composable fun SaleScreen(model: SaleViewModel,history: Boolean,onBack: ()->Unit,onHistory: ()->Unit,onShift: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(history) {model.refresh()}
    val locked=ui.pending || ui.unresolved!=null
    BackHandler {if(!ui.pending && ui.unresolved==null) {if(ui.preview!=null) model.dismissPreview() else onBack()}}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar(if(history) "M-POS · История чеков" else "M-POS · Оплата") {
            MposNavigationAction("Назад",{if(!locked) onBack()})
            if(!history) MposNavigationAction("История чеков",{if(!locked) onHistory()})
        }
        if(ui.loading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else if(ui.fatal) Column(Modifier.padding(20.dp)) {MposNotice(ui.message ?: "Не удалось открыть оплату",true);MposButton("Повторить открытие",model::refresh)}
        else if(history) {
            if(ui.message!=null) MposNotice(ui.message!!,true)
            LazyColumn(Modifier.fillMaxSize().padding(20.dp).testTag("receiptHistory"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(ui.history.isEmpty()) item {Text("Проведённых чеков пока нет")}
                items(ui.history,key={it.id}) {r->Surface(shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Чек №${r.number} · ${money(r.total)}",style=MaterialTheme.typography.titleMedium)
                        Text(date(r.createdAt));Text("Наличные: ${money(r.cash)} · Карта: ${money(r.card)}")
                        MposButton("Открыть чек",{model.select(r.id)},Modifier.testTag("receipt:${r.commandId}"),style=ActionStyle.Secondary)
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
                    MposSelect("Оплата",f.mode,listOf("cash" to "Наличные","card" to "Карта","split" to "Разделить / смешанная"),{v->model.edit {it.copy(mode=v)}},enabled)
                    if(f.mode=="cash") MposField(f.given,{v->model.edit {it.copy(given=v)}},"Получено наличными, BYN",Modifier.testTag("cashGiven"),enabled,numeric=true)
                    if(f.mode=="split") {
                        Text("Части — подготовленные суммы. Все части подтверждаются вместе.",style=MaterialTheme.typography.bodySmall)
                        var count by remember {mutableStateOf("2")}
                        MposField(count,{count=it},"Количество равных частей (2–10)",Modifier.testTag("splitCount"),enabled,numeric=true)
                        MposButton("Разделить поровну",{model.equal(count.toIntOrNull() ?: 0)},enabled=enabled,style=ActionStyle.Secondary)
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
    ui.selected?.let {selected->MposDialog("Чек №${selected.first.number}",model::closeDetails,wide=true) {
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()).testTag("receiptDetails")) {ReceiptBody(selected)}
    }}
}
@Composable private fun ReceiptBody(value: Pair<ReceiptEntity,ReceiptSnapshot>) {
    val (r,s)=value
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Чек №${r.number}",style=MaterialTheme.typography.titleLarge)
        Text(date(r.createdAt));Text("Смена: ${r.shiftId}",style=MaterialTheme.typography.bodySmall)
        Text(OrderRules.typeLabel(s.cart.details.type))
        if(s.cart.details.label.isNotBlank()) Text(s.cart.details.label)
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
