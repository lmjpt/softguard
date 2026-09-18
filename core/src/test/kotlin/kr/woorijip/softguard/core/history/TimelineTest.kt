package kr.woorijip.softguard.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {
    private val dayStart = 1_000_000L
    private val dayEnd = dayStart + 86_400_000L
    private var nextId = 1L

    private fun launch(at: Long, pkg: String = "app.a", blocked: Boolean = false) =
        LogEvent(nextId++, LogType.APP_LAUNCH, dayStart + at, pkg, pkg, blocked)

    private fun guard(type: LogType, at: Long, inferred: Boolean = false) =
        LogEvent(nextId++, type, dayStart + at, inferred = inferred)

    @Test fun launchesOnlyComeOutNewestFirst() {
        val items = Timeline.build(listOf(launch(1_000), launch(2_000)), dayStart, dayEnd, null, dayEnd)
        assertEquals(2, items.size)
        assertEquals(dayStart + 2_000, (items[0] as TimelineItem.Launch).event.occurredAt)
        assertEquals(dayStart + 1_000, (items[1] as TimelineItem.Launch).event.occurredAt)
    }

    @Test fun stopAndStartBecomeGap() {
        val items = Timeline.build(
            listOf(launch(1_000), guard(LogType.GUARD_STOPPED, 2_000), guard(LogType.GUARD_STARTED, 5_000), launch(6_000)),
            dayStart, dayEnd, null, dayEnd,
        )
        assertEquals(3, items.size)
        val gap = items[1] as TimelineItem.Gap
        assertEquals(dayStart + 2_000, gap.from)
        assertEquals(dayStart + 5_000, gap.to)
        assertEquals(false, gap.inferred)
        assertEquals(false, gap.rebooted)
        assertEquals(false, gap.ongoing)
    }

    @Test fun inferredStopIsMarked() {
        val items = Timeline.build(
            listOf(guard(LogType.GUARD_STOPPED, 2_000, inferred = true), guard(LogType.GUARD_STARTED, 5_000)),
            dayStart, dayEnd, null, dayEnd,
        )
        assertTrue((items.single() as TimelineItem.Gap).inferred)
    }

    @Test fun bootInsideGapMarksReboot() {
        val items = Timeline.build(
            listOf(guard(LogType.GUARD_STOPPED, 2_000, inferred = true), guard(LogType.DEVICE_BOOT, 4_000), guard(LogType.GUARD_STARTED, 5_000)),
            dayStart, dayEnd, null, dayEnd,
        )
        val gap = items.single() as TimelineItem.Gap
        assertTrue(gap.rebooted)
    }

    @Test fun bootWithoutStopIsStandaloneMarker() {
        val items = Timeline.build(listOf(guard(LogType.DEVICE_BOOT, 4_000)), dayStart, dayEnd, null, dayEnd)
        assertTrue(items.single() is TimelineItem.Boot)
    }

    @Test fun openGapFromPreviousDayIsClampedToDayStart() {
        val prev = LogEvent(99, LogType.GUARD_STOPPED, dayStart - 3_600_000)
        val items = Timeline.build(listOf(guard(LogType.GUARD_STARTED, 5_000)), dayStart, dayEnd, prev, dayEnd)
        val gap = items.single() as TimelineItem.Gap
        assertEquals(dayStart, gap.from)
        assertTrue(gap.startsBeforeDay)
    }

    @Test fun unclosedGapTodayIsOngoingUntilNow() {
        val now = dayStart + 10_000
        val items = Timeline.build(listOf(guard(LogType.GUARD_STOPPED, 2_000)), dayStart, dayEnd, null, now)
        val gap = items.single() as TimelineItem.Gap
        assertEquals(now, gap.to)
        assertTrue(gap.ongoing)
        assertEquals(false, gap.endsAfterDay)
    }

    @Test fun unclosedGapOnPastDayRunsToDayEnd() {
        val items = Timeline.build(listOf(guard(LogType.GUARD_STOPPED, 2_000)), dayStart, dayEnd, null, dayEnd + 999)
        val gap = items.single() as TimelineItem.Gap
        assertEquals(dayEnd, gap.to)
        assertTrue(gap.endsAfterDay)
        assertEquals(false, gap.ongoing)
    }

    @Test fun consecutiveStopsKeepTheFirst() {
        val items = Timeline.build(
            listOf(guard(LogType.GUARD_STOPPED, 2_000), guard(LogType.GUARD_STOPPED, 3_000), guard(LogType.GUARD_STARTED, 5_000)),
            dayStart, dayEnd, null, dayEnd,
        )
        assertEquals(dayStart + 2_000, (items.single() as TimelineItem.Gap).from)
    }
}
