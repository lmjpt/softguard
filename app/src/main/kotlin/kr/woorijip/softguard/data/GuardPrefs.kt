package kr.woorijip.softguard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 작은 상태값들. 정책(파일)·이력(Room)·PIN(PinStore) 외의 것. */
class GuardPrefs(context: Context) {
    private val sp = context.getSharedPreferences("guard", Context.MODE_PRIVATE)

    /** 마지막으로 살아 있었다고 기록한 시각. 비정상 종료 추론의 기준점 (설계문서 §8.7). */
    var lastAliveAt: Long
        get() = sp.getLong(KEY_LAST_ALIVE, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_ALIVE, value).apply()

    private val _setupDone = MutableStateFlow(sp.getBoolean(KEY_SETUP_DONE, false))
    val setupDoneFlow: StateFlow<Boolean> = _setupDone

    var setupDone: Boolean
        get() = _setupDone.value
        set(value) {
            sp.edit().putBoolean(KEY_SETUP_DONE, value).apply()
            _setupDone.value = value
        }

    private companion object {
        const val KEY_LAST_ALIVE = "lastAliveAt"
        const val KEY_SETUP_DONE = "setupDone"
    }
}
