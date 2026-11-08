package com.mendelev.mpos.feature.products

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.media.ProductPhotoStore
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable fun ProductPhotoEditor(model: CatalogViewModel, d: ProductDraft, enabled: Boolean, onPick: () -> Unit) {
    HorizontalDivider(color=MaterialTheme.colorScheme.outline)
    Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Surface(Modifier.size(120.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.background,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
            Box(contentAlignment=androidx.compose.ui.Alignment.Center) {
                if(d.photoId==null) ProductEditorNote("Фото товара") else ProductPhoto(d.photoId,model.photos,Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Фото товара",style=MaterialTheme.typography.titleSmall)
            ProductEditorNote("Изображение для онлайн-меню.",Modifier.padding(top=8.dp,bottom=12.dp))
            ProductEditorButton("Выбрать фото",onPick,enabled=enabled)
            Spacer(Modifier.height(8.dp))
            ProductEditorButton("Убрать фото",{model.change {it.copy(photoId=null)}},enabled=enabled && d.photoId!=null)
        }
    }

}

@Composable fun ProductModifierEditor(model: CatalogViewModel, state: CatalogUi, d: ProductDraft) {
    ProductSectionHeading("Модификаторы")
    ProductEditorNote("Варианты связаны с существующими товарами и списываются с их остатков.")
    val enabled= !state.busy
    fun group(index: Int, transform: (ModifierGroupInput) -> ModifierGroupInput) {
        model.change { draft -> draft.copy(modifierGroups=draft.modifierGroups.mapIndexed { i,g -> if(i==index) transform(g) else g }) }
    }
    d.modifierGroups.forEachIndexed { gi,g ->
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            ProductEditorField(g.name,{v -> group(gi) { it.copy(name=v) }},"Название группы ${gi+1}",Modifier.testTag("modifierGroup$gi"),enabled)
            ProductEditorField(g.minimum,{v -> group(gi) { it.copy(minimum=v) }},"Минимум вариантов ${gi+1}",enabled=enabled,numeric=true)
            ProductEditorField(g.maximum,{v -> group(gi) { it.copy(maximum=v) }},"Максимум вариантов ${gi+1}",enabled=enabled,numeric=true)
            g.options.forEachIndexed { oi,o ->
                fun option(transform: (ModifierOptionInput) -> ModifierOptionInput) { group(gi) { current ->
                    current.copy(options=current.options.mapIndexed { i,value -> if(i==oi) transform(value) else value })
                } }
                val product=state.products.find { it.product.id==o.productId }?.product
                ProductPickerField("Товар",product?.name ?: "Выберите товар",state.products.filter {it.product.id!=d.id && (it.product.id==o.productId || g.options.none {option->option.productId==it.product.id})},enabled,d.sessionId,"modifierProduct$gi-$oi") {row->
                    option {it.copy(productId=row.product.id)}
                }
                ProductEditorField(o.posName,{v -> option { it.copy(posName=v) }},"Название в POS ${gi+1}.${oi+1}",enabled=enabled)
                ProductEditorField(o.quantity,{v -> option { it.copy(quantity=v) }},"Количество ${gi+1}.${oi+1}, ${unitLabel(product?.unit ?: "piece")}",Modifier.testTag("modifierQty$gi-$oi"),enabled,true)
                ProductEditorField(o.priceDelta,{v -> option { it.copy(priceDelta=v) }},"Доплата ${gi+1}.${oi+1}, BYN",Modifier.testTag("modifierPrice$gi-$oi"),enabled,true)
                ProductEditorButton("Удалить вариант ${gi+1}.${oi+1}",{ group(gi) { it.copy(options=it.options.filterIndexed { index,_ -> index!=oi }) } },enabled=enabled,style=ActionStyle.Secondary)
            }
            val choices=state.products.filter {it.product.id!=d.id && g.options.none {o->o.productId==it.product.id}}
            if(choices.isNotEmpty()) ProductPickerField("Добавить вариант в группу ${gi+1}","Выбрать вариант ${gi+1}",choices,enabled,d.sessionId,"modifierAdd$gi") {row->
                group(gi) {it.copy(options=it.options+ModifierOptionInput(UUID.randomUUID().toString(),row.product.id))}
            }
            ProductEditorButton("Удалить группу ${gi+1}",{ model.change { it.copy(modifierGroups=it.modifierGroups.filterIndexed { index,_ -> index!=gi }) } },enabled=enabled,style=ActionStyle.Danger)
            HorizontalDivider()
        }
    }
    ProductEditorButton("Добавить группу",{ model.change { it.copy(modifierGroups=it.modifierGroups+ModifierGroupInput(UUID.randomUUID().toString(),"")) } },
        enabled=enabled && d.modifierGroups.size<20,style=ActionStyle.Secondary)
    Text("Выбор вариантов доступен в заказе. Количество задаётся в базовой единице товара; доплата — за одну основную позицию.",style=MaterialTheme.typography.bodySmall)
}
