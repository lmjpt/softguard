package kr.woorijip.softguard.core.model

import java.time.DayOfWeek

/**
 * 차단 목록의 앱을 열어 주는 공통 허용 시간대 하나 (요일 + 시작~종료).
 * 종료가 시작보다 이르면 자정을 넘기는 구간이다 (22:00~01:00).
 */
data class AllowWindow(
    val days: Set<DayOfWeek>,
    val from: TimeOfDay,
    val to: TimeOfDay,
) {
    /** 자정을 넘기는가. to = 24:00 은 넘기는 것이 아니라 자정에 끝나는 것이다. */
    val crossesMidnight: Boolean get() = to < from

    /** 문제가 없으면 null, 있으면 사람이 읽을 문구. */
    fun validate(): String? = when {
        days.isEmpty() -> "요일을 하나 이상 골라 주세요"
        from == TimeOfDay.END_OF_DAY -> "시작 시각은 24:00 이 될 수 없어요"
        from == to -> "시작과 종료 시각이 같아요"
        else -> null
    }

    val isValid: Boolean get() = validate() == null

    /** 구간 길이(분). 자정을 넘기면 다음 날 종료까지. */
    fun durationMinutes(): Int =
        if (to > from) to.minutes - from.minutes
        else to.minutes + TimeOfDay.MINUTES_PER_DAY - from.minutes
}
