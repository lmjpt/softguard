package kr.woorijip.softguard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.woorijip.softguard.core.model.Policy
import kr.woorijip.softguard.core.policy.PolicyEngine
import kr.woorijip.softguard.core.storage.DecodeResult
import kr.woorijip.softguard.core.storage.PolicyCodec
import java.io.File
import java.io.IOException

/**
 * 정책의 유일한 저장소. filesDir/policy.json 한 파일 (PolicyCodec 포맷).
 *
 * 읽기에 실패하면 예외를 던지지 않고 "아무것도 차단하지 않는 정책"으로 동작한다 (설계문서 §7.2).
 * 전부 차단하는 쪽으로 넘어지면 설정 앱조차 못 열어 기기를 되살릴 수 없기 때문이다.
 * 실패 사실은 state.loadFailed 로 대시보드에 드러낸다.
 */
class PolicyRepository(
    context: Context,
    private val diag: DetectionLog,
    private val selfPackage: String = context.packageName,
) {
    data class State(val policy: Policy, val loadFailed: Boolean = false, val error: String? = null)

    private val file = File(context.filesDir, "policy.json")
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state

    val policy: Policy get() = _state.value.policy

    @Volatile
    var engine: PolicyEngine = PolicyEngine(_state.value.policy, selfPackage)
        private set

    private fun load(): State {
        if (!file.exists()) return State(Policy.EMPTY)
        val text = try {
            file.readText()
        } catch (e: IOException) {
            diag.log("정책 파일을 읽지 못함: ${e.message}")
            return State(Policy.EMPTY, loadFailed = true, error = e.message)
        }
        return when (val r = PolicyCodec.decode(text)) {
            is DecodeResult.Ok -> State(r.policy)
            is DecodeResult.Failed -> {
                diag.log("정책 해석 실패 → 아무것도 차단하지 않음: ${r.message}")
                State(Policy.EMPTY, loadFailed = true, error = r.message)
            }
        }
    }

    /** 정책을 바꾼다. 바뀐 게 없으면 저장하지 않는다. */
    fun update(transform: (Policy) -> Policy): Boolean = synchronized(lock) {
        val current = _state.value
        val next = transform(current.policy)
        if (next == current.policy && !current.loadFailed) return true
        save(next)
    }

    /** 가져오기 텍스트를 해석해 저장한다. */
    fun importText(text: String): DecodeResult = synchronized(lock) {
        val r = PolicyCodec.decode(text)
        if (r is DecodeResult.Ok) save(r.policy)
        r
    }

    fun exportText(): String = PolicyCodec.encode(policy)

    private fun save(next: Policy): Boolean {
        val text = PolicyCodec.encode(next)
        try {
            val tmp = File(file.parentFile, "policy.json.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (e: IOException) {
            diag.log("정책 저장 실패: ${e.message}")
            return false
        }
        engine = PolicyEngine(next, selfPackage)
        _state.value = State(next)
        diag.log("정책 저장: 차단 ${next.blockedPackages.size}개 · 시간대 ${next.allowWindows.size}개")
        return true
    }
}
