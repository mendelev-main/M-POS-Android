package com.mendelev.mpos.feature.purchasing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.warehouse.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PurchaseOrderEditDialog(ui: PurchaseUi,model: PurchaseViewModel) {
    val edit=ui.editing ?: return;val s=ui.snapshot ?: return
    val locked=ui.pending || ui.orderUnresolved!=null
    val enabled=!locked && !ui.blocked
    var confirmClose by rememberSaveable(edit.baseline.id) {mutableStateOf(false)}
    fun close() {if(!locked) {if(edit.dirty) confirmClose=true else model.discardEdit()}}
    if(confirmClose) {
        MposDialog("Закрыть без сохранения?",{confirmClose=false}) {
            Text("Несохранённые изменения заказа будут потеряны. Сохранённые версии останутся в истории.")
            MposButton("Продолжить редактирование",{confirmClose=false},style=ActionStyle.Secondary)
            MposButton("Закрыть без сохранения",{confirmClose=false;model.discardEdit()},style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseEditDiscard"))
        }
    } else MposDialog("Изменить заказ поставщику",::close,!locked,wide=true) {
        Text("${edit.baseline.source.supplierName} · Версия ${edit.baseline.requestVersion}")
        if(ui.message!=null) MposNotice(ui.message!!,ui.blocked || ui.orderUnresolved!=null)
        if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(ui.orderUnresolved!=null) MposButton("Проверить результат",model::retryOrder,enabled=!ui.pending,modifier=Modifier.testTag("purchaseOrderRetry"))
        val supplier=s.suppliers.find {it.id==edit.form.supplierId}
        val products=s.products.filter {it.id in supplier?.productIds.orEmpty()}
        Column(Modifier.weight(1f,fill=false).heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            products.forEach {p->
                val l=edit.form.lines.find {it.productId==p.id} ?: p.blankLine()
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(l.productName,style=MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        MposField(l.quantity,{v->model.editLine(p.id) {it.copy(quantity=v)}},"Количество",Modifier.width(170.dp).testTag("purchaseEditQty:${p.id}"),enabled,numeric=true)
                        MposSelect("Единица заказа",l.unit,units(p.unit)+PurchaseDraftRules.packs.map {it to purchaseUnitLabel(it)},
                            {v->model.editLine(p.id) {it.copy(unit=v)}},enabled,Modifier.width(190.dp).testTag("purchaseEditUnit:${p.id}"))
                        if(l.unit in PurchaseDraftRules.packs) {
                            MposField(l.packSize,{v->model.editLine(p.id) {it.copy(packSize=v)}},"В единице",Modifier.width(170.dp).testTag("purchaseEditPack:${p.id}"),enabled,numeric=true)
                            MposSelect("Единица внутри",l.contentUnit,units(p.unit),{v->model.editLine(p.id) {it.copy(contentUnit=v)}},enabled,Modifier.width(190.dp).testTag("purchaseEditContent:${p.id}"))
                        }
                    }
                    Text(expectation(l),modifier=Modifier.testTag("purchaseEditExpected:${p.id}"))
                    if(edit.form.lines.any {it.productId==p.id}) MposButton("Убрать строку",{model.removeEditLine(p.id)},enabled=enabled,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseEditRemove:${p.id}"))
                }
            }
            edit.form.lines.filter {l->products.none {it.id==l.productId}}.forEach {l->
                Text("${l.productName}: недоступен у поставщика. ${l.quantity} ${purchaseUnitLabel(l.unit)}, в единице ${l.packSize} ${l.contentUnit}")
                MposButton("Убрать строку",{model.removeEditLine(l.productId)},enabled=enabled,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseEditRemove:${l.productId}"))
            }
            if(products.isEmpty() && edit.form.lines.isEmpty()) Text("У поставщика нет доступных товаров")
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            MposButton("Сохранить изменения",model::saveEdit,enabled=enabled,modifier=Modifier.testTag("purchaseEditSave"))
            MposButton("Обновить ссылки",model::revalidateEdit,enabled=enabled,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseEditRevalidate"))
            MposButton("Отмена",::close,enabled=!locked,style=ActionStyle.Secondary)
        }
    }
}
