package com.mendelev.mpos.core.access

import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdminFormsTest {
    private fun session()=AdminSession(PasswordVerifier {it.concatToString()=="fixture"})
    private suspend fun rejects(block: suspend ()->Unit) {try {block();fail("Revoked authority accepted")} catch(_:CatalogFailure) {}}
    @Test fun rapidLogoutLoginCannotReviveFormEvenWhenCollectorSkipsLockedState()=runBlocking {
        val session=session();session.login("fixture".toCharArray())
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined);val forms=AdminForms(scope,session)
        try {
            var cleared=0;forms.watch {cleared++};val old=forms.permit()
            session.logout();session.login("fixture".toCharArray())
            assertFalse(forms.current());rejects {old.write {session.authorized(currentCoroutineContext()[AdminAuthorization]) {fail()}}}
            assertTrue(forms.reopen {cleared++});assertTrue(forms.current());assertTrue(cleared>0)
            val fresh=forms.permit();var writes=0
            fresh.write {session.authorized(currentCoroutineContext()[AdminAuthorization]) {writes++}}
            assertEquals(1,writes);rejects {session.authorized(old) {writes++}};assertEquals(1,writes)
        } finally {scope.cancel()}
    }
    @Test fun unresolvedCommandRetainsOriginalPermitWhileNewCommandRequiresExplicitReopen()=runBlocking {
        val session=session();session.login("fixture".toCharArray());val scope=CoroutineScope(SupervisorJob())
        try {
            val forms=AdminForms(scope,session);val old=forms.command("same")
            session.logout();session.login("fixture".toCharArray());forms.reopen {}
            assertSame(old,forms.command("same"));rejects {forms.command("same").write {fail()}}
            assertTrue(forms.command("new").current())
        } finally {scope.cancel()}
    }
    @Test fun permitCannotAuthorizeAnotherRuntimeEvenWithSameGeneration()=runBlocking {
        val one=session();val two=session();one.login("fixture".toCharArray());two.login("fixture".toCharArray())
        val scope=CoroutineScope(SupervisorJob())
        try {val p=AdminForms(scope,one).permit();rejects {two.authorized(p) {fail()}}}
        finally {scope.cancel()}
    }
}
