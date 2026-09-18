package kr.woorijip.softguard.core.policy

import kr.woorijip.softguard.core.model.AllowWindow
import kr.woorijip.softguard.core.model.TimeOfDay
import kr.woorijip.softguard.core.policy.WeeklySchedule.Companion.WEEK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime

class WeeklyScheduleTest {
    private fun t(h: Int, m: Int = 0) = TimeOfDay.of(h, m)
    private fun min(day: DayOfWeek, h: Int, m: Int = 0) = (day.value - 1) * 1440 + h * 60 + m

    @Test fun emptyWhenNoWindows() {
        val s = WeeklySchedule.of(emptyList())
        assertTrue(s.isEmpty)
        assertFalse(s.contains(0))
        assertNull(s.nextStartAfter(0))
        assertNull(s.endOfCurrent(0))
    }

    @Test fun halfOpenInterval() {
        val s = WeeklySchedule.of(listOf(AllowWindow(setOf(MONDAY), t(19), t(20))))
        assertFalse(s.contains(min(MONDAY, 18, 59)))
        assertTrue(s.contains(min(MONDAY, 19, 0)))
        assertTrue(s.contains(min(MONDAY, 19, 59)))
        assertFalse(s.contains(min(MONDAY, 20, 0)))
        assertEquals(min(MONDAY, 20), s.endOfCurrent(min(MONDAY, 19, 30)))
    }

    @Test fun appliesToEachSelectedDay() {
        val s = WeeklySchedule.of(listOf(AllowWindow(setOf(MONDAY, WEDNESDAY, FRIDAY), t(19), t(20))))
        assertEquals(3, s.intervals.size)
        assertTrue(s.contains(min(WEDNESDAY, 19, 30)))
        assertFalse(s.contains(min(TUESDAY, 19, 30)))
    }

    @Test fun midnightCrossingSplitsIntoTwo() {
        val s = WeeklySchedule.of(listOf(AllowWindow(setOf(TUESDAY), t(22), t(1))))
        assertTrue(s.contains(min(TUESDAY, 23, 30)))
        assertTrue(s.contains(min(WEDNESDAY, 0, 30)))
        assertFalse(s.contains(min(WEDNESDAY, 1, 0)))
        // 화요일 22:00 에 시작한 허용은 수요일 01:00 에 끝난다
        assertEquals(min(WEDNESDAY, 1), s.endOfCurrent(min(TUESDAY, 23)))
    }

    @Test fun overlappingWindowsMerge() {
        val s = WeeklySchedule.of(
            listOf(
                AllowWindow(setOf(SATURDAY), t(10), t(12)),
                AllowWindow(setOf(SATURDAY), t(11), t(13)),
                AllowWindow(setOf(SATURDAY), t(13), t(14)), // 맞닿음 → 합침
            )
        )
        assertEquals(listOf(WeeklySchedule.Interval(min(SATURDAY, 10), min(SATURDAY, 14))), s.intervals)
        assertEquals(min(SATURDAY, 14), s.endOfCurrent(min(SATURDAY, 10, 30)))
    }

    @Test fun endOfDayMeansMidnight() {
        val s = WeeklySchedule.of(listOf(AllowWindow(setOf(MONDAY), t(22), TimeOfDay.END_OF_DAY)))
        assertTrue(s.contains(min(MONDAY, 23, 59)))
        assertFalse(s.contains(min(TUESDAY, 0, 0)))
        assertEquals(min(TUESDAY, 0), s.endOfCurrent(min(MONDAY, 23)))
    }

    @Test fun sundayNightWrapsIntoMonday() {
        val s = WeeklySchedule.of(listOf(AllowWindow(setOf(SUNDAY), t(22), t(1))))
        assertEquals(2, s.intervals.size)
        assertTrue(s.contains(min(SUNDAY, 23)))
        assertTrue(s.contains(min(MONDAY, 0, 30)))
        // 일요일 23:00 의 허용은 다음 주 월요일 01:00 에 끝난다 → WEEK + 60
        assertEquals(WEEK + 60, s.endOfCurrent(min(SUNDAY, 23)))
        // 월요일 00:30 에서 보면 이번 주 월요일 01:00
        assertEquals(min(MONDAY, 1), s.endOfCurrent(min(MONDAY, 0, 30)))
    }

    @Test fun nextStartWrapsToNextWeek() {
        val s = WeeklySchedule.of(listOf(AllowWindow(setOf(MONDAY), t(19), t(20))))
        assertEquals(min(MONDAY, 19), s.nextStartAfter(min(MONDAY, 8)))
        assertEquals(WEEK + min(MONDAY, 19), s.nextStartAfter(min(MONDAY, 20)))
        assertEquals(WEEK + min(MONDAY, 19), s.nextStartAfter(min(SUNDAY, 23, 59)))
    }

    @Test fun nextStartPicksEarliestFollowing() {
        val s = WeeklySchedule.of(
            listOf(
                AllowWindow(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), t(19), t(20)),
                AllowWindow(setOf(SATURDAY, SUNDAY), t(10), t(12)),
            )
        )
        assertEquals(min(SATURDAY, 10), s.nextStartAfter(min(FRIDAY, 20)))
        assertEquals(min(TUESDAY, 19), s.nextStartAfter(min(MONDAY, 20)))
    }

    @Test fun wholeWeekHasNoEnd() {
        val s = WeeklySchedule.of(listOf(AllowWindow(DayOfWeek.entries.toSet(), TimeOfDay.MIDNIGHT, TimeOfDay.END_OF_DAY)))
        assertTrue(s.coversWholeWeek)
        assertTrue(s.contains(0))
        assertTrue(s.contains(WEEK - 1))
        assertNull(s.endOfCurrent(min(WEDNESDAY, 12)))
    }

    @Test fun invalidWindowsAreSkipped() {
        val s = WeeklySchedule.of(listOf(AllowWindow(emptySet(), t(1), t(2)), AllowWindow(setOf(MONDAY), t(3), t(3))))
        assertTrue(s.isEmpty)
    }

    @Test fun minuteOfWeekStartsMondayMidnight() {
        val monday = LocalDateTime.of(2026, 9, 14, 0, 0)
        assertEquals(MONDAY, monday.dayOfWeek)
        assertEquals(0, WeeklySchedule.minuteOfWeek(monday))
        assertEquals(WEEK - 1, WeeklySchedule.minuteOfWeek(LocalDateTime.of(2026, 9, 20, 23, 59, 30)))
    }
}
