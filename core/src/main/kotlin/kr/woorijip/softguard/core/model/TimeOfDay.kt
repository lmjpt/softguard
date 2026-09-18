package kr.woorijip.softguard.core.model

import java.util.Locale

/**
 * 하루 안의 시각. 0 = 00:00, 1440 = 24:00.
 * 24:00 은 "자정까지" 라는 종료 시각으로만 쓴다 (설계문서 §7.3).
 */
@JvmInline
value class TimeOfDay(val minutes: Int) : Comparable<TimeOfDay> {
    init {
        require(minutes in 0..MINUTES_PER_DAY) { "시각은 0..1440 분 사이여야 합니다: $minutes" }
    }

    val hour: Int get() = minutes / 60
    val minute: Int get() = minutes % 60

    override fun compareTo(other: TimeOfDay): Int = minutes.compareTo(other.minutes)

    override fun toString(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        val MIDNIGHT = TimeOfDay(0)
        val END_OF_DAY = TimeOfDay(MINUTES_PER_DAY)

        fun of(hour: Int, minute: Int): TimeOfDay = TimeOfDay(hour * 60 + minute)

        private val PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")

        /** "HH:mm" 을 읽는다. "24:00" 만 예외로 허용. 형식이 틀리면 null. */
        fun parse(text: String): TimeOfDay? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (min !in 0..59) return null
            if (h == 24) return if (min == 0) END_OF_DAY else null
            if (h !in 0..23) return null
            return of(h, min)
        }
    }
}
