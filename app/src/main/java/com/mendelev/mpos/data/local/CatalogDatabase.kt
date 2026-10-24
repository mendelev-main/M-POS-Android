package com.mendelev.mpos.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "categories", indices = [Index(value = ["normalizedKey"], unique = true)])
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val normalizedKey: String, val sortOrder: Int, val revision: Long,
    @ColumnInfo(defaultValue = "'#EEF1F5'") val color: String = "#EEF1F5",
    @ColumnInfo(defaultValue = "''") val symbol: String = "")

@Entity(tableName = "products", foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("categoryId")])
data class ProductEntity(@PrimaryKey val id: String, val categoryId: String?, val name: String, val searchKey: String,
    val price: String, val cost: String, val revision: Long, val currency: String = "BYN", val unit: String = "piece", val type: String = "simple",
    @ColumnInfo(defaultValue = "'piece'") val displayUnit: String = unit,
    @ColumnInfo(defaultValue = "0") val noStockTracking: Boolean = false,
    @ColumnInfo(defaultValue = "0") val allowNegativeStock: Boolean = false,
    val minimumStock: String? = null, @ColumnInfo(defaultValue = "''") val purchaseLabel: String = "",
    val packSize: String? = null, @ColumnInfo(defaultValue = "'1'") val recipeYield: String = "1", val photoId: String? = null)

@Entity(tableName = "recipe_components", primaryKeys = ["ownerId", "position"], foreignKeys = [
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["ownerId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("productId")])
data class RecipeComponentEntity(val ownerId: String, val position: Int, val productId: String, val quantity: String, val unit: String)

@Entity(tableName = "modifier_groups", primaryKeys = ["ownerId", "id"], foreignKeys = [
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["ownerId"], onDelete = ForeignKey.CASCADE)])
data class ModifierGroupEntity(val ownerId: String, val id: String, val name: String, val minimum: Int, val maximum: Int, val position: Int)

@Entity(tableName = "modifier_options", primaryKeys = ["ownerId", "id"], foreignKeys = [
    ForeignKey(entity = ModifierGroupEntity::class, parentColumns = ["ownerId", "id"], childColumns = ["ownerId", "groupId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["ownerId", "groupId"]), Index("productId")])
data class ModifierOptionEntity(val ownerId: String, val id: String, val groupId: String, val productId: String, val posName: String,
    val quantity: String, val priceDelta: String, val position: Int)

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

@Entity(tableName = "workspace_documents")
data class WorkspaceDocument(@PrimaryKey val id: String, val revision: Long, val payload: String)

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace_documents ORDER BY id") fun observe(): Flow<List<WorkspaceDocument>>
    @Query("SELECT * FROM workspace_documents WHERE id=:id") suspend fun document(id: String): WorkspaceDocument?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: WorkspaceDocument)
}

data class ProductRow(@Embedded val product: ProductEntity, val quantity: String, val stockRevision: Long, val categoryName: String?)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name, id") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT p.*, COALESCE(b.quantity, '0') AS quantity, COALESCE(b.revision, 0) AS stockRevision, c.name AS categoryName FROM products p LEFT JOIN stock_balances b ON p.id=b.productId LEFT JOIN categories c ON c.id=p.categoryId ORDER BY p.name, p.id")
    fun products(): Flow<List<ProductRow>>
    @Query("SELECT * FROM modifier_groups WHERE ownerId=:id ORDER BY position") suspend fun modifierGroups(id: String): List<ModifierGroupEntity>
    @Query("SELECT * FROM modifier_options WHERE ownerId=:id ORDER BY position") suspend fun modifierOptions(id: String): List<ModifierOptionEntity>
    @Query("SELECT COUNT(*) FROM modifier_options WHERE productId=:id") suspend fun modifierUses(id: String): Int
    @Query("DELETE FROM modifier_options WHERE ownerId=:id") suspend fun clearModifierOptions(id: String)
    @Query("DELETE FROM modifier_groups WHERE ownerId=:id") suspend fun clearModifierGroups(id: String)
    @Insert suspend fun insertModifierGroups(values: List<ModifierGroupEntity>)
    @Insert suspend fun insertModifierOptions(values: List<ModifierOptionEntity>)
    @Query("SELECT * FROM stock_balances") suspend fun allBalances(): List<BalanceEntity>
    @Query("SELECT * FROM modifier_groups ORDER BY ownerId, position") suspend fun allModifierGroups(): List<ModifierGroupEntity>
    @Query("SELECT * FROM modifier_options ORDER BY ownerId, position") suspend fun allModifierOptions(): List<ModifierOptionEntity>
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
    @Query("SELECT * FROM categories ORDER BY sortOrder, name, id") suspend fun categorySnapshot(): List<CategoryEntity>
    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories") suspend fun nextCategoryOrder(): Int
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
    ResultEntity::class, AuditEntity::class, DraftEntity::class, ConfigEntity::class, RecipeComponentEntity::class, ModifierGroupEntity::class, ModifierOptionEntity::class, WorkspaceDocument::class], version = 5, exportSchema = true)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun workspace(): WorkspaceDao
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
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN photoId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS modifier_groups (ownerId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, minimum INTEGER NOT NULL, maximum INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(ownerId, id), FOREIGN KEY(ownerId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS modifier_options (ownerId TEXT NOT NULL, id TEXT NOT NULL, groupId TEXT NOT NULL, productId TEXT NOT NULL, posName TEXT NOT NULL, quantity TEXT NOT NULL, priceDelta TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(ownerId, id), FOREIGN KEY(ownerId, groupId) REFERENCES modifier_groups(ownerId, id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_modifier_options_ownerId_groupId ON modifier_options(ownerId, groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_modifier_options_productId ON modifier_options(productId)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN color TEXT NOT NULL DEFAULT '#EEF1F5'")
                db.execSQL("ALTER TABLE categories ADD COLUMN symbol TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS workspace_documents (id TEXT NOT NULL PRIMARY KEY, revision INTEGER NOT NULL, payload TEXT NOT NULL)")
            }
        }
        @Volatile private var instance: CatalogDatabase? = null
        fun get(context: Context): CatalogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CatalogDatabase::class.java, "mpos.db")
                .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5).build().also { instance = it }
        }
    }
}
