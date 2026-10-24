package com.mendelev.mpos.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "categories", indices = [Index(value = ["normalizedKey"], unique = true)])
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val normalizedKey: String, val sortOrder: Int, val revision: Long)

@Entity(tableName = "products", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId")])
data class ProductEntity(@PrimaryKey val id: String, val categoryId: String?, val name: String, val searchKey: String,
    val price: String, val cost: String, val revision: Long, val currency: String = "BYN", val unit: String = "piece", val type: String = "simple",
    @ColumnInfo(defaultValue = "'piece'") val displayUnit: String = unit,
    @ColumnInfo(defaultValue = "0") val noStockTracking: Boolean = false,
    @ColumnInfo(defaultValue = "0") val allowNegativeStock: Boolean = false,
    val minimumStock: String? = null, @ColumnInfo(defaultValue = "''") val purchaseLabel: String = "",
    val packSize: String? = null, @ColumnInfo(defaultValue = "'1'") val recipeYield: String = "1")

@Entity(tableName = "recipe_components", primaryKeys = ["ownerId", "position"], foreignKeys = [
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["ownerId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("productId")])
data class RecipeComponentEntity(val ownerId: String, val position: Int, val productId: String, val quantity: String, val unit: String)

@Entity(tableName = "stock_balances", foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)])
data class BalanceEntity(@PrimaryKey val productId: String, val quantity: String, val revision: Long)

@Entity(tableName = "stock_movements", foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("productId"), Index(value = ["commandId"], unique = true)])
data class MovementEntity(@PrimaryKey val id: String, val productId: String, val delta: String, val source: String,
    val commandId: String, val createdAt: Long, val reason: String? = null)

@Entity(tableName = "operation_results")
data class ResultEntity(@PrimaryKey val commandId: String, val fingerprint: String, val resultId: String)
@Entity(tableName = "audit_events", indices = [Index("commandId")])
data class AuditEntity(@PrimaryKey val id: String, val commandId: String, val actorId: String, val action: String, val targetId: String, val createdAt: Long)
@Entity(tableName = "editor_drafts")
data class DraftEntity(@PrimaryKey val id: String = "product", val payload: String)
@Entity(tableName = "app_config")
data class ConfigEntity(@PrimaryKey val id: Int = 1, val registerId: String, val ownerId: String, val currency: String = "BYN")

data class ProductRow(@Embedded val product: ProductEntity, val quantity: String, val stockRevision: Long, val categoryName: String?)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT p.*, COALESCE(b.quantity, '0') AS quantity, COALESCE(b.revision, 0) AS stockRevision, c.name AS categoryName FROM products p LEFT JOIN stock_balances b ON p.id=b.productId LEFT JOIN categories c ON c.id=p.categoryId ORDER BY p.name, p.id")
    fun products(): Flow<List<ProductRow>>
    @Query("SELECT * FROM products") suspend fun allProducts(): List<ProductEntity>
    @Query("SELECT * FROM recipe_components ORDER BY ownerId, position") suspend fun allComponents(): List<RecipeComponentEntity>
    @Query("SELECT * FROM recipe_components ORDER BY ownerId, position") fun components(): Flow<List<RecipeComponentEntity>>
    @Query("SELECT * FROM recipe_components WHERE ownerId=:id ORDER BY position") suspend fun recipe(id: String): List<RecipeComponentEntity>
    @Query("SELECT COUNT(*) FROM recipe_components WHERE productId=:id") suspend fun recipeUses(id: String): Int
    @Query("DELETE FROM recipe_components WHERE ownerId=:id") suspend fun clearRecipe(id: String)
    @Insert suspend fun insertComponents(values: List<RecipeComponentEntity>)
    @Query("SELECT * FROM products WHERE id=:id") suspend fun product(id: String): ProductEntity?
    @Query("SELECT * FROM categories WHERE id=:id") suspend fun category(id: String): CategoryEntity?
    @Query("SELECT * FROM categories WHERE normalizedKey=:key") suspend fun categoryByKey(key: String): CategoryEntity?
    @Query("SELECT * FROM stock_balances WHERE productId=:id") suspend fun balance(id: String): BalanceEntity?
    @Query("SELECT * FROM operation_results WHERE commandId=:id") suspend fun result(id: String): ResultEntity?
    @Query("SELECT * FROM editor_drafts WHERE id='product'") suspend fun draft(): DraftEntity?
    @Query("SELECT * FROM app_config WHERE id=1") suspend fun config(): ConfigEntity?
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Query("SELECT COUNT(*) FROM products WHERE categoryId=:id") suspend fun categoryUses(id: String): Int
    @Query("SELECT COUNT(*) FROM stock_movements WHERE productId=:id") suspend fun movementCount(id: String): Int
    @Insert suspend fun insertProduct(value: ProductEntity)
    @Update suspend fun updateProduct(value: ProductEntity)
    @Insert suspend fun insertCategory(value: CategoryEntity)
    @Update suspend fun updateCategory(value: CategoryEntity)
    @Insert suspend fun insertBalance(value: BalanceEntity)
    @Update suspend fun updateBalance(value: BalanceEntity)
    @Insert suspend fun movement(value: MovementEntity)
    @Insert suspend fun result(value: ResultEntity)
    @Insert suspend fun audit(value: AuditEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun draft(value: DraftEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun config(value: ConfigEntity)
    @Query("DELETE FROM editor_drafts WHERE id='product'") suspend fun clearDraft()
    @Query("DELETE FROM categories WHERE id=:id") suspend fun deleteCategory(id: String)
    @Query("DELETE FROM stock_balances WHERE productId=:id") suspend fun deleteBalance(id: String)
    @Query("DELETE FROM products WHERE id=:id") suspend fun deleteProduct(id: String)
}

@Database(entities = [CategoryEntity::class, ProductEntity::class, BalanceEntity::class, MovementEntity::class,
    ResultEntity::class, AuditEntity::class, DraftEntity::class, ConfigEntity::class, RecipeComponentEntity::class], version = 2, exportSchema = true)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN displayUnit TEXT NOT NULL DEFAULT 'piece'")
                db.execSQL("ALTER TABLE products ADD COLUMN noStockTracking INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN allowNegativeStock INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN minimumStock TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN purchaseLabel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN packSize TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN recipeYield TEXT NOT NULL DEFAULT '1'")
                db.execSQL("UPDATE products SET displayUnit=unit")
                db.execSQL("CREATE TABLE IF NOT EXISTS recipe_components (ownerId TEXT NOT NULL, position INTEGER NOT NULL, productId TEXT NOT NULL, quantity TEXT NOT NULL, unit TEXT NOT NULL, PRIMARY KEY(ownerId, position), FOREIGN KEY(ownerId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_components_productId ON recipe_components (productId)")
            }
        }
        @Volatile private var instance: CatalogDatabase? = null
        fun get(context: Context): CatalogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CatalogDatabase::class.java, "mpos.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
