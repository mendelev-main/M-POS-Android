package com.mendelev.mpos.core.access

import com.mendelev.mpos.domain.catalog.CatalogFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

fun interface PasswordVerifier { fun matches(password: CharArray): Boolean }

/** Salt and slow verifier are public APK material; this is not a device-specific secret. */
class EmbeddedPasswordVerifier(private val salt: String=SALT,private val expected: String=EXPECTED) : PasswordVerifier {
    override fun matches(password: CharArray): Boolean {
        if(password.isEmpty() || password.size>128) return false
        val spec=PBEKeySpec(password,hex(salt),600_000,256)
        return try {
            val actual=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { MessageDigest.isEqual(actual,hex(expected)) } finally { actual.fill(0) }
        } finally { spec.clearPassword() }
    }
    private fun hex(value: String)=value.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    private companion object {
        const val SALT="30e743393673f76e23e843be6302617e"
        const val EXPECTED="4b783bac918ab75c9a045f11de96a324bf8a5cf69768f3a159c303a4636a6466"
    }
}

data class AdminState(val active: Boolean=false,val generation: Long=0)

/** Process-memory only: never SavedState/Room/preferences. Background and recreation preserve it. */
class AdminSession(private val verifier: PasswordVerifier=EmbeddedPasswordVerifier(),
    private val monotonicMillis: ()->Long={System.nanoTime()/1_000_000}) {
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(AdminState())
    val state=mutable.asStateFlow()
    private var failures=0
    private var blockedUntil=0L
    suspend fun login(password: CharArray): Boolean {
        try {
            // Serialize verification as well: concurrent attempts cannot evade the attempt limit.
            return mutex.withLock {
                if(monotonicMillis()<blockedUntil) throw CatalogFailure("access","Слишком много попыток. Повторите вход через 30 секунд")
                val valid=withContext(Dispatchers.Default) {verifier.matches(password)}
                if(valid) {failures=0;blockedUntil=0;mutable.value=AdminState(true,mutable.value.generation+1)}
                else if(++failures>=5) {failures=0;blockedUntil=monotonicMillis()+30_000}
                valid
            }
        } finally {password.fill('\u0000')}
    }
    suspend fun logout()=mutex.withLock {mutable.value=AdminState(false,mutable.value.generation+1)}
    /** A command admitted before logout finishes before logout; logout winning the lock refuses it. */
    suspend fun <T> authorized(expected: AdminAuthorization?=null,block: suspend ()->T): T=mutex.withLock {
        if(expected!=null && (expected.session!==this || expected.generation!=mutable.value.generation)) throw CatalogFailure("access","Сессия этой формы завершена. Откройте форму заново")
        if(!mutable.value.active) throw CatalogFailure("access","Откройте сессию администрирования в Настройках")
        block()
    }
}

object Administration {
    // Replaced only by the instrumentation test adapter; default is locked in every APK variant.
    internal var session=AdminSession()
}
