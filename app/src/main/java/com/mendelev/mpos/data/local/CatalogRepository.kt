package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.catalog.*
import java.util.UUID
import com.mendelev.mpos.data.media.ProductPhotoStore
import org.json.JSONObject

data class CatalogEditorSnapshot(val product: ProductEntity, val balance: BalanceEntity?, val components: List<RecipeComponentEntity>, val modifierGroups: List<ModifierGroupInput>)

class CatalogRepository(private val db: CatalogDatabase, val photos: ProductPhotoStore? = null, private val afterCategoryOrderWrite: (() -> Unit)? = null, private val afterCategoryWrite: (() -> Unit)? = null, private val afterProductWrite: (() -> Unit)? = null) {
    val dao = db.catalog()
    val products = dao.products()
    val categories = dao.categories()
    val components = dao.components()
    suspend fun editorSnapshot(id: String): CatalogEditorSnapshot = db.withTransaction {
        val product=dao.product(id) ?: throw CatalogFailure("conflict", "Товар больше не существует")
        val options=dao.modifierOptions(id)
        val groups=dao.modifierGroups(id).map { g -> ModifierGroupInput(g.id,g.name,g.minimum.toString(),g.maximum.toString(),
            options.filter { it.groupId==g.id }.map { ModifierOptionInput(it.id,it.productId,it.posName,it.quantity,it.priceDelta) }) }
        CatalogEditorSnapshot(product,dao.balance(id),dao.recipe(id),groups)
    }
    private fun requirePhoto(id: String?) {
        if(id!=null) (photos ?: throw CatalogFailure("photo","Хранилище фото недоступно")).requirePrepared(id)
    }
    suspend fun discardDraft()=db.adminTransaction {dao.clearDraft()}
    suspend fun saveDraft(draft: DraftEntity) = db.adminTransaction {
        requirePhoto(JSONObject(draft.payload).optString("photoId").takeIf { it.isNotEmpty() && it!="null" })
        dao.draft(draft)
    }
    suspend fun cleanupPhotos() = db.withTransaction {
        val refs=dao.allProducts().mapNotNull { it.photoId }.toMutableSet()
        dao.draft()?.let { draft -> JSONObject(draft.payload).optString("photoId").takeIf { it.isNotEmpty() && it!="null" }?.let(refs::add) }
        photos?.cleanup(refs)
    }
    suspend fun initialize() { dao.config(ConfigEntity(registerId = UUID.randomUUID().toString(), ownerId = UUID.randomUUID().toString())) }
    private suspend fun repeat(command: String, fingerprint: String): String? = dao.result(command)?.let {
        if (it.fingerprint != fingerprint) throw CatalogFailure("conflict", "Команда уже использована для другого изменения")
        it.resultId
    }
    private suspend fun record(command: String, fp: String, id: String, action: String) {
        val owner = dao.config()?.ownerId ?: error("Хранилище ещё не готово")
        dao.result(ResultEntity(command, fp, id))
        dao.audit(AuditEntity(UUID.randomUUID().toString(), command, owner, action, id, System.currentTimeMillis()))
    }
    suspend fun saveProduct(raw: ProductInput): String {
        val input = raw.validated(); val fp = "product:" + input.fingerprint()
        return db.adminTransaction {
            repeat(input.commandId, fp)?.let { return@adminTransaction it }
            SplitPaymentRepository(db).requireProductUnlocked(input.id)
            requirePhoto(input.photoId)
            val groups=ModifierRules.validate(input.id,input.modifierGroups,dao.allProducts().map { it.id }.toSet())
            val old = dao.product(input.id)
            if (old?.revision != input.expectedRevision) throw CatalogFailure("conflict", "Товар изменился. Перечитайте карточку перед сохранением")
            if (input.categoryId != null && dao.category(input.categoryId) == null) throw CatalogFailure("category", "Категория больше не существует")
            if (old != null && (old.type != input.type || old.unit != input.unit))
                throw CatalogFailure("type", "Тип и базовая единица уже назначены. Создайте отдельный товар")
            val balance = dao.balance(input.id)
            if (old != null && input.stockEdited && balance?.revision != input.expectedStockRevision)
                throw CatalogFailure("conflict", "Остаток изменился. Перечитайте карточку: введённое значение не сохранено")
            val display = quantityUnit(input.displayUnit); val base = quantityUnit(input.unit)
            fun baseQuantity(value: String) = CatalogRules.canonical(display.convert(value.toBigDecimal(), base)).also {
                val n = it.toBigDecimal()
                if (n.scale() > 12 || n.precision() - n.scale() > 18) throw CatalogFailure("precision", "Количество вне допустимой точности склада")
            }
            val baseCost = CatalogRules.canonical(input.cost.toBigDecimal().divide(display.convert(java.math.BigDecimal.ONE, base)))
            if (baseCost.toBigDecimal().scale() > 12) throw CatalogFailure("precision", "Себестоимость вне допустимой точности")
            val product = ProductEntity(input.id, input.categoryId, input.name, CatalogRules.key(input.name), input.price, baseCost,
                (old?.revision ?: 0) + 1, unit=input.unit, type=input.type, displayUnit=input.displayUnit,
                noStockTracking=input.noStockTracking, allowNegativeStock=input.allowNegativeStock,
                minimumStock=input.minimumStock?.let(::baseQuantity), purchaseLabel=input.purchaseLabel,
                packSize=input.packSize?.let(::baseQuantity), recipeYield=input.recipeYield, photoId=input.photoId)
            if (old == null) dao.insertProduct(product) else dao.updateProduct(product)
            afterProductWrite?.invoke() // fault seam: rollback must be proved against the actual Room transaction.
            dao.clearRecipe(input.id)
            val nextComponents = input.components.mapIndexed { index, c ->
                RecipeComponentEntity(input.id, index, c.productId, c.quantity, c.unit)
            }
            val graph = recipeSnapshot(dao.allProducts(), dao.allComponents() + nextComponents)
            val calculator = RecipeCalculator(graph)
            // Validate every root: changing a nested yield/unit must not break an existing parent recipe.
            graph.filterIsInstance<RecipeProduct.Composite>().forEach {
                val request = listOf(RecipeRequest(it.id, java.math.BigDecimal.ONE, it.unit))
                calculator.consumption(request); calculator.cost(request)
            }
            dao.insertComponents(nextComponents)
            dao.clearModifierOptions(input.id);dao.clearModifierGroups(input.id)
            dao.insertModifierGroups(groups.mapIndexed { index,g -> ModifierGroupEntity(input.id,g.id,g.name,g.minimum.toInt(),g.maximum.toInt(),index) })
            dao.insertModifierOptions(groups.flatMap { g -> g.options.mapIndexed { index,o ->
                ModifierOptionEntity(input.id,o.id,g.id,o.productId,o.posName,o.quantity,o.priceDelta,index)
            } })
            if (input.type == "simple" && (old == null || input.stockEdited)) {
                val targetText = baseQuantity(input.stock)
                val target = targetText.toBigDecimal(); val delta = target - (balance?.quantity?.toBigDecimal() ?: java.math.BigDecimal.ZERO)
                val next = BalanceEntity(input.id, targetText, (balance?.revision ?: 0) + if (delta.signum() != 0 || old == null || old.noStockTracking != input.noStockTracking) 1 else 0)
                if (balance == null) dao.insertBalance(next) else dao.updateBalance(next)
                if (delta.signum() != 0) dao.movement(MovementEntity(UUID.randomUUID().toString(), input.id, CatalogRules.canonical(delta),
                    if (old == null) "opening" else "product_card", input.commandId, System.currentTimeMillis()))
            }
            if (input.type == "simple" && old != null && !input.stockEdited && old.noStockTracking != input.noStockTracking) {
                val current=balance ?: error("Нет складского baseline")
                dao.updateBalance(current.copy(revision=Math.addExact(current.revision,1)))
            }
            record(input.commandId, fp, input.id, if (old == null) "product.create" else "product.update")
            dao.clearDraft()
            input.id
        }
    }
    suspend fun saveCategory(id: String, expectedRevision: Long?, rawName: String, commandId: String, color: String = CategoryAppearance.DEFAULT_COLOR, symbol: String = ""): String {
        val name = CatalogRules.name(rawName, 100); val key = CatalogRules.key(name)
        val cleanColor=CategoryAppearance.color(color); val cleanSymbol=CategoryAppearance.symbol(symbol)
        val legacyFp = CatalogRules.fingerprint("category", id, expectedRevision, name)
        val fp = if(cleanColor==CategoryAppearance.DEFAULT_COLOR && cleanSymbol.isEmpty()) legacyFp
            else CatalogRules.fingerprint("categoryAppearance",legacyFp,cleanColor,cleanSymbol)
        return db.adminTransaction {
            repeat(commandId, fp)?.let { return@adminTransaction it }
            SplitPaymentRepository(db).requireCategoryUnlocked(id)
            val old = dao.category(id)
            if (old?.revision != expectedRevision) throw CatalogFailure("conflict", "Категория изменилась. Откройте её заново")
            if (dao.categoryByKey(key)?.id?.let { it != id } == true) throw CatalogFailure("name", "Такая категория уже существует")
            val category = CategoryEntity(id, name, key, old?.sortOrder ?: dao.nextCategoryOrder(), (old?.revision ?: 0) + 1, cleanColor, cleanSymbol)
            if (old == null) dao.insertCategory(category) else dao.updateCategory(category)
            afterCategoryWrite?.invoke()
            record(commandId, fp, id, "category.save"); id
        }
    }
    suspend fun reorderCategories(order: List<CategoryOrderEntry>, commandId: String) = db.adminTransaction {
        val fp=CatalogRules.fingerprint("categoryOrder",*order.flatMap { listOf(it.id,it.revision.toString()) }.toTypedArray())
        if(repeat(commandId,fp)!=null) return@adminTransaction
        val current=dao.categorySnapshot()
        if(order.map { it.id }.distinct().size!=order.size || current.map { it.id }.toSet()!=order.map { it.id }.toSet() ||
            order.any { entry -> current.find { it.id==entry.id }?.revision!=entry.revision })
            throw CatalogFailure("categoryOrder","Список категорий изменился. Повторите перестановку по обновлённому списку")
        val byId=current.associateBy { it.id }
        order.forEachIndexed { index,entry ->
            val old=byId.getValue(entry.id)
            if(old.sortOrder!=index) {
                dao.updateCategory(old.copy(sortOrder=index,revision=old.revision+1))
                afterCategoryOrderWrite?.invoke()
            }
        }
        record(commandId,fp,"categories","category.reorder")
    }
    suspend fun deleteCategory(id: String, revision: Long, commandId: String) = db.adminTransaction {
        val fp = CatalogRules.fingerprint("deleteCategory", id, revision)
        if (repeat(commandId, fp) != null) return@adminTransaction
        SplitPaymentRepository(db).requireCategoryUnlocked(id)
        if (dao.category(id)?.revision != revision) throw CatalogFailure("conflict", "Категория изменилась")
        if (dao.categoryUses(id) > 0) throw CatalogFailure("used", "В категории есть товары. Сначала переместите их")
        db.requireNoWorkspaceCategory(id)
        dao.deleteCategory(id); record(commandId, fp, id, "category.delete")
    }
    suspend fun deleteProduct(id: String, revision: Long, commandId: String) = db.adminTransaction {
        val fp = CatalogRules.fingerprint("deleteProduct", id, revision)
        if (repeat(commandId, fp) != null) return@adminTransaction
        SplitPaymentRepository(db).requireProductUnlocked(id)
        if (dao.product(id)?.revision != revision) throw CatalogFailure("conflict", "Товар изменился")
        db.requireNoWorkspaceProduct(id)
        val draft=dao.draft()?.let { JSONObject(it.payload) }
        if(draft!=null && draft.optString("id")!=id) {
            val components=draft.optJSONArray("components")
            val groups=draft.optJSONArray("modifierGroups")
            val recipeUse=(0 until (components?.length() ?: 0)).any { components!!.getJSONObject(it).optString("productId")==id }
            val modifierUse=(0 until (groups?.length() ?: 0)).any { gi ->
                val options=groups!!.getJSONObject(gi).optJSONArray("options")
                (0 until (options?.length() ?: 0)).any { options!!.getJSONObject(it).optString("productId")==id }
            }
            if(recipeUse || modifierUse) throw CatalogFailure("used","Товар используется в несохранённой карточке")
        }
        InventoryRepository(db).requireProductUnused(id)
        SupplierRepository(db).requireProductUnlinked(id)
        PurchaseDraftRepository(db).requireProductUnused(id)
        if (dao.modifierUses(id) > 0) throw CatalogFailure("used", "Товар используется в модификаторах: сначала измените варианты")
        if (dao.recipeUses(id) > 0) throw CatalogFailure("used", "Товар используется в рецептуре: сначала измените состав")
        if (db.sales().productUses(id)>0) throw CatalogFailure("used", "Товар есть в истории чеков: удалить нельзя")
        if (dao.movementCount(id) > 0) throw CatalogFailure("used", "Есть складская история: удалить товар нельзя")
        dao.clearModifierOptions(id);dao.clearModifierGroups(id);dao.clearRecipe(id); dao.deleteBalance(id); dao.deleteProduct(id); if(draft?.optString("id")==id) dao.clearDraft(); record(commandId, fp, id, "product.delete")
    }
}
