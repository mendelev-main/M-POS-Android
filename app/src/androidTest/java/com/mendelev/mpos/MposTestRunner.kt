package com.mendelev.mpos

import androidx.test.runner.AndroidJUnitRunner
import com.mendelev.mpos.core.access.*
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/** Privileged business fixtures only. Not part of the target APK. Access tests explicitly revoke it. */
class MposTestRunner : AndroidJUnitRunner() {
    private var nativeRestoreMode: String?=null
    override fun onCreate(arguments: android.os.Bundle?) {
        val args=arguments ?: android.os.Bundle()
        nativeRestoreMode=args.getString("nativeRestoreMode")
        val listeners=listOfNotNull(args.getString("listener"),FixtureAdministrationListener::class.java.name)
        args.putString("listener",listeners.joinToString(","))
        super.onCreate(args)
    }
    override fun onStart() {
        val mode=nativeRestoreMode
        if(mode==null) {super.onStart();return}
        try {
            require(mode in setOf("seed","verify"))
            val result=runBlocking {NativeRestoreProcessFixture.run(mode)}
            finish(android.app.Activity.RESULT_OK,android.os.Bundle().apply {putString("stream",result)})
        } catch(e:Throwable) {
            finish(android.app.Activity.RESULT_CANCELED,android.os.Bundle().apply {putString("stream","NATIVE RESTORE PROCESS FAILED: "+e.stackTraceToString())})
        }
    }
}
class FixtureAdministrationListener : RunListener() {
    override fun testStarted(description: Description) {
        val fixture=AdminSession(PasswordVerifier {it.concatToString()=="fixture-admin"})
        Administration.session=fixture
        runBlocking {check(fixture.login("fixture-admin".toCharArray()))}
    }
}
