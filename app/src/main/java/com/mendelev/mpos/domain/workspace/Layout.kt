package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*

data class LayoutItem(val type: String, val id: String, val categoryId: String, val folderId: String = "", val name: String = "")
sealed interface LayoutAction {
    data class SaveFolder(val id: String, val categoryId: String, val name: String): LayoutAction
    data class DeleteFolder(val id: String): LayoutAction
    data class MoveProduct(val productId: String, val folderId: String): LayoutAction
    data class ResetProduct(val productId: String): LayoutAction
    data class Step(val type: String, val id: String, val delta: Int): LayoutAction
}
object LayoutEngine {
    fun project(items: List<LayoutItem>, products: List<WorkspaceProduct>, categories: Set<String>): List<LayoutItem> {
        val byId=products.associateBy { it.id };val seen=mutableSetOf<String>()
        val valid=items.filter {
            (it.type=="folder" && it.categoryId in categories || it.type=="product" && byId[it.id]?.categoryId==it.categoryId) && seen.add("${it.type}:${it.id}")
        }
        val folders=valid.filter { it.type=="folder" }.associateBy { it.id }
        return valid.map { if(it.type=="product" && folders[it.folderId]?.categoryId!=it.categoryId) it.copy(folderId="") else it } +
            products.filter { p -> valid.none { it.type=="product" && it.id==p.id } }.map { LayoutItem("product",it.id,it.categoryId) }
    }
    fun apply(saved: List<LayoutItem>, products: List<WorkspaceProduct>, categories: Set<String>, action: LayoutAction): List<LayoutItem> {
        val items=project(saved,products,categories).toMutableList()
        when(action) {
            is LayoutAction.SaveFolder -> {
                if(action.categoryId !in categories || action.id.isBlank() || action.id.length>100) throw CatalogFailure("folder","Категория или папка недоступны")
                val name=CatalogRules.name(action.name,80)
                val old=items.find { it.type=="folder" && it.id==action.id }
                if(old!=null && old.categoryId!=action.categoryId) throw CatalogFailure("folder","Папка принадлежит другой категории")
                if(items.any { it.type=="folder" && it.categoryId==action.categoryId && it.id!=action.id && CatalogRules.key(it.name)==CatalogRules.key(name) })
                    throw CatalogFailure("folder","Папка с таким названием уже есть")
                if(old==null) items.add(LayoutItem("folder",action.id,action.categoryId,name=name))
                else items[items.indexOf(old)]=old.copy(name=name)
            }
            is LayoutAction.DeleteFolder -> {
                val old=items.find { it.type=="folder" && it.id==action.id } ?: throw CatalogFailure("folder","Папка больше не существует")
                items.remove(old)
                items.indices.forEach { i -> if(items[i].folderId==old.id) items[i]=items[i].copy(folderId="") }
            }
            is LayoutAction.MoveProduct -> {
                val old=items.find { it.type=="product" && it.id==action.productId } ?: throw CatalogFailure("reference","Товар больше не существует")
                if(action.folderId.isNotEmpty() && items.none { it.type=="folder" && it.id==action.folderId && it.categoryId==old.categoryId })
                    throw CatalogFailure("folder","Выберите папку этой категории")
                items.remove(old);items.add(old.copy(folderId=action.folderId))
            }
            is LayoutAction.ResetProduct -> return items.filterNot { it.type=="product" && it.id==action.productId }
            is LayoutAction.Step -> {
                if(action.delta !in listOf(-1,1)) throw CatalogFailure("layout","Некорректный шаг раскладки")
                val old=items.find { it.type==action.type && it.id==action.id } ?: throw CatalogFailure("layout","Плитка больше не существует")
                val indexes=items.indices.filter { items[it].categoryId==old.categoryId && items[it].folderId==old.folderId }
                val index=indexes.indexOf(items.indexOf(old));val target=index+action.delta
                if(target !in indexes.indices) throw CatalogFailure("layout","Плитка уже на краю списка")
                val a=indexes[index];val b=indexes[target];val temp=items[a];items[a]=items[b];items[b]=temp
            }
        }
        return items
    }
}
