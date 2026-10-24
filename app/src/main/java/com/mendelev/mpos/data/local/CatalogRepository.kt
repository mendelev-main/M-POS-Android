package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.catalog.*
import java.util.UUID

class CatalogRepository(private val db: CatalogDatabase, private val afterProductWrite: (() -> Unit)? = null) {
    val dao = db.catalog()
    val products = dao.products()
    val categories = dao.categories()
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
            val balance = dao.balance(input.id)
            if (old != null && input.stockEdited && balance?.revision != input.expectedStockRevision)
                throw CatalogFailure("conflict", "Остаток изменился. Перечитайте карточку: введённое значение не сохранено")
            val product = ProductEntity(input.id, input.categoryId, input.name, CatalogRules.key(input.name), input.price, input.cost, (old?.revision ?: 0) + 1)
            if (old == null) dao.insertProduct(product) else dao.updateProduct(product)
            afterProductWrite?.invoke() // fault seam: rollback must be proved against the actual Room transaction.
            if (old == null || input.stockEdited) {
                val target = input.stock.toBigDecimal(); val delta = target - (balance?.quantity?.toBigDecimal() ?: java.math.BigDecimal.ZERO)
                val next = BalanceEntity(input.id, input.stock, (balance?.revision ?: 0) + if (delta.signum() != 0 || old == null) 1 else 0)
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
        if (dao.movementCount(id) > 0) throw CatalogFailure("used", "Есть складская история: удалить товар нельзя")
        dao.deleteBalance(id); dao.deleteProduct(id); dao.clearDraft(); record(commandId, fp, id, "product.delete")
    }
}
