package kr.woorijip.softguard.core.policy

import kr.woorijip.softguard.core.model.AllowWindow
import kr.woorijip.softguard.core.model.Policy
import kr.woorijip.softguard.core.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class PolicyEngineTest {
    private val self = "kr.woorijip.softguard"
    private val youtube = "com.google.android.youtube"
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    // 2026-09-14 = 월요일
    private fun mon(h: Int, m: Int = 0, s: Int = 0) = LocalDateTime.of(2026, 9, 14, h, m, s)
    private fun fri(h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, 18, h, m)
    private fun sat(h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, 19, h, m)

    private val policy = Policy(
        blockedPackages = setOf(youtube),
        allowWindows = listOf(
            AllowWindow(weekdays, TimeOfDay.of(19, 0), TimeOfDay.of(20, 0)),
            AllowWindow(weekend, TimeOfDay.of(10, 0), TimeOfDay.of(12, 0)),
        ),
    )
    private val engine = PolicyEngine(policy, self)

    @Test fun step1_selfAlwaysAllowed() {
        assertEquals(Verdict.Allowed(null), PolicyEngine(Policy(setOf(self)), self).evaluate(self, mon(3)))
    }

    @Test fun step2_unlistedAppAllowed() {
        assertEquals(Verdict.Allowed(null), engine.evaluate("com.kakao.talk", mon(3)))
    }

    @Test fun step3_noWindowsMeansAlwaysBlocked() {
        val e = PolicyEngine(Policy(setOf(youtube)), self)
        assertEquals(Verdict.Blocked(BlockReason.ALWAYS_BLOCKED, null), e.evaluate(youtube, mon(19, 30)))
    }

    @Test fun step4_insideWindowAllowedUntilEnd() {
        assertEquals(Verdict.Allowed(mon(20)), engine.evaluate(youtube, mon(19, 0)))
        assertEquals(Verdict.Allowed(mon(20)), engine.evaluate(youtube, mon(19, 59)))
    }

    @Test fun step5_outsideWindowBlockedWithNextStart() {
        assertEquals(Verdict.Blocked(BlockReason.OUT_OF_HOURS, mon(19)), engine.evaluate(youtube, mon(8)))
        // 월요일 20:00 정각 → 이미 끝남 → 다음은 화요일 19:00
        assertEquals(
            Verdict.Blocked(BlockReason.OUT_OF_HOURS, LocalDateTime.of(2026, 9, 15, 19, 0)),
            engine.evaluate(youtube, mon(20)),
        )
    }

    @Test fun secondsAreTruncated() {
        assertEquals(Verdict.Allowed(mon(20)), engine.evaluate(youtube, mon(19, 59, 59)))
        assertEquals(Verdict.Blocked(BlockReason.OUT_OF_HOURS, mon(19)), engine.evaluate(youtube, mon(18, 59, 59)))
    }

    @Test fun crossesToWeekend() {
        // 금요일 20:00 이후 → 토요일 10:00
        assertEquals(Verdict.Blocked(BlockReason.OUT_OF_HOURS, sat(10)), engine.evaluate(youtube, fri(21)))
        assertEquals(Verdict.Allowed(sat(12)), engine.evaluate(youtube, sat(11, 30)))
    }

    @Test fun sundayNightWrapsToNextMonday() {
        val e = PolicyEngine(
            Policy(setOf(youtube), listOf(AllowWindow(setOf(DayOfWeek.SUNDAY), TimeOfDay.of(22, 0), TimeOfDay.of(1, 0)))),
            self,
        )
        val sunday = LocalDateTime.of(2026, 9, 20, 23, 0)
        assertEquals(Verdict.Allowed(LocalDateTime.of(2026, 9, 21, 1, 0)), e.evaluate(youtube, sunday))
        // 월요일 02:00 → 다음 일요일 22:00
        assertEquals(
            Verdict.Blocked(BlockReason.OUT_OF_HOURS, LocalDateTime.of(2026, 9, 27, 22, 0)),
            e.evaluate(youtube, LocalDateTime.of(2026, 9, 21, 2, 0)),
        )
    }

    @Test fun blockedAppStatusMirrorsEvaluate() {
        assertEquals(engine.evaluate(youtube, mon(19, 30)), engine.evaluateBlockedApp(mon(19, 30)))
    }

    @Test fun verdictTexts() {
        val blocked = engine.evaluate(youtube, mon(8)) as Verdict.Blocked
        val t = VerdictTexts.forBlocked(blocked, mon(8))
        assertEquals("지금은 사용 시간이 아니에요", t.title)
        assertEquals("오늘 19:00부터 사용할 수 있어요", t.body)
        val friNight = engine.evaluate(youtube, fri(21)) as Verdict.Blocked
        assertEquals("내일 10:00부터 사용할 수 있어요", VerdictTexts.forBlocked(friNight, fri(21)).body)
    }
}
