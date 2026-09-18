package kr.woorijip.softguard.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSuppressorTest {
    private val a = "app.a"
    private val b = "app.b"

    @Test fun firstSeenIsRecorded() {
        assertTrue(DuplicateSuppressor().shouldRecord(a, 1_000))
    }

    @Test fun suppressedWithinWindow() {
        val d = DuplicateSuppressor(10_000)
        assertTrue(d.shouldRecord(a, 0))
        assertFalse(d.shouldRecord(a, 9_999))
        assertTrue(d.shouldRecord(a, 10_000))
    }

    @Test fun roundTripWithinWindowIsSuppressed() {
        val d = DuplicateSuppressor(10_000)
        assertTrue(d.shouldRecord(a, 0))
        assertTrue(d.shouldRecord(b, 2_000))
        assertFalse(d.shouldRecord(a, 5_000))
        assertTrue(d.shouldRecord(a, 10_000))
    }

    @Test fun repeatedTapsRecordOncePerWindow() {
        val d = DuplicateSuppressor(10_000)
        var recorded = 0
        for (t in 0 until 60_000 step 3_000) if (d.shouldRecord(a, t.toLong())) recorded++
        // 0, 12, 24, 36, 48 초 → 5번
        assertEquals(5, recorded)
    }

    @Test fun clockGoingBackwardsRecords() {
        val d = DuplicateSuppressor(10_000)
        assertTrue(d.shouldRecord(a, 100_000))
        assertTrue(d.shouldRecord(a, 50_000))
    }

    @Test fun memoryIsBounded() {
        val d = DuplicateSuppressor(10_000, maxEntries = 3)
        for (i in 0 until 10) d.shouldRecord("pkg$i", i.toLong())
        assertEquals(3, d.size)
        // 가장 오래된 것이 밀려났으므로 다시 보면 기록된다
        assertTrue(d.shouldRecord("pkg0", 11))
    }
}
