package kr.woorijip.softguard.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 6자리 숫자 PIN (설계문서 §6).
 *   - 평문 저장 금지: PBKDF2-HMAC-SHA256 10만회 + 16바이트 랜덤 salt → 해시만 저장
 *   - 상수 시간 비교
 *   - 실패 백오프: 5회 → 30초, 10회부터 → 5분
 *   - 분실 시 복구 불가 (앱 삭제 후 재설치)
 *
 * 저장하는 값이 이미 salt 를 섞은 해시라 일반 SharedPreferences 로 충분하다.
 * (EncryptedSharedPreferences 는 폐기된 라이브러리이므로 쓰지 않는다.)
 */
class PinStore(context: Context) {
    private val sp = context.getSharedPreferences("pin", Context.MODE_PRIVATE)

    data class State(val isSet: Boolean, val failCount: Int, val lockedUntil: Long)

    sealed interface VerifyResult {
        data object Ok : VerifyResult
        data class Wrong(val failCount: Int, val lockedForMillis: Long) : VerifyResult
        data class Locked(val remainingMillis: Long) : VerifyResult
    }

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<State> = _state

    val isSet: Boolean get() = sp.contains(KEY_HASH)

    private fun readState() = State(
        isSet = sp.contains(KEY_HASH),
        failCount = sp.getInt(KEY_FAILS, 0),
        lockedUntil = sp.getLong(KEY_LOCKED_UNTIL, 0L),
    )

    fun set(pin: String) {
        require(isValidFormat(pin)) { "PIN 은 숫자 6자리여야 합니다" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, ITERATIONS)
        sp.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_ITER, ITERATIONS)
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .apply()
        _state.value = readState()
    }

    fun verify(pin: String, now: Long = System.currentTimeMillis()): VerifyResult {
        val lockedUntil = sp.getLong(KEY_LOCKED_UNTIL, 0L)
        if (now < lockedUntil) return VerifyResult.Locked(lockedUntil - now)

        val salt = Base64.decode(sp.getString(KEY_SALT, "") ?: "", Base64.NO_WRAP)
        val stored = Base64.decode(sp.getString(KEY_HASH, "") ?: "", Base64.NO_WRAP)
        val iter = sp.getInt(KEY_ITER, ITERATIONS)
        val ok = isValidFormat(pin) && stored.isNotEmpty() && MessageDigest.isEqual(derive(pin, salt, iter), stored)

        if (ok) {
            sp.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
            _state.value = readState()
            return VerifyResult.Ok
        }
        val fails = sp.getInt(KEY_FAILS, 0) + 1
        val lock = when {
            fails >= 10 -> 5 * 60_000L
            fails >= 5 -> 30_000L
            else -> 0L
        }
        sp.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCKED_UNTIL, if (lock > 0) now + lock else 0L).apply()
        _state.value = readState()
        return VerifyResult.Wrong(fails, lock)
    }

    fun clear() {
        sp.edit().clear().apply()
        _state.value = readState()
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 6
        private const val ITERATIONS = 100_000
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_ITER = "iter"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCKED_UNTIL = "lockedUntil"

        fun isValidFormat(pin: String) = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
    }
}
