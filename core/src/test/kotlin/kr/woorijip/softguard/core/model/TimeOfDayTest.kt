package kr.woorijip.softguard.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeOfDayTest {
    @Test fun parsesNormalTimes() {
        assertEquals(TimeOfDay.of(19, 0), TimeOfDay.parse("19:00"))
        assertEquals(TimeOfDay.of(0, 0), TimeOfDay.parse("00:00"))
        assertEquals(TimeOfDay.of(9, 5), TimeOfDay.parse("9:05"))
        assertEquals(TimeOfDay.of(23, 59), TimeOfDay.parse(" 23:59 "))
    }

    @Test fun allowsMidnightEndOnly() {
        assertEquals(TimeOfDay.END_OF_DAY, TimeOfDay.parse("24:00"))
        assertNull(TimeOfDay.parse("24:01"))
        assertNull(TimeOfDay.parse("25:00"))
    }

    @Test fun rejectsMalformed() {
        assertNull(TimeOfDay.parse("19:60"))
        assertNull(TimeOfDay.parse("19"))
        assertNull(TimeOfDay.parse("19:0"))
        assertNull(TimeOfDay.parse("abc"))
        assertNull(TimeOfDay.parse(""))
    }

    @Test fun formatsWithTwoDigits() {
        assertEquals("07:05", TimeOfDay.of(7, 5).toString())
        assertEquals("24:00", TimeOfDay.END_OF_DAY.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRange() {
        TimeOfDay(1441)
    }
}
