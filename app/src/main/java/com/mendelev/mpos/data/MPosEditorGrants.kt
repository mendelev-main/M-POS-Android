package com.mendelev.mpos.data

import org.json.JSONObject
import java.util.UUID

/** Activity/runtime scoped grants. Never stored in backup, Room or diagnostics. Accessed on storage FIFO only. */
class MPosEditorGrants {
    private data class Grant(val operation: String, val id: Any?, val document: String?)
    private val grants = linkedMapOf<String, Grant>()
    fun issue(operation: String, id: Any?, expected: Any): String {
        val token = UUID.randomUUID().toString()
        // Replace earlier authorization for this editor/field; repeated requests cannot grow without bound.
        grants.entries.removeAll { it.value.operation == operation && MPosSupplyParity.same(it.value.id, id) }
        grants[token] = Grant(operation, id, if (expected === JSONObject.NULL) null else expected.toString())
        while (grants.size > 32) grants.remove(grants.keys.first())
        return token
    }
    fun allows(token: String, operation: String, id: Any?, expected: Any): Boolean {
        val grant = grants[token] ?: return false
        val previous = grant.document?.let(::JSONObject) ?: JSONObject.NULL
        return grant.operation == operation && MPosSupplyParity.same(grant.id, id) && MPosSupplyParity.same(previous, expected)
    }
    fun consume(tokens: Collection<String>) { tokens.forEach(grants::remove) }
}
