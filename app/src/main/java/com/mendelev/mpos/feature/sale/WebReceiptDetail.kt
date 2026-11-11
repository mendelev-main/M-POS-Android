package com.mendelev.mpos.feature.sale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.data.local.WebReceiptRecord
import com.mendelev.mpos.domain.shift.ShiftRules
import org.json.JSONObject

@Composable internal fun WebReceiptDetail(record: WebReceiptRecord,modifier: Modifier=Modifier) {
    val r=record.raw
    fun date(value: Long)=if(value<=0) "Дата не указана" else java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(value))
    fun money(v: Any?)=v?.toString()?.toBigDecimalOrNull()?.setScale(2,java.math.RoundingMode.HALF_UP)?.toPlainString()?.plus(" BYN") ?: "—"
    Surface(modifier,shape=RoundedCornerShape(topStart=22.dp,topEnd=22.dp)) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(record.label,fontSize=20.sp,fontWeight=FontWeight.ExtraBold)
            Text(date(record.time),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(record.returned) "Возвращён" else "Оплачен",color=if(record.returned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
            r.optString("employeeName").takeIf {it.isNotBlank()}?.let {Text("Сотрудник: $it")}
            r.optString("orderType").takeIf {it.isNotBlank()}?.let {Text(it)}
            r.optString("orderLabel").takeIf {it.isNotBlank()}?.let {Text(it,fontWeight=FontWeight.Bold)}
            r.optJSONObject("customer")?.let {customer->
                listOf("name","phone","address").forEach {key->customer.optString(key).takeIf {it.isNotBlank()}?.let {Text(it,fontSize=13.sp)}}
            }
            val items=r.optJSONArray("items")
            if(items!=null) repeat(items.length()) {i->val item=items.getJSONObject(i)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(item.optString("name",item.optString("productName","Товар")),fontWeight=FontWeight.Bold)
                        Text("${item.opt("qty") ?: item.opt("quantity") ?: "—"} × ${money(item.opt("price"))}",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        item.optString("comment").takeIf {it.isNotBlank()}?.let {Text(it,fontSize=13.sp)}
                        val mods=item.optJSONArray("modifiers")
                        if(mods!=null) repeat(mods.length()) {j->Text(mods.getJSONObject(j).optString("name"),fontSize=13.sp)}
                        item.optString("discountName").takeIf {it.isNotBlank()}?.let {Text("Скидка: $it",fontSize=13.sp)}
                        item.optJSONObject("discount")?.let {d->Text("Скидка: "+d.optString("name")+" "+d.opt("value"),fontSize=13.sp)}
                    }
                    val q=(item.opt("qty") ?: item.opt("quantity"))?.toString()?.toBigDecimalOrNull()
                    val price=item.opt("price")?.toString()?.toBigDecimalOrNull()
                    val gross=if(q!=null && price!=null) q*price else null
                    val discount=item.opt("discountValue")?.toString()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                    val reduction=if(gross==null || item.optString("discountName").isBlank()) java.math.BigDecimal.ZERO else
                        if(item.optString("discountType")=="percent") gross*discount/java.math.BigDecimal(100) else (discount*(q ?: java.math.BigDecimal.ZERO)).min(gross)
                    Text(money(gross?.minus(reduction)))
                }
                HorizontalDivider()
            }
            if(r.opt("productDiscountTotal")?.toString()?.toBigDecimalOrNull()?.signum()==1) Text("Скидки на товары: "+money(r.opt("productDiscountTotal")))
            if(r.opt("loyaltyDiscount")?.toString()?.toBigDecimalOrNull()?.signum()==1) Text("Лояльность: "+money(r.opt("loyaltyDiscount")))
            r.optString("comment").takeIf {it.isNotBlank()}?.let {Text(it)}
            if(r.opt("deliveryFee")?.toString()?.toBigDecimalOrNull()?.signum()==1) Text("Доставка: "+money(r.opt("deliveryFee")))
            Text("Итого: "+if(record.data.isNull("total")) "—" else ShiftRules.money(record.data.getLong("total")),fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
            val payments=r.optJSONArray("payments")
            if(payments!=null) repeat(payments.length()) {i->val payment=payments.getJSONObject(i)
                Text((if(payment.optString("method")=="cash") "Наличные: " else "Карта: ")+money(payment.opt("amount")))
                if(payment.has("cashGiven") && !payment.isNull("cashGiven")) Text("Получено: "+money(payment.opt("cashGiven")),fontSize=13.sp)
                if(payment.has("change") && !payment.isNull("change")) Text("Сдача: "+money(payment.opt("change")),fontSize=13.sp)
            } else Text(when(r.optString("method")) {"cash"->"Наличные";"card"->"Карта";else->"Оплата Web"})
            if(record.returned) {
                Text("Возврат: "+date(r.optLong("returnedAt")),fontWeight=FontWeight.Bold)
                Text("Возвращено: "+money(r.opt("returnAmount") ?: r.opt("total")))
            }
        }
    }
}
