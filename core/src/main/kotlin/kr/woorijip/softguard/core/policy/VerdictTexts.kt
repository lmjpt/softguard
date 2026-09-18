package kr.woorijip.softguard.core.policy

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 차단 안내 화면과 대시보드에 보여 줄 문구. 아이도 읽으므로 짧고 쉬운 말로. */
object VerdictTexts {
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    data class Texts(val title: String, val body: String)

    fun forBlocked(verdict: Verdict.Blocked, now: LocalDateTime): Texts = when (verdict.reason) {
        BlockReason.ALWAYS_BLOCKED -> Texts(
            title = "이 앱은 사용할 수 없어요",
            body = "정해진 사용 시간이 없어요",
        )
        BlockReason.OUT_OF_HOURS -> Texts(
            title = "지금은 사용 시간이 아니에요",
            body = verdict.nextAvailableAt?.let { "${dayWord(now, it)} ${it.format(HHMM)}부터 사용할 수 있어요" } ?: "",
        )
    }

    /** "오늘" / "내일" / "금요일" */
    fun dayWord(now: LocalDateTime, at: LocalDateTime): String {
        val today = now.toLocalDate()
        val day = at.toLocalDate()
        return when {
            day == today -> "오늘"
            day == today.plusDays(1) -> "내일"
            else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        }
    }

    fun hhmm(t: LocalDateTime): String = t.format(HHMM)
}
