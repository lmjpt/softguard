package kr.woorijip.softguard.core.history

/**
 * 비정상 종료 추론 (설계문서 §8.7).
 * 강제 종료·크래시·전원 차단 때는 GUARD_STOPPED 가 남지 않는다. 서비스가 다시 시작될 때
 * 마지막 기록이 정상 종료가 아니면, 마지막으로 살아 있었다고 알려진 시각에 추정 종료를 끼워 넣는다.
 */
object GapInference {
    /**
     * @param lastEvent 부팅(DEVICE_BOOT)을 제외한 마지막 이력. 없으면 첫 실행.
     * @param lastAliveAt 마지막으로 살아 있음을 기록한 시각 (flush·화면 켬/꺼짐·이벤트 수신 때 갱신). 없으면 null.
     * @return 끼워 넣을 GUARD_STOPPED(inferred) 의 시각. 필요 없으면 null.
     */
    fun inferredStopAt(lastEvent: LogEvent?, lastAliveAt: Long?, now: Long): Long? {
        if (lastEvent == null) return null
        if (lastEvent.type == LogType.GUARD_STOPPED) return null
        val candidate = maxOf(lastAliveAt ?: 0L, lastEvent.occurredAt)
        return candidate.takeIf { it > 0L && it <= now }
    }
}
