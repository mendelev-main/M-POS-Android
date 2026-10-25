package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.CatalogFailure

data class HomeTile(val type: String,val id: String,val slot: Int)
data class HomeLayout(val customized: Boolean = false,val tiles: List<HomeTile> = emptyList())
sealed interface HomeAction {
    data class Add(val type: String,val id: String): HomeAction
    data class Move(val type: String,val id: String,val slot: Int): HomeAction
    data class Remove(val type: String,val id: String): HomeAction
    data object Reset: HomeAction
}
object HomeEngine {
    const val MAX_TILES=20
    const val MAX_SLOTS=100
    fun validate(layout: HomeLayout): HomeLayout {
        if(layout.tiles.size>MAX_TILES || layout.tiles.any {it.type !in setOf("category","product") || it.slot !in 0 until MAX_SLOTS} ||
            layout.tiles.map {it.type to it.id}.distinct().size!=layout.tiles.size || layout.tiles.map {it.slot}.distinct().size!=layout.tiles.size)
            throw CatalogFailure("layout","Некорректная раскладка главного экрана")
        return layout
    }
    fun project(layout: HomeLayout,products: List<WorkspaceProduct>,categories: List<String>): HomeLayout {
        validate(layout)
        val validCategories=categories.toSet()+""
        if(!layout.customized) return HomeLayout(false,(categories+listOfNotNull("".takeIf {products.any {p->p.categoryId.isEmpty()}})).distinct().take(MAX_TILES).mapIndexed {index,id->HomeTile("category",id,index)})
        return layout.copy(tiles=layout.tiles.filter {if(it.type=="category") it.id in validCategories else products.any {p->p.id==it.id}})
    }
    fun apply(layout: HomeLayout,products: List<WorkspaceProduct>,categories: List<String>,action: HomeAction): HomeLayout {
        if(action is HomeAction.Reset) return HomeLayout()
        val tiles=project(layout,products,categories).tiles.toMutableList()
        fun firstFree(start: Int = 0): Int = (start until MAX_SLOTS).firstOrNull {slot->tiles.none {it.slot==slot}}
            ?: (0 until start).firstOrNull {slot->tiles.none {it.slot==slot}}
            ?: throw CatalogFailure("layout","Нет свободного места")
        when(action) {
            is HomeAction.Add -> {
                if(action.type !in setOf("category","product") || (if(action.type=="category") action.id !in categories+"" else products.none {it.id==action.id}))
                    throw CatalogFailure("reference","Плитка больше не существует")
                if(tiles.any {it.type==action.type && it.id==action.id}) throw CatalogFailure("layout","Плитка уже добавлена")
                if(tiles.size>=MAX_TILES) throw CatalogFailure("layout","Можно разместить максимум 20 плиток")
                tiles.add(HomeTile(action.type,action.id,firstFree()))
            }
            is HomeAction.Move -> {
                if(action.slot !in 0 until MAX_SLOTS) throw CatalogFailure("layout","Укажите место от 1 до 100")
                val old=tiles.find {it.type==action.type && it.id==action.id} ?: throw CatalogFailure("conflict","Плитка больше не существует")
                // Like Web nearestFreeCell: an occupied destination never displaces another tile.
                val occupied=tiles.any {it.slot==action.slot && it!=old}
                val target=if(occupied) (0 until MAX_SLOTS).filter {slot->tiles.none {it!=old && it.slot==slot}}
                    .minWith(compareBy<Int> {kotlin.math.abs(it%5-action.slot%5)+kotlin.math.abs(it/5-action.slot/5)}.thenBy {it}) else action.slot
                tiles[tiles.indexOf(old)]=old.copy(slot=target)
            }
            is HomeAction.Remove -> {
                if(!tiles.removeAll {it.type==action.type && it.id==action.id}) throw CatalogFailure("conflict","Плитка больше не существует")
            }
            is HomeAction.Reset -> Unit
        }
        return validate(HomeLayout(true,tiles))
    }
}
