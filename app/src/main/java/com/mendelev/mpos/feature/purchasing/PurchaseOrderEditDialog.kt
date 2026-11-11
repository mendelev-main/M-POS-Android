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
@Composable internal fun PurchaseOrderEditDialog(ui: PurchaseUi,model: PurchaseViewModel,onClosed: (String)->Unit={}) {
    val edit=ui.editing ?: return;val s=ui.snapshot ?: return
    val locked=ui.pending || ui.orderUnresolved!=null
    val enabled=!locked && !ui.blocked
    var confirmClose by rememberSaveable(edit.baseline.id) {mutableStateOf(false)}
    fun close() {if(!locked) {if(edit.dirty) confirmClose=true else {model.discardEdit();onClosed(edit.baseline.id)}}}
    if(confirmClose) {
        MposDialog("Закрыть без сохранения?",{confirmClose=false}) {
            Text("Несохранённые изменения заказа будут потеряны. Сохранённые версии останутся в истории.")
            PurchaseAction("Продолжить редактирование",{confirmClose=false},style=ActionStyle.Secondary)
            PurchaseAction("Закрыть без сохранения",{confirmClose=false;model.discardEdit();onClosed(edit.baseline.id)},style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseEditDiscard"))
        }
    } else MposDialog("Изменить заказ поставщику",::close,!locked,maxWidth=1100.dp) {
        Text(edit.baseline.source.supplierName,style=MaterialTheme.typography.titleSmall)
        Text(purchaseDate(edit.baseline.createdAt),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(ui.message!=null) MposNotice(ui.message!!,ui.blocked || ui.orderUnresolved!=null)
        if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(ui.orderUnresolved!=null) PurchaseAction("Проверить результат",model::retryOrder,enabled=!ui.pending,modifier=Modifier.testTag("purchaseOrderRetry"))
        val supplier=s.suppliers.find {it.id==edit.form.supplierId}
        val products=s.products.filter {it.id in supplier?.productIds.orEmpty()}.sortedBy {it.name.lowercase()}
        Column(Modifier.weight(1f,fill=false).heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            products.forEach {p->
                val l=edit.form.lines.find {it.productId==p.id} ?: p.blankLine()
                PurchaseRequestRow(p,l,enabled,"purchaseEdit") {next->if(l.quantity.isNotBlank() && next.quantity.isBlank()) model.removeEditLine(p.id) else model.editLine(p.id) {next}}
            }
            edit.form.lines.filter {l->products.none {it.id==l.productId}}.forEach {l->
                Text("${l.productName}: недоступен у поставщика. ${l.quantity} ${purchaseUnitLabel(l.unit)}, в единице ${l.packSize} ${l.contentUnit}")
                PurchaseAction("Убрать строку",{model.removeEditLine(l.productId)},enabled=enabled,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseEditRemove:${l.productId}"))
            }
            if(products.isEmpty() && edit.form.lines.isEmpty()) Text("У поставщика нет доступных товаров")
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            PurchaseAction("Отмена",::close,Modifier.weight(1f),enabled=!locked,style=ActionStyle.Secondary)
            PurchaseAction("Сохранить изменения",model::saveEdit,enabled=enabled,modifier=Modifier.weight(1f).testTag("purchaseEditSave"))
        }
        if(ui.message!=null && ui.orderUnresolved==null) PurchaseAction("Обновить ссылки",model::revalidateEdit,enabled=enabled,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseEditRevalidate"))
    }
}
