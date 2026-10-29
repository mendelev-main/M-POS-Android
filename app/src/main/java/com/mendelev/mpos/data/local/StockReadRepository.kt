package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import java.time.Clock

data class StockRow(val id: String,val name: String,val category: String,val unit: String,val quantity: String?,val minimum: String?,val revision: Long) {
    val low get()=quantity!=null && minimum!=null && quantity.toBigDecimal()<=minimum.toBigDecimal()
}
data class StockSnapshot(val rows: List<StockRow>,val selectedId: String?,val movements: List<MovementEntity>,val at: Long,val zone: String=java.time.ZoneId.systemDefault().id)
data class StockDocument(val title: String,val at: Long,val lines: List<String>)
fun interface StockReadAccess {
    suspend fun snapshot(productId: String?): StockSnapshot
    suspend fun document(movementId: String): StockDocument = error("Документ недоступен")
}
/** Read-only: a single transaction binds current balances and the selected movement history. */
class StockReadRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemDefaultZone()): StockReadAccess {
    override suspend fun document(movementId: String): StockDocument = StockDocumentReader(db).read(movementId)
    override suspend fun snapshot(productId: String?): StockSnapshot=db.withTransaction {
        val categories=db.catalog().categorySnapshot().associate {it.id to it.name}
        val balances=db.catalog().allBalances().associateBy {it.productId}
        val rows=db.catalog().allProducts().filter {it.type=="simple"}.map {p->
            val b=checkNotNull(balances[p.id]) {"Не найден остаток товара"}
            com.mendelev.mpos.domain.warehouse.ReceivingRules.quantity(b.quantity)
            require(b.revision>=1)
            p.minimumStock?.let {require(com.mendelev.mpos.domain.warehouse.PurchaseDraftRules.number(it)!=null)}
            StockRow(p.id,p.name,categories[p.categoryId] ?: "Без категории",p.unit,if(p.noStockTracking) null else b.quantity,p.minimumStock,b.revision)
        }.sortedWith(compareBy({com.mendelev.mpos.domain.catalog.CatalogRules.key(it.name)},{it.id}))
        require(productId==null || rows.any {it.id==productId}) {"Товар больше недоступен. Вернитесь к списку"}
        StockSnapshot(rows,productId,productId?.let {db.catalog().stockHistory(it)} ?: emptyList(),clock.millis(),clock.zone.id)
    }
}
