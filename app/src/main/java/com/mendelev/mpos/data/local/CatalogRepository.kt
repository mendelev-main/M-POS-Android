package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.catalog.*
import java.util.UUID

class CatalogRepository(private val db: CatalogDatabase, private val afterProductWrite: (() -> Unit)? = null) {
    val dao = db.catalog()
    val products = dao.products()
    val categories = dao.categories()
    val components = dao.components()
    suspend fun editorSnapshot(id: String): Triple<ProductEntity, BalanceEntity?, List<RecipeComponentEntity>> = db.withTransaction {
        val product=dao.product(id) ?: throw CatalogFailure("conflict", "Товар больше не существует")
        Triple(product,dao.balance(id),dao.recipe(id))
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
        return db.withTransaction {
            repeat(input.commandId, fp)?.let { return@withTransaction it }
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
                packSize=input.packSize?.let(::baseQuantity), recipeYield=input.recipeYield)
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
            if (input.type == "simple" && (old == null || input.stockEdited)) {
                val targetText = baseQuantity(input.stock)
                val target = targetText.toBigDecimal(); val delta = target - (balance?.quantity?.toBigDecimal() ?: java.math.BigDecimal.ZERO)
                val next = BalanceEntity(input.id, targetText, (balance?.revision ?: 0) + if (delta.signum() != 0 || old == null) 1 else 0)
                if (balance == null) dao.insertBalance(next) else dao.updateBalance(next)
                if (delta.signum() != 0) dao.movement(MovementEntity(UUID.randomUUID().toString(), input.id, CatalogRules.canonical(delta),
                    if (old == null) "opening" else "product_card", input.commandId, System.currentTimeMillis()))
            }
            record(input.commandId, fp, input.id, if (old == null) "product.create" else "product.update")
            dao.clearDraft()
            input.id
        }
    }
    suspend fun saveCategory(id: String, expectedRevision: Long?, rawName: String, commandId: String): String {
        val name = CatalogRules.name(rawName, 100); val key = CatalogRules.key(name)
        val fp = CatalogRules.fingerprint("category", id, expectedRevision, name)
        return db.withTransaction {
            repeat(commandId, fp)?.let { return@withTransaction it }
            val old = dao.category(id)
            if (old?.revision != expectedRevision) throw CatalogFailure("conflict", "Категория изменилась. Откройте её заново")
            if (dao.categoryByKey(key)?.id?.let { it != id } == true) throw CatalogFailure("name", "Такая категория уже существует")
            val category = CategoryEntity(id, name, key, old?.sortOrder ?: dao.categoryCount(), (old?.revision ?: 0) + 1)
            if (old == null) dao.insertCategory(category) else dao.updateCategory(category)
            record(commandId, fp, id, "category.save"); id
        }
    }
    suspend fun deleteCategory(id: String, revision: Long, commandId: String) = db.withTransaction {
        val fp = CatalogRules.fingerprint("deleteCategory", id, revision)
        if (repeat(commandId, fp) != null) return@withTransaction
        if (dao.category(id)?.revision != revision) throw CatalogFailure("conflict", "Категория изменилась")
        if (dao.categoryUses(id) > 0) throw CatalogFailure("used", "В категории есть товары. Сначала переместите их")
        dao.deleteCategory(id); record(commandId, fp, id, "category.delete")
    }
    suspend fun deleteProduct(id: String, revision: Long, commandId: String) = db.withTransaction {
        val fp = CatalogRules.fingerprint("deleteProduct", id, revision)
        if (repeat(commandId, fp) != null) return@withTransaction
        if (dao.product(id)?.revision != revision) throw CatalogFailure("conflict", "Товар изменился")
        if (dao.recipeUses(id) > 0) throw CatalogFailure("used", "Товар используется в рецептуре: сначала измените состав")
        if (dao.movementCount(id) > 0) throw CatalogFailure("used", "Есть складская история: удалить товар нельзя")
        dao.clearRecipe(id); dao.deleteBalance(id); dao.deleteProduct(id); dao.clearDraft(); record(commandId, fp, id, "product.delete")
    }
}
