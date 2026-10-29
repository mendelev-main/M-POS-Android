package com.mendelev.mpos

import androidx.test.runner.AndroidJUnitRunner
import com.mendelev.mpos.core.access.*
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/** Privileged business fixtures only. Not part of the target APK. Access tests explicitly revoke it. */
class MposTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: android.os.Bundle?) {
        val args=arguments ?: android.os.Bundle()
        val listeners=listOfNotNull(args.getString("listener"),FixtureAdministrationListener::class.java.name)
        args.putString("listener",listeners.joinToString(","))
        super.onCreate(args)
    }
}
class FixtureAdministrationListener : RunListener() {
    override fun testStarted(description: Description) {
        val fixture=AdminSession(PasswordVerifier {it.concatToString()=="fixture-admin"})
        Administration.session=fixture
        runBlocking {check(fixture.login("fixture-admin".toCharArray()))}
    }
}
