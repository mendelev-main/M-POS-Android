package com.mendelev.mpos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CatalogRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun input(id: String="p", command: String="create", revision: Long?=null, stockRevision: Long?=null,
        stock: String="10", edited: Boolean=false, name: String="Чай") =
        ProductInput(id,revision,name,null,"2.50","0.5",stock,stockRevision,edited,command)
    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block(); fail("Expected failure") } catch(_: CatalogFailure) { }
    }
    @Test fun cardCostEditPersistsWithoutStockMovementAndRejectsStaleOrInvalidInput() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            repo.saveProduct(input().copy(unit="kg",displayUnit="kg"))
            val balance=repo.dao.balance("p")
            val movements=repo.dao.movementCount("p")
            // 0.003 BYN/g is saved as 3 BYN/kg, without changing the quantity.
            val change=input(command="cost",revision=1,stockRevision=1).copy(unit="kg",displayUnit="g",cost="0.003")
            repo.saveProduct(change);repo.saveProduct(change)
            assertEquals("3",repo.dao.product("p")!!.cost)
            assertEquals(2L,repo.dao.product("p")!!.revision)
            assertEquals(balance,repo.dao.balance("p"));assertEquals(movements,repo.dao.movementCount("p"))
            expectFailure {repo.saveProduct(change.copy(commandId="stale",cost="0.004"))}
            expectFailure {repo.saveProduct(change.copy(commandId="negative",expectedRevision=2,cost="-1"))}
            assertEquals("3",repo.dao.product("p")!!.cost)
            assertEquals(balance,repo.dao.balance("p"))
        } finally {db.close()}
    }
    @Test fun repeatConflictAndStockChangesAreAtomic() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo = CatalogRepository(db);repo.initialize()
            val original=input(); repo.saveProduct(original);repo.saveProduct(original)
            assertEquals(1,repo.dao.products().first().size);assertEquals(1,repo.dao.movementCount("p"))
            expectFailure { repo.saveProduct(original.copy(name="Другой")) }
            val adjust=input(command="adjust",revision=1,stockRevision=1,stock="8",edited=true)
            repo.saveProduct(adjust);repo.saveProduct(adjust)
            assertEquals("8",repo.dao.balance("p")!!.quantity);assertEquals(2,repo.dao.movementCount("p"))
            expectFailure { repo.saveProduct(input(command="stale",revision=2,stockRevision=1,stock="5",edited=true,name="Потеря")) }
            assertEquals("Чай",repo.dao.product("p")!!.name);assertEquals("8",repo.dao.balance("p")!!.quantity)
            // Metadata-only save must preserve stock changed since the editor opened.
            repo.saveProduct(input(command="rename",revision=2,stockRevision=1,stock="10",name="Чай новый"))
            assertEquals("8",repo.dao.balance("p")!!.quantity)
            expectFailure { repo.deleteProduct("p",3,"delete") }
        } finally { db.close() }
    }
    @Test fun actualTransactionRollsBackAfterProductWrite() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db) { throw java.io.IOException("injected disk failure") };repo.initialize()
            try { repo.saveProduct(input());fail("Expected write failure") } catch(_: java.io.IOException) { }
            assertNull(repo.dao.product("p"));assertNull(repo.dao.balance("p"));assertNull(repo.dao.result("create"))
            assertEquals(0,repo.dao.movementCount("p"))
        } finally { db.close() }
    }
    @Test fun categoryReferencesDuplicateNamesAndConcurrentRevisions() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,CatalogDatabase::class.java).build()
        try {
            val repo=CatalogRepository(db);repo.initialize()
            repo.saveCategory("c",null,"Напитки","cat")
            expectFailure { repo.saveCategory("d",null," напитки ","duplicate") }
            repo.saveProduct(input(stock="0").copy(categoryId="c"))
            repo.saveCategory("c",1,"Горячие","rename-category")
            assertEquals("c",repo.dao.product("p")!!.categoryId)
            expectFailure { repo.deleteCategory("c",2,"delcat") }
            val outcomes=coroutineScope {
                (1..2).map { n -> async(Dispatchers.IO) {
                    try { repo.saveProduct(input(command="edit$n",revision=1,stockRevision=1,stock="0",name="Чай$n").copy(categoryId="c"));true }
                    catch(_: CatalogFailure) { false }
                } }.awaitAll()
            }
            assertEquals(1,outcomes.count { it }); assertEquals(2,repo.dao.product("p")!!.revision)
        } finally { db.close() }
    }
    @Test fun diskReopenPreservesCommittedDataAndEditorDraft() = runBlocking {
        val name="catalog-test-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,CatalogDatabase::class.java,name).build()
        var db=open()
        try {
            var repo=CatalogRepository(db);repo.initialize();repo.saveProduct(input())
            val draft=com.mendelev.mpos.feature.products.ProductDraft(name="Черновик",dirty=true)
            repo.dao.draft(DraftEntity(payload=draft.json()))
            val owner=repo.dao.config()!!.ownerId
            db.close();db=open();repo=CatalogRepository(db);repo.initialize()
            assertEquals("Чай",repo.dao.product("p")!!.name);assertEquals("10",repo.dao.balance("p")!!.quantity)
            assertEquals("Черновик",com.mendelev.mpos.feature.products.ProductDraft.parse(repo.dao.draft()!!.payload).name)
            assertEquals(owner,repo.dao.config()!!.ownerId)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
