package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.data.local.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class WebReceiptIndexTest {
    private fun source()=WebBackupReader.read("""{"version":1,"products":[],"employees":[],"shifts":[],"orders":[
      {"id":"old","timestamp":10,"total":10.5,"method":"cash","receiptDisplayNumber":"#7","orderType":"Доставка","items":[{"name":"Товар тогда","qty":1.5,"price":7}],"returnedAt":30,"returnAmount":10.5},
      {"id":"new","timestamp":20,"total":20,"method":"card","items":[]}
    ]}""".byteInputStream())
    @Test fun indexPreservesCompleteFrozenReceiptAndReturnWithStableChronologicalKeys() {
        val docs=WebReceiptIndex.documents(source()).sortedBy {it.id};assertEquals(2,docs.size)
        docs.forEach(WebReceiptIndex::validate)
        val newer=WebReceiptRecord(docs[0].id,docs[0].payload);val old=WebReceiptRecord(docs[1].id,docs[1].payload)
        assertEquals("new",newer.data.getString("sourceId"))
        assertEquals(1050L,old.summary().total);assertTrue(old.returned)
        assertEquals("Товар тогда",old.raw.getJSONArray("items").getJSONObject(0).getString("name"))
        assertEquals("Чек #7",old.label);assertEquals("delivery",old.type());assertEquals("Наличные",old.paymentLabel())
        assertFalse(old.toString().contains("Товар"))
        fun numbers(v: BackupJson) {when(v) {
            is BackupJson.Num->assertNotNull(v.raw.toLongOrNull())
            is BackupJson.Obj->v.fields.values.forEach(::numbers)
            is BackupJson.Arr->v.values.forEach(::numbers)
            else->Unit
        }}
        docs.forEach {numbers(BackupJsonParser(it.payload).parse())}
    }
    @Test fun rewrittenChronologicalKeyIsRejected() {
        val d=WebReceiptIndex.documents(source()).first()
        assertThrows(Exception::class.java) {WebReceiptIndex.validate(d.copy(id=d.id+"x"))}
    }
}
