package kr.woorijip.softguard.core.policy

import kr.woorijip.softguard.core.model.AllowWindow
import kr.woorijip.softguard.core.model.TimeOfDay
import java.time.LocalDateTime

/**
 * 허용 시간대 목록을 "한 주의 분(minute-of-week)" 위의 반열린 구간 목록으로 정규화한 것.
 *   - 월요일 00:00 = 0, 일요일 24:00 = WEEK(10080)
 *   - 자정을 넘기는 구간은 둘로 나누고, 겹치는 구간은 합친다
 *   - 일요일 밤 → 월요일 새벽으로 이어지는 구간은 주 경계에서 나뉘어 저장되며,
 *     endOfCurrent() 가 이를 다시 이어 붙인다
 */
class WeeklySchedule private constructor(val intervals: List<Interval>) {

    /** [start, end) 분 단위. 0 <= start < end <= WEEK */
    data class Interval(val start: Int, val end: Int)

    val isEmpty: Boolean get() = intervals.isEmpty()

    val coversWholeWeek: Boolean
        get() = intervals.size == 1 && intervals[0].start == 0 && intervals[0].end == WEEK

    private fun find(minuteOfWeek: Int): Interval? =
        intervals.firstOrNull { minuteOfWeek >= it.start && minuteOfWeek < it.end }

    fun contains(minuteOfWeek: Int): Boolean = find(minuteOfWeek) != null

    /**
     * minuteOfWeek 를 포함하는 구간의 종료 시각(분). 주를 넘기면 WEEK 이상의 값.
     * 포함하는 구간이 없거나, 한 주 전체가 허용이라(끝이 없음) null.
     */
    fun endOfCurrent(minuteOfWeek: Int): Int? {
        if (coversWholeWeek) return null
        val current = find(minuteOfWeek) ?: return null
        if (current.end < WEEK) return current.end
        // 일요일 24:00 까지 이어지는 구간: 월요일 00:00 에서 시작하는 구간이 있으면 그 끝까지 잇는다
        val first = intervals.first()
        return if (first.start == 0) WEEK + first.end else WEEK
    }

    /** minuteOfWeek 이후 처음 시작하는 구간의 시작 시각(분). 다음 주로 넘어가면 WEEK 이상. 비어 있으면 null. */
    fun nextStartAfter(minuteOfWeek: Int): Int? {
        if (intervals.isEmpty()) return null
        val next = intervals.firstOrNull { it.start > minuteOfWeek }
        return next?.start ?: (WEEK + intervals.first().start)
    }

    companion object {
        const val MINUTES_PER_DAY = TimeOfDay.MINUTES_PER_DAY
        const val WEEK = 7 * MINUTES_PER_DAY

        fun minuteOfWeek(t: LocalDateTime): Int =
            (t.dayOfWeek.value - 1) * MINUTES_PER_DAY + t.hour * 60 + t.minute

        val EMPTY: WeeklySchedule = WeeklySchedule(emptyList())

        fun of(windows: Collection<AllowWindow>): WeeklySchedule {
            val raw = ArrayList<Interval>()
            for (w in windows) {
                if (!w.isValid) continue
                for (day in w.days) {
                    val base = (day.value - 1) * MINUTES_PER_DAY
                    val start = base + w.from.minutes
                    val end = base + if (w.to > w.from) w.to.minutes else w.to.minutes + MINUTES_PER_DAY
                    if (end <= WEEK) {
                        raw += Interval(start, end)
                    } else {
                        raw += Interval(start, WEEK)
                        raw += Interval(0, end - WEEK)
                    }
                }
            }
            if (raw.isEmpty()) return EMPTY
            val merged = ArrayList<Interval>()
            for (iv in raw.sortedBy { it.start }) {
                val last = merged.lastOrNull()
                if (last != null && iv.start <= last.end) {
                    if (iv.end > last.end) merged[merged.lastIndex] = Interval(last.start, iv.end)
                } else {
                    merged += iv
                }
            }
            return WeeklySchedule(merged)
        }
    }
}
