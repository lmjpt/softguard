package kr.woorijip.softguard.core.policy

import kr.woorijip.softguard.core.model.Policy
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 판정 규칙 (설계문서 §7.3):
 *   1. 자기 자신               → Allowed(until = null)
 *   2. 차단 목록에 없음         → Allowed(until = null)
 *   3. 허용 시간대가 비어 있음   → Blocked(ALWAYS_BLOCKED)
 *   4. 지금이 허용 시간대 안     → Allowed(until = 구간 종료)
 *   5. 그 외                   → Blocked(OUT_OF_HOURS, next = 다음 구간 시작)
 *
 * 정책이 바뀌면 새로 만든다. 시간대는 생성 시 한 번만 정규화한다.
 */
class PolicyEngine(val policy: Policy, private val selfPackage: String) {

    val schedule: WeeklySchedule = WeeklySchedule.of(policy.allowWindows)

    fun evaluate(packageName: String, now: LocalDateTime): Verdict {
        if (packageName == selfPackage) return Verdict.Allowed(null)
        if (packageName !in policy.blockedPackages) return Verdict.Allowed(null)
        return evaluateBlockedApp(now)
    }

    /** 차단 목록에 있는 앱이라면 지금 어떤 판정을 받는가. 대시보드의 "지금 사용 가능?" 표시에도 쓴다. */
    fun evaluateBlockedApp(now: LocalDateTime): Verdict {
        if (schedule.isEmpty) return Verdict.Blocked(BlockReason.ALWAYS_BLOCKED, null)
        val base = now.truncatedTo(ChronoUnit.MINUTES)
        val m = WeeklySchedule.minuteOfWeek(base)
        if (schedule.contains(m)) {
            val end = schedule.endOfCurrent(m)
            return Verdict.Allowed(end?.let { base.plusMinutes((it - m).toLong()) })
        }
        val next = schedule.nextStartAfter(m)
            ?: return Verdict.Blocked(BlockReason.ALWAYS_BLOCKED, null)
        return Verdict.Blocked(BlockReason.OUT_OF_HOURS, base.plusMinutes((next - m).toLong()))
    }
}
