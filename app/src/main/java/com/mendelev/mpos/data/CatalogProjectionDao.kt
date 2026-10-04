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

    @Query("SELECT * FROM product_projection ORDER BY sortIndex ASC, id ASC")
    suspend fun allProducts(): List<ProductProjectionEntity>

    @Query("SELECT COUNT(*) FROM category_projection")
    suspend fun categoryCount(): Int

    @Query("SELECT * FROM category_projection ORDER BY sortIndex ASC, name ASC")
    suspend fun allCategories(): List<CategoryProjectionEntity>
}
