package com.mendelev.mpos.feature.products

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import java.math.RoundingMode

internal class CatalogNumbers(private val rows: List<ProductRow>,components: List<RecipeComponentEntity>,private val batches: Set<String> = emptySet()) {
    private val graph=recipeSnapshot(rows.map {it.product},components)
    private val calculator=RecipeCalculator(graph)
    private val balances=rows.associate {it.product.id to it.quantity.toBigDecimal()}
    fun cost(row: ProductRow)=runCatching {
        if(row.product.type=="simple") row.product.cost.toBigDecimal() else calculator.cost(listOf(RecipeRequest(row.product.id,if(row.product.id in batches) row.product.recipeYield.toBigDecimal() else BigDecimal.ONE,quantityUnit(row.product.unit))))
    }.getOrNull()
    fun stock(row: ProductRow): String {
        val p=row.product
        if(p.noStockTracking && p.type=="simple") return "—"
        return runCatching {
            val number=if(p.type=="simple") quantityUnit(p.unit).convert(row.quantity.toBigDecimal(),quantityUnit(p.displayUnit)) else calculator.physicalPortions(RecipeRequest(p.id,if(p.id in batches) p.recipeYield.toBigDecimal() else BigDecimal.ONE,quantityUnit(p.unit)),balances)
            if(number==null) "Не учитывается" else CatalogRules.canonical(number).replace('.',',')+" "+unitLabel(p.displayUnit)+
                if(p.type=="simple" && p.minimumStock!=null && row.quantity.toBigDecimal()<=p.minimumStock.toBigDecimal()) " · низкий" else ""
        }.getOrDefault("—")
    }
}
private fun catalogMoney(value: BigDecimal?)=value?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.replace('.',',')?.plus(" BYN") ?: "—"
@Composable internal fun CatalogPage(state: CatalogUi,search: String,onSearch: (String)->Unit,filter: String?,onFilter: (String?)->Unit,admin: Boolean,onCategories: ()->Unit,onAdd: ()->Unit,onEdit: (ProductRow)->Unit,modifier: Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    val sorted=remember(state.products) {state.products.sortedWith {a,b->CatalogPresentationRules.compareNames(a.product.name,b.product.name).takeIf {it!=0} ?: a.product.id.compareTo(b.product.id)}}
    val categories=remember(sorted) {sorted.map {it.product.categoryId to (it.categoryName ?: "Без категории")}.distinctBy {it.first}.sortedWith {a,b->CatalogPresentationRules.compareNames(a.second,b.second)}}
    val selected=filter.takeIf {id->categories.any {"category:"+(it.first ?: "")==id}}
    val visible=remember(sorted,selected,search) {sorted.filter {(selected==null || it.product.categoryId.orEmpty()==selected.removePrefix("category:")) && CatalogPresentationRules.matches(it.product.name,it.categoryName ?: "Без категории",search)}}
    val numbers=remember(state.products,state.components,state.recipeBatchIds) {CatalogNumbers(state.products,state.components,state.recipeBatchIds)}
    Column(modifier.fillMaxSize().padding(start=26.dp,end=26.dp,top=22.dp,bottom=40.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom=14.dp)) {
            val narrow=maxWidth<=768.dp
            @Composable fun title(modifier: Modifier=Modifier) {Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("Товары",fontSize=21.sp,lineHeight=28.sp,fontWeight=FontWeight.ExtraBold)
                Text("·",color=c.onSurfaceVariant,fontWeight=FontWeight.Bold)
                Text(state.products.size.toString(),fontSize=16.sp,fontWeight=FontWeight.Bold,color=c.onSurfaceVariant,modifier=Modifier.testTag("catalogCount"))
            }}
            @Composable fun actions(width: Dp?=null) {Row(if(width==null) Modifier else Modifier.width(width),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                val first=width?.let {(it-14.dp)/2}
                CatalogHeaderAction("Категории",onCategories,enabled=!state.busy,modifier=first?.let {Modifier.width(it)} ?: Modifier)
                CatalogHeaderAction("Добавить товар",onAdd,enabled=admin && !state.busy,primary=true,tag="catalogAdd",modifier=first?.let {Modifier.width(it+6.dp)} ?: Modifier)
            }}
            if(narrow) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {title();actions()}
                CatalogSearch(search,onSearch,Modifier.fillMaxWidth())
            } else {
                val measurer=rememberTextMeasurer();val density=LocalDensity.current
                val font=MaterialTheme.typography.bodyMedium.fontFamily
                fun width(text: String,size: Float,weight: FontWeight)=with(density) {measurer.measure(text,TextStyle(fontSize=size.sp,fontWeight=weight,fontFamily=font)).size.width.toDp()}
                val titleMin=width("Товары",21f,FontWeight.ExtraBold)+width("·",16f,FontWeight.Bold)+width(state.products.size.toString(),16f,FontWeight.Bold)+14.dp
                val actionsMin=width("Категории",14.5f,FontWeight.Bold)+width("Добавить товар",14.5f,FontWeight.Bold)+83.dp
                val searchWidth=(maxWidth-titleMin-actionsMin-24.dp).coerceIn(240.dp,440.dp)
                val titleWidth=((maxWidth-searchWidth-24.dp-actionsMin+titleMin)/2).coerceAtLeast(titleMin)
                val actionsWidth=maxWidth-titleWidth-searchWidth-24.dp
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    title(Modifier.width(titleWidth));CatalogSearch(search,onSearch,Modifier.width(searchWidth));actions(actionsWidth)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top=4.dp,bottom=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            val options=listOf(null to "Все")+categories.map { (id,name)->("category:"+(id ?: "")) to name }
            options.forEach {(id,name)->
                val active=id==selected
                Surface(onClick={onFilter(id)},modifier=Modifier.testTag("catalogCategory:${id.orEmpty()}").semantics {this.selected=active},shape=RoundedCornerShape(999.dp),border=BorderStroke(1.dp,if(active) c.primary else c.outline),color=if(active) c.primaryContainer else c.surface,contentColor=if(active) c.primary else c.onSurfaceVariant) {
                    Text(name,Modifier.padding(horizontal=14.dp,vertical=10.dp),fontSize=12.5.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold)
                }
            }
        }
        if(state.hasSavedDraft) Text("Есть сохранённый черновик. Кнопка «Добавить товар» откроет его.",Modifier.padding(bottom=12.dp),fontSize=12.sp,color=c.onSurfaceVariant)
        if(state.error && state.message!=null) MposNotice(state.message,true)
        if(!admin) Text("Для изменения каталога войдите в администрирование через Настройки",Modifier.padding(bottom=12.dp),fontSize=12.sp,color=c.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth().weight(1f,fill=false),shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,color=c.surface) {
            BoxWithConstraints(Modifier.padding(20.dp)) {
                val mobile=maxWidth<=728.dp
                val compact=maxWidth<=958.dp
                val widths=if(compact) listOf(95.dp,100.dp,110.dp,105.dp) else listOf(105.dp,110.dp,125.dp,120.dp)
                val gap=if(compact) 8.dp else 12.dp
                @Composable fun tableRow(row: ProductRow?,header: Boolean=false) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=if(header) 0.dp else 13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(gap)) {
                        @Composable fun text(value: String,modifier: Modifier,muted: Boolean=false) {Text(value,modifier,fontSize=if(header) 11.5.sp else 13.sp,lineHeight=18.sp,fontWeight=if(header) FontWeight.ExtraBold else FontWeight.SemiBold,color=if(header || muted) c.onSurfaceVariant else c.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis)}
                        if(row==null) text("ТОВАР",Modifier.weight(if(compact) 2f else 2.2f).heightIn(min=44.dp).wrapContentHeight())
                        else Column(Modifier.weight(if(compact) 2f else 2.2f)) {
                            Text(row.product.name,fontSize=14.5.sp,lineHeight=20.sp,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if(mobile) Row(Modifier.padding(top=5.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {CatalogTypeBadge(row.product.type);text(if(row.product.noStockTracking) "Остаток: ∞" else "Остаток "+numbers.stock(row),Modifier.weight(1f),true)}
                        }
                        if(!mobile) {
                            text(if(header) "КАТЕГОРИЯ" else row!!.categoryName ?: "Без категории",Modifier.weight(if(compact) 1f else 1.2f),true)
                            Box(Modifier.width(widths[0])) {if(header) text("ТИП",Modifier) else CatalogTypeBadge(row!!.product.type)}
                            text(if(header) "ЦЕНА" else catalogMoney(row!!.product.price.toBigDecimal()),Modifier.width(widths[1]))
                            text(if(header) "СЕБЕСТОИМОСТЬ" else catalogMoney(numbers.cost(row!!)),Modifier.width(widths[2]))
                            text(if(header) "ОСТАТОК" else numbers.stock(row!!),Modifier.width(widths[3]))
                        }
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().testTag("catalogProducts").semantics {testTagsAsResourceId=true}) {
                    if(visible.isEmpty()) item {Box(Modifier.fillMaxWidth().padding(vertical=20.dp),contentAlignment=Alignment.Center) {Text(if(state.products.isEmpty()) "Пока нет товаров. Добавьте первый." else "По вашему запросу товары не найдены.",fontSize=13.sp,color=c.onSurfaceVariant)}}
                    else {
                        if(!mobile) item {tableRow(null,true);Spacer(Modifier.height(10.dp))}
                        items(visible,key={it.product.id}) {row->
                            HorizontalDivider()
                            Box(Modifier.fillMaxWidth().testTag("catalogProduct:${row.product.id}").clickable(enabled=admin && !state.busy,onClickLabel="Открыть товар ${row.product.name}") {onEdit(row)}) {tableRow(row)}
                        }
                    }
                }
            }
        }
    }
}
@Composable internal fun CatalogTypeBadge(type: String) {Surface(shape=RoundedCornerShape(999.dp),color=if(type=="composite") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background) {Text(if(type=="composite") "Составной" else "Простой",Modifier.padding(horizontal=9.dp,vertical=4.dp),fontSize=11.sp,lineHeight=15.sp,fontWeight=FontWeight.Bold,color=if(type=="composite") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun CatalogHeaderAction(label: String,onClick: ()->Unit,enabled: Boolean,primary: Boolean=false,tag: String="",modifier: Modifier=Modifier) {Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp).testTag(tag),shape=RoundedCornerShape(999.dp),border=if(primary) null else BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline),color=if(primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,contentColor=if(primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {Box(Modifier.padding(horizontal=16.dp,vertical=12.dp),contentAlignment=Alignment.Center) {Text(label,fontSize=14.5.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)}}}
@Composable private fun CatalogSearch(value: String,onChange: (String)->Unit,modifier: Modifier) {
    val c=MaterialTheme.colorScheme
    Surface(modifier,shape=RoundedCornerShape(999.dp),border=BorderStroke(1.5.dp,c.outline),color=c.surface) {
        Row(Modifier.heightIn(min=48.dp).padding(start=17.dp,end=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            com.mendelev.mpos.feature.workspace.PosIcon("search",c.onSurfaceVariant)
            BasicTextField(value,onChange,Modifier.weight(1f).testTag("catalogSearch"),singleLine=true,textStyle=TextStyle(color=c.onSurface,fontSize=14.sp,fontFamily=MaterialTheme.typography.bodyMedium.fontFamily),decorationBox={inner->Box {if(value.isEmpty()) Text("Поиск товаров",fontSize=14.sp,color=c.onSurfaceVariant);inner()}})
            if(value.isNotEmpty()) TextButton({onChange("")},Modifier.size(38.dp).testTag("catalogSearchClear")) {Text("×",fontSize=20.sp)}
        }
    }
}
