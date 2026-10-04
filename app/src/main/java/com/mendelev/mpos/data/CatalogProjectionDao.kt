package com.mendelev.mpos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CatalogProjectionDao {
    @Query("DELETE FROM product_projection")
    suspend fun clearProducts()

    @Query("DELETE FROM category_projection")
    suspend fun clearCategories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductProjectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryProjectionEntity>)

    @Query("SELECT COUNT(*) FROM product_projection")
    suspend fun productCount(): Int

    @Query("SELECT COUNT(*) FROM category_projection")
    suspend fun categoryCount(): Int
}
