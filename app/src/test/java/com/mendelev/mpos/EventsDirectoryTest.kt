package com.mendelev.mpos

import com.mendelev.mpos.backend.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsDirectoryTest {
    private fun rows(start: Int,count: Int)=List(count) {JSONObject().put("id","order-${start+it}").put("status","accepted")}
    @Test fun historyLoadsOnlyFiveAtATimeAndDoubleTapDoesNotRequestTwice()=runTest {
        val calls=mutableListOf<String?>()
        val model=EventsDirectory(this,{error("history must not refresh current")}) {cursor->
            calls+=cursor
            when(cursor) {null->OrderHistoryPage(rows(1,5),"page1");"page1"->OrderHistoryPage(rows(6,5),"page2");else->OrderHistoryPage(rows(11,3),null)}
        }
        model.history();runCurrent();assertEquals(5,model.state.value.rows.size)
        model.more();model.more();runCurrent();assertEquals(10,model.state.value.rows.size)
        model.more();runCurrent();assertEquals(13,model.state.value.rows.size);assertNull(model.state.value.cursor)
        model.more();runCurrent();assertEquals(listOf(null,"page1","page2"),calls)
    }
    @Test fun failedNextPageKeepsRowsAndCursorThenRefreshReplacesThePage()=runTest {
        var fail=false;var ready=false
        val model=EventsDirectory(this,{}) {cursor->
            if(fail) error("offline")
            OrderHistoryPage(rows(if(cursor==null) 1 else 6,5).onEach {if(ready) it.put("status","ready")},"next")
        }
        model.history();runCurrent();val before=model.state.value
        fail=true;model.more();runCurrent()
        assertEquals(before.rows,model.state.value.rows);assertEquals(before.cursor,model.state.value.cursor);assertNotNull(model.state.value.error)
        fail=false;ready=true;model.refresh();runCurrent()
        assertEquals(5,model.state.value.rows.size);assertTrue(model.state.value.rows.all {it.getString("status")=="ready"});assertNull(model.state.value.error)
    }
    @Test fun sourceResetPreventsLateOldResponseFromExposingAnotherOrganizationsHistory()=runTest {
        val gate=CompletableDeferred<Unit>();var calls=0
        val model=EventsDirectory(this,{}) {
            if(++calls==1) {withContext(NonCancellable) {gate.await()};OrderHistoryPage(rows(100,5),"old")}
            else OrderHistoryPage(rows(1,5),null)
        }
        model.history();runCurrent();model.reset();model.history();runCurrent()
        gate.complete(Unit);runCurrent()
        assertEquals("order-1",model.state.value.rows.first().getString("id"));assertNull(model.state.value.cursor)
    }
    @Test fun currentRefreshFailureDoesNotPretendSuccessAndCanBeRetried()=runTest {
        var calls=0
        val model=EventsDirectory(this,{if(++calls==1) error("offline")},{error("unexpected history")})
        model.refresh();runCurrent();assertNotNull(model.state.value.error);assertFalse(model.state.value.loading)
        model.refresh();runCurrent();assertEquals(2,calls);assertNull(model.state.value.error)
    }
}
