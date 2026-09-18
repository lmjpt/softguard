package kr.woorijip.softguard.core.history

/** 날짜별 이력 화면에 그릴 항목. 최신순으로 정렬되어 나온다. */
sealed interface TimelineItem {
    data class Launch(val event: LogEvent) : TimelineItem

    /**
     * 제어가 꺼져 있던 구간 (설계문서 §8.7).
     * @param inferred 종료 시각이 추정인가
     * @param rebooted 구간 안에 기기 부팅이 있었나 → "기기가 꺼져 있었습니다"
     * @param ongoing 아직 끝나지 않았나 (지금도 꺼져 있음)
     * @param startsBeforeDay 전날부터 이어진 공백인가
     * @param endsAfterDay 다음 날로 이어지는 공백인가
     */
    data class Gap(
        val from: Long,
        val to: Long,
        val inferred: Boolean,
        val rebooted: Boolean,
        val ongoing: Boolean,
        val startsBeforeDay: Boolean,
        val endsAfterDay: Boolean,
    ) : TimelineItem

    /** 직전 종료 기록 없이 나타난 부팅 (참고 표시) */
    data class Boot(val at: Long) : TimelineItem
}

object Timeline {
    /**
     * @param events 그 날의 이벤트, 시각 오름차순
     * @param dayStart 그 날 00:00 (epoch millis)
     * @param dayEnd 다음 날 00:00
     * @param openGap 그 날이 시작되기 전에 열려 있던 공백의 GUARD_STOPPED. 없으면 null
     * @param now 현재 시각 — 아직 닫히지 않은 공백의 끝을 정한다
     */
    fun build(
        events: List<LogEvent>,
        dayStart: Long,
        dayEnd: Long,
        openGap: LogEvent?,
        now: Long,
    ): List<TimelineItem> {
        val out = ArrayList<TimelineItem>(events.size + 2)
        var gapStart: LogEvent? = openGap
        var rebooted = false

        for (e in events) {
            when (e.type) {
                LogType.APP_LAUNCH -> out += TimelineItem.Launch(e)
                LogType.GUARD_STOPPED -> if (gapStart == null) {
                    gapStart = e
                    rebooted = false
                }
                LogType.DEVICE_BOOT -> if (gapStart != null) rebooted = true else out += TimelineItem.Boot(e.occurredAt)
                LogType.GUARD_STARTED -> {
                    val g = gapStart
                    if (g != null) {
                        out += TimelineItem.Gap(
                            from = maxOf(g.occurredAt, dayStart),
                            to = e.occurredAt,
                            inferred = g.inferred,
                            rebooted = rebooted,
                            ongoing = false,
                            startsBeforeDay = g.occurredAt < dayStart,
                            endsAfterDay = false,
                        )
                        gapStart = null
                        rebooted = false
                    }
                }
            }
        }

        val g = gapStart
        if (g != null) {
            val from = maxOf(g.occurredAt, dayStart)
            val to = minOf(dayEnd, now)
            if (to > from) {
                out += TimelineItem.Gap(
                    from = from,
                    to = to,
                    inferred = g.inferred,
                    rebooted = rebooted,
                    ongoing = now < dayEnd,
                    startsBeforeDay = g.occurredAt < dayStart,
                    endsAfterDay = now >= dayEnd,
                )
            }
        }
        return out.asReversed()
    }
}
