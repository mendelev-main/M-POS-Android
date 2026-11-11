package com.mendelev.mpos.feature.products

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ProductEditorButton("Выбрать фото",onPick,Modifier.weight(1f).testTag("productPickPhoto"),enabled)
                ProductEditorButton("Удалить фото",{model.change {it.copy(photoId=null)}},Modifier.weight(1f).testTag("productRemovePhoto"),enabled && d.photoId!=null)
            }
        }
    }

}

@Composable fun ProductModifierEditor(model: CatalogViewModel, state: CatalogUi, d: ProductDraft) {
    val enabled=!state.busy
    fun group(index: Int,transform: (ModifierGroupInput)->ModifierGroupInput) {
        model.change {draft->draft.copy(modifierGroups=draft.modifierGroups.mapIndexed {i,g->if(i==index) transform(g) else g})}
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Модификаторы",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold)
        ProductEditorButton("+ Группа",{model.change {it.copy(modifierGroups=it.modifierGroups+ModifierGroupInput(UUID.randomUUID().toString(),"",minimum="1"))}},
            Modifier.testTag("modifierAddGroup"),enabled && d.modifierGroups.size<20)
    }
    if(d.modifierGroups.isEmpty()) ProductEditorNote("Модификаторов пока нет. Добавьте группу вариантов.")
    d.modifierGroups.forEachIndexed {gi,g->key(g.id) {
        Surface(Modifier.fillMaxWidth().testTag("modifierGroupCard$gi"),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val name: @Composable (Modifier)->Unit={m->Box(m) {ProductEditorField(g.name,{v->group(gi) {it.copy(name=v)}},"Название группы",Modifier.testTag("modifierGroup$gi"),enabled,placeholder="Например, Начинка")}}
                    val limits: @Composable (Modifier)->Unit={m->Row(m,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {ProductEditorField(g.minimum,{v->group(gi) {it.copy(minimum=v)}},"Минимум вариантов",Modifier.testTag("modifierMinimum$gi"),enabled,true)}
                        Box(Modifier.weight(1f)) {ProductEditorField(g.maximum,{v->group(gi) {it.copy(maximum=v)}},"Максимум вариантов",Modifier.testTag("modifierMaximum$gi"),enabled,true)}
                    }}
                    val remove: @Composable ()->Unit={ModifierDelete("Удалить группу",enabled,Modifier.padding(top=22.dp).testTag("modifierDeleteGroup$gi")) {
                        model.change {it.copy(modifierGroups=it.modifierGroups.filterIndexed {i,_->i!=gi})}
                    }}
                    if(maxWidth>=560.dp) Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {name(Modifier.weight(1.3f));limits(Modifier.weight(1f));remove()}
                    else Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {name(Modifier.weight(1f));remove()}
                        limits(Modifier.fillMaxWidth())
                    }
                }
                val min=g.minimum.toIntOrNull();val max=g.maximum.toIntOrNull()
                ProductEditorNote(when {
                    min==null || max==null || min<0 || max<1 || min>max->"Проверьте минимум и максимум вариантов."
                    min>0 && max==1->"Обязательная группа · выбрать 1"
                    min>0->"Обязательная группа · выбрать от $min до $max"
                    else->"Необязательная группа · выбрать до $max"
                })
                if(g.options.isEmpty()) ProductEditorNote("Добавьте первый вариант.")
                g.options.forEachIndexed {oi,o->key(o.id) {
                    fun option(transform: (ModifierOptionInput)->ModifierOptionInput) {group(gi) {current->
                        current.copy(options=current.options.mapIndexed {i,v->if(i==oi) transform(v) else v})
                    }}
                    val product=state.products.find {it.product.id==o.productId}?.product
                    Surface(Modifier.fillMaxWidth().testTag("modifierOption$gi-$oi"),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            ModifierFieldPair({m->Box(m) {
                                ProductPickerField("Товар",product?.name ?: "Выберите товар",state.products.filter {it.product.id!=d.id && (it.product.id==o.productId || g.options.none {v->v.productId==it.product.id})},
                                    enabled,d.sessionId,"modifierProduct$gi-$oi") {row->option {it.copy(productId=row.product.id)}}
                            }},{m->Box(m) {
                                ProductEditorField(o.posName,{v->option {it.copy(posName=v)}},"Название в POS",Modifier.testTag("modifierName$gi-$oi"),enabled,placeholder=product?.name ?: "Название для кассы")
                            }})
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
                                Box(Modifier.weight(1f)) {ProductEditorField(o.quantity,{v->option {it.copy(quantity=v)}},"Количество для списания, ${unitLabel(product?.unit ?: "piece")}",Modifier.testTag("modifierQty$gi-$oi"),enabled,true)}
                                Box(Modifier.weight(1f)) {ProductEditorField(o.priceDelta,{v->option {it.copy(priceDelta=v)}},"Доплата к цене, BYN",Modifier.testTag("modifierPrice$gi-$oi"),enabled,true)}
                                ModifierDelete("Удалить вариант",enabled,Modifier.testTag("modifierDeleteOption$gi-$oi")) {
                                    group(gi) {it.copy(options=it.options.filterIndexed {i,_->i!=oi})}
                                }
                            }
                        }
                    }
                }}
                val choices=state.products.filter {it.product.id!=d.id && g.options.none {o->o.productId==it.product.id}}
                if(choices.isNotEmpty()) ProductPickerField("Добавить вариант","Выбрать товар",choices,enabled,d.sessionId,"modifierAdd$gi") {row->
                    group(gi) {it.copy(options=it.options+ModifierOptionInput(UUID.randomUUID().toString(),row.product.id))}
                }
            }
        }
    }}
}

@Composable private fun ModifierFieldPair(left: @Composable (Modifier)->Unit,right: @Composable (Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth>=450.dp) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {left(Modifier.weight(1f));right(Modifier.weight(1f))}
        else Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {left(Modifier.fillMaxWidth());right(Modifier.fillMaxWidth())}
    }
}
@Composable private fun ModifierDelete(label: String,enabled: Boolean,modifier: Modifier=Modifier,onClick: ()->Unit) {
    MposPosControl("",onClick,modifier.size(44.dp).semantics {contentDescription=label},enabled,PosControl.Delete,44.dp,12.dp,icon="delete")
}
