package com.mendelev.mpos

import com.mendelev.mpos.data.local.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReceiptPagingTest {
    private fun native(i: Int)=ReceiptEntity("n-$i","c-$i","session-$i","shift",i.toLong(),1000L-i,100,100,0,0,"")
    private fun web(i: Int): WebReceiptRecord {
        val j=JSONObject().put("id","web:$i").put("sourceId",i.toString()).put("createdAt",1000L-i).put("total",100)
            .put("rawJson",JSONObject().put("id",i).put("total",1).toString())
        return WebReceiptRecord("key-$i",j.toString())
    }
    @Test fun twentyRowsAcrossTwoSourcesHaveNoGapsDuplicatesOrExtraReads() {
        val n=List(121) {native(it*2)}.sortedWith(compareByDescending<ReceiptEntity> {it.createdAt}.thenBy {it.id})
        val w=List(137) {web(it*2+1)}.sortedWith(compareByDescending<WebReceiptRecord> {it.time}.thenBy {it.id})
        val expected=(n.map {it.createdAt to it.id}+w.map {it.time to it.id}).sortedWith(compareByDescending<Pair<Long,String>> {it.first}.thenBy {it.second}).map {it.second}
        var no=0;var wo=0;val actual=mutableListOf<String>();var iterations=0
        do {
            val batchN=n.drop(no).take(21);val batchW=w.drop(wo).take(21)
            val page=ReceiptHistoryPaging.combine(batchN,batchW,no,wo)
            assertTrue(page.native.size+page.web.size<=20)
            actual+=(page.native.map {it.createdAt to it.id}+page.web.map {it.time to it.id}).sortedWith(compareByDescending<Pair<Long,String>> {it.first}.thenBy {it.second}).map {it.second}
            no=page.nativeOffset;wo=page.webOffset;iterations++
        } while(page.more)
        assertEquals(13,iterations);assertEquals(expected,actual);assertEquals(258,actual.toSet().size)
    }
    @Test fun exactlyTwentyAndEmptyHistoryDoNotOfferPhantomNextPage() {
        val page=ReceiptHistoryPaging.combine(List(20) {native(it)},emptyList(),0,0)
        assertFalse(page.more);assertEquals(20,page.nativeOffset)
        assertFalse(ReceiptHistoryPaging.combine(emptyList(),emptyList(),0,0).more)
    }
}
