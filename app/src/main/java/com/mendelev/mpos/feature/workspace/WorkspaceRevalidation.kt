package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import java.math.BigDecimal

@Composable internal fun WorkspaceRevalidationDialog(s: WorkspaceSnapshot,ui: WorkspaceUi,owner: String,revision: Long,
    token: String?,onClose: ()->Unit,onConfirm: (String)->Unit) {
    val result=runCatching {CartRevalidator.preview(s.cart,s.products,s.graph,s.balances,OrderReservations.total(s.parked.map {it.cart}))}
    val plan=result.getOrNull()
    val same=s.cart.sessionId==owner && s.cartRevision==revision && token!=null && plan?.token==token
    fun amount(q: BigDecimal)=CatalogRules.canonical(q)
    MposDialog("Пересчёт заказа",onClose,ui.pending==0,wide=true) {
        if(ui.message!=null) MposNotice(ui.message,true)
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(plan==null) Text((result.exceptionOrNull() as? CatalogFailure)?.message ?: "Не удалось пересчитать заказ",color=MaterialTheme.colorScheme.error)
            else if(!same) MposNotice("Данные изменились. Закройте окно и откройте пересчёт заново",true)
            else {
                Text("Сумма: ${amount(CartEngine.total(plan.before))} → ${amount(CartEngine.total(plan.after))} BYN",modifier=Modifier.testTag("revalidationTotal"))
                plan.before.lines.zip(plan.after.lines).filter {(a,b)->a!=b}.forEach {(a,b) ->
                    Text("${a.name} → ${b.name}: ${a.quantity} ${unitLabel(a.unit)}, цена ${a.unitPrice} → ${b.unitPrice} BYN")
                    Text("Варианты: ${a.options.joinToString { "${it.name} × ${it.quantity} (${it.priceDelta})" }.ifBlank {"нет"}} → ${b.options.joinToString { "${it.name} × ${it.quantity} (${it.priceDelta})" }.ifBlank {"нет"}}",style=MaterialTheme.typography.bodySmall)
                }
                Text("Расход ингредиентов",style=MaterialTheme.typography.titleSmall)
                val ids=(plan.oldConsumption.keys+plan.newConsumption.keys).sorted()
                ids.forEach {id ->
                    val old=plan.oldConsumption[id] ?: BigDecimal.ZERO;val next=plan.newConsumption[id] ?: BigDecimal.ZERO
                    fun unitLabelOf(cart: CurrentCart)=when(cart.graph.find {it.id==id}?.unit) {
                        QuantityUnit.PIECE->"шт";QuantityUnit.KG->"кг";QuantityUnit.G->"г";QuantityUnit.L->"л";QuantityUnit.ML->"мл";null->""
                    }
                    Text("${s.products.find {it.id==id}?.name ?: id}: ${amount(old)} ${unitLabelOf(plan.before)} → ${amount(next)} ${unitLabelOf(plan.after)}")
                }
                Text("Количество и параметры заказа сохранятся. Остатки склада не изменятся.",style=MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена",onClose,enabled=ui.pending==0,style=ActionStyle.Secondary)
            MposButton("Применить пересчёт",{token?.let(onConfirm)},enabled=ui.pending==0 && same,modifier=Modifier.testTag("revalidationConfirm"))
        }
    }
}
