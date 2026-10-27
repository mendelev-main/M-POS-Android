package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test

class SupplierRulesTest {
    private fun save(id: String="s",rev: Long?=null,name: String="Поставщик",ids: Set<String> = setOf("p"))=
        SupplierCommand("cmd","owner",SupplierAction.Save(id,rev,name,ids))
    private fun reject(block: ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun sourceNameAndSimpleLinksAreValidatedAndSnapshotsCopied() {
        val selected=mutableSetOf("p")
        val rows=SupplierRules.apply(emptyList(),save(name="  ООО   Чай  ",ids=selected),mapOf("p" to "simple"))
        selected.clear()
        assertEquals("ООО Чай",rows.single().name);assertEquals(setOf("p"),rows.single().productIds)
        reject {SupplierRules.apply(emptyList(),save(name=" "),mapOf("p" to "simple"))}
        reject {SupplierRules.apply(emptyList(),save(),mapOf("p" to "composite"))}
        reject {SupplierRules.apply(emptyList(),save(),emptyMap())}
        assertEquals(1L,rows.single().revision)
    }
    @Test fun staleEditorAndDeletedIdCannotOverwriteAnotherSupplierGeneration() {
        var rows=SupplierRules.apply(emptyList(),save(),mapOf("p" to "simple"))
        rows=SupplierRules.apply(rows,save(rev=1,name="Новый"),mapOf("p" to "simple"))
        reject {SupplierRules.apply(rows,save(rev=1),mapOf("p" to "simple"))}
        rows=SupplierRules.apply(rows,SupplierCommand("del","owner",SupplierAction.Delete("s",2)),emptyMap())
        assertTrue(rows.single().deleted);assertTrue(rows.single().productIds.isEmpty())
        reject {SupplierRules.apply(rows,save(),mapOf("p" to "simple"))}
        reject {SupplierRules.apply(rows,save(rev=1),mapOf("p" to "simple"))}
    }
    @Test fun replayFingerprintUsesCanonicalSelectionAndActorButNamesNeedNotBeUnique() {
        val a=save(ids=setOf("p","q"));val b=save(ids=setOf("q","p"))
        assertEquals(a.fingerprint(),b.fingerprint())
        assertNotEquals(a.fingerprint(),a.copy(actorId="another").fingerprint())
        assertNotEquals(a.fingerprint(),save(ids=setOf("p, q")).fingerprint())
        val rows=SupplierRules.apply(emptyList(),save(ids=emptySet()),emptyMap())
        assertEquals(2,SupplierRules.apply(rows,save(id="another",ids=emptySet()),emptyMap()).size)
    }
}
