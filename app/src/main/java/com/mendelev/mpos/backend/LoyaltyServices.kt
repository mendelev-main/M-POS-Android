package com.mendelev.mpos.backend

import android.content.Context
import com.mendelev.mpos.data.recovery.AppDataset

internal object LoyaltyServices {
    data class Product(val id: String,val name: String,val category: String)
    suspend fun products(context: Context): List<Product> {
        val db=com.mendelev.mpos.data.local.CatalogDatabase.get(context)
        val source=com.mendelev.mpos.domain.backup.WebSourceDocuments.decode(db.workspace().documentsByPrefix(com.mendelev.mpos.domain.backup.WebSourceDocuments.KEY).associate {it.id to it.payload})
        val imported=source?.let {com.mendelev.mpos.domain.backup.WebCatalogMapping.map(it).products.associate {p->p.id to p.sourceId}}.orEmpty()
        val categories=db.catalog().categorySnapshot().associate {it.id to it.name}
        return db.catalog().allProducts().map {Product(imported[it.id] ?: it.id,it.name,categories[it.categoryId] ?: "Без категории")}.sortedBy {it.name.lowercase()}
    }
    fun settings(context: Context): BackendSettings {
        check(AppDataset.runtime.blocking.value==null) {"Данные восстанавливаются. Повторите действие после завершения"}
        val s=BackendAccess(context,0).store.read().validated()
        check(s.datasetId==AppDataset.paths(context).id) {"После восстановления подтвердите подключение backend в настройках"}
        return s
    }
    fun api(context: Context)=LoyaltyApi({settings(context)})
}
