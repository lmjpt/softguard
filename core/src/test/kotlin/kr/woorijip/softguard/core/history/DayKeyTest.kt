package kr.woorijip.softguard.core.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DayKeyTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test fun formatsAsYyyyMmDd() {
        assertEquals(20260918, DayKey.of(LocalDate.of(2026, 9, 18)))
        assertEquals(20260101, DayKey.of(LocalDate.of(2026, 1, 1)))
    }

    @Test fun roundTrips() {
        val d = LocalDate.of(2026, 12, 31)
        assertEquals(d, DayKey.toDate(DayKey.of(d)))
    }

    @Test fun midnightBoundaryInLocalZone() {
        val beforeMidnight = LocalDateTime.of(2026, 9, 17, 23, 59, 59).atZone(seoul).toInstant().toEpochMilli()
        val afterMidnight = LocalDateTime.of(2026, 9, 18, 0, 0, 0).atZone(seoul).toInstant().toEpochMilli()
        assertEquals(20260917, DayKey.of(beforeMidnight, seoul))
        assertEquals(20260918, DayKey.of(afterMidnight, seoul))
    }

    @Test fun cutoffKeepsRetentionDaysIncludingToday() {
        val today = LocalDate.of(2026, 9, 18)
        // 180일 보관 → 오늘 포함 180일 = 2026-03-23 부터 남긴다 (3/23 ~ 9/18 = 180일)
        assertEquals(DayKey.of(LocalDate.of(2026, 3, 23)), DayKey.cutoff(today, 180))
        assertEquals(DayKey.of(today), DayKey.cutoff(today, 1))
    }

    @Test fun dayBoundsMillis() {
        val key = 20260918
        val start = DayKey.startOfDayMillis(key, seoul)
        val end = DayKey.endOfDayMillis(key, seoul)
        assertEquals(24 * 60 * 60 * 1000L, end - start)
        assertEquals(key, DayKey.of(start, seoul))
        assertEquals(20260919, DayKey.of(end, seoul))
    }
}
