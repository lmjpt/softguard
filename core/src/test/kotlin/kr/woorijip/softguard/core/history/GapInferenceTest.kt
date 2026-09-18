package kr.woorijip.softguard.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GapInferenceTest {
    private fun ev(type: LogType, at: Long) = LogEvent(1, type, at)

    @Test fun firstRunInfersNothing() {
        assertNull(GapInference.inferredStopAt(null, 5_000, 10_000))
    }

    @Test fun normalStopInfersNothing() {
        assertNull(GapInference.inferredStopAt(ev(LogType.GUARD_STOPPED, 5_000), 9_000, 10_000))
    }

    @Test fun abnormalStopUsesLastAliveAt() {
        assertEquals(9_000L, GapInference.inferredStopAt(ev(LogType.APP_LAUNCH, 5_000), 9_000, 10_000))
    }

    @Test fun usesLastEventIfLaterThanAlive() {
        assertEquals(9_500L, GapInference.inferredStopAt(ev(LogType.GUARD_STARTED, 9_500), 9_000, 10_000))
    }

    @Test fun missingAliveFallsBackToLastEvent() {
        assertEquals(5_000L, GapInference.inferredStopAt(ev(LogType.APP_LAUNCH, 5_000), null, 10_000))
    }

    @Test fun futureCandidateIsIgnored() {
        assertNull(GapInference.inferredStopAt(ev(LogType.APP_LAUNCH, 5_000), 20_000, 10_000))
    }
}
