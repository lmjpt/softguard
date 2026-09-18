package kr.woorijip.softguard.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class AllowWindowTest {
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)

    @Test fun validWindow() {
        val w = AllowWindow(weekdays, TimeOfDay.of(19, 0), TimeOfDay.of(20, 0))
        assertNull(w.validate())
        assertFalse(w.crossesMidnight)
        assertEquals(60, w.durationMinutes())
    }

    @Test fun midnightCrossing() {
        val w = AllowWindow(weekdays, TimeOfDay.of(22, 0), TimeOfDay.of(1, 0))
        assertTrue(w.isValid)
        assertTrue(w.crossesMidnight)
        assertEquals(180, w.durationMinutes())
    }

    @Test fun endOfDayIsNotCrossing() {
        val w = AllowWindow(weekdays, TimeOfDay.of(22, 0), TimeOfDay.END_OF_DAY)
        assertFalse(w.crossesMidnight)
        assertEquals(120, w.durationMinutes())
    }

    @Test fun rejectsEmptyDays() {
        assertNotNull(AllowWindow(emptySet(), TimeOfDay.of(1, 0), TimeOfDay.of(2, 0)).validate())
    }

    @Test fun rejectsSameStartAndEnd() {
        assertNotNull(AllowWindow(weekdays, TimeOfDay.of(1, 0), TimeOfDay.of(1, 0)).validate())
    }

    @Test fun rejectsStartAtEndOfDay() {
        assertNotNull(AllowWindow(weekdays, TimeOfDay.END_OF_DAY, TimeOfDay.of(1, 0)).validate())
    }
}
