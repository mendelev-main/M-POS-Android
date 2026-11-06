package com.mendelev.mpos.core.access

import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdminSessionTest {
    private fun session(clock: ()->Long={0})=AdminSession(PasswordVerifier {it.concatToString()=="fixture"},clock)
    @Test fun lockedWrongCorrectLogoutAndNewRuntime()=runBlocking {
        val s=session();var writes=0
        try {s.authorized {writes++};fail()} catch(_: CatalogFailure) {}
        val bad="wrong".toCharArray();assertFalse(s.login(bad));assertTrue(bad.all {it=='\u0000'});assertFalse(s.state.value.active)
        val good="fixture".toCharArray();assertTrue(s.login(good));assertTrue(good.all {it=='\u0000'})
        s.authorized {writes++};assertEquals(1,writes)
        s.logout();try {s.authorized {writes++};fail()} catch(_: CatalogFailure) {}
        assertEquals(1,writes);assertFalse(session().state.value.active)
    }
    @Test fun throttlesConcurrentAttemptsAndExpiresMonotonically()=runBlocking {
        var now=0L;val s=session {now}
        repeat(5) {assertFalse(s.login("bad".toCharArray()))}
        val p="fixture".toCharArray()
        try {s.login(p);fail()} catch(_: CatalogFailure) {}
        assertTrue(p.all {it=='\u0000'});assertFalse(s.state.value.active)
        now=30_000;assertTrue(s.login("fixture".toCharArray()))
    }
    @Test fun logoutWaitsForAdmittedCommitThenRefusesQueuedCommand()=runBlocking {
        val s=session();s.login("fixture".toCharArray())
        val admitted=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val order=mutableListOf<String>()
        val write=launch {s.authorized {admitted.complete(Unit);release.await();order.add("commit")}}
        admitted.await();val logout=launch(start=CoroutineStart.UNDISPATCHED) {s.logout();order.add("logout")}
        assertFalse(logout.isCompleted);release.complete(Unit);write.join();logout.join()
        try {s.authorized {order.add("invalid")};fail()} catch(_: CatalogFailure) {}
        assertEquals(listOf("commit","logout"),order)
    }
    @Test fun exceptionReleasesAuthorizationWithoutChangingSession()=runBlocking {
        val s=session();s.login("fixture".toCharArray())
        try {s.authorized {throw IllegalStateException("disk")}} catch(_: IllegalStateException) {}
        s.logout();assertFalse(s.state.value.active)
        assertFalse(EmbeddedPasswordVerifier().matches("wrong-fixture-password".toCharArray()))
    }
    @Test fun slowVerifierAcceptsOnlyExactFixtureIncludingCaseAndWhitespace() {
        val verifier=EmbeddedPasswordVerifier("00000000000000000000000000000000","c4450018bc3b5008a6e051d0f52b0581c535c8dfc614b8d80ee02abf5eacb7a6")
        assertTrue(verifier.matches("fixture".toCharArray()))
        assertFalse(verifier.matches("Fixture".toCharArray()))
        assertFalse(verifier.matches("fixture ".toCharArray()))
        assertFalse(verifier.matches(CharArray(129) {'a'}))
    }

}
