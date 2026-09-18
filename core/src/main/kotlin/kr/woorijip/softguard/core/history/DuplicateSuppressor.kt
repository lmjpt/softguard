package kr.woorijip.softguard.core.history

/**
 * 이력 중복 억제 (설계문서 §8.3). 같은 패키지를 마지막으로 **기록한** 뒤 windowMillis 안에
 * 다시 보면 기록하지 않는다. 기준이 "마지막 기록 시각" 이므로 같은 앱을 계속 두드리면
 * windowMillis 마다 한 번씩은 기록된다.
 *
 * 집행(판정·오버레이)에는 쓰지 않는다. 기록만 억제한다.
 */
class DuplicateSuppressor(
    private val windowMillis: Long = 10_000L,
    private val maxEntries: Int = 64,
) {
    private val lastRecorded = LinkedHashMap<String, Long>()

    /** 기록해야 하면 true 를 돌려주고 시각을 갱신한다. */
    @Synchronized
    fun shouldRecord(packageName: String, nowMillis: Long): Boolean {
        val last = lastRecorded[packageName]
        if (last != null && nowMillis - last in 0 until windowMillis) return false
        lastRecorded.remove(packageName)
        lastRecorded[packageName] = nowMillis // 다시 넣어 삽입 순서를 최신으로
        while (lastRecorded.size > maxEntries) {
            lastRecorded.remove(lastRecorded.keys.first())
        }
        return true
    }

    @Synchronized
    fun clear() = lastRecorded.clear()

    val size: Int
        @Synchronized get() = lastRecorded.size
}
