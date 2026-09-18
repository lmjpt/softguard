package kr.woorijip.softguard.core.storage

import kr.woorijip.softguard.core.model.AllowWindow
import kr.woorijip.softguard.core.model.Policy
import kr.woorijip.softguard.core.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

class PolicyCodecTest {
    private val sample = Policy(
        blockedPackages = setOf("com.sec.android.app.sbrowser", "com.google.android.youtube", "com.android.vending"),
        allowWindows = listOf(
            AllowWindow(setOf(SATURDAY, SUNDAY), TimeOfDay.of(19, 0), TimeOfDay.of(21, 0)),
            AllowWindow(setOf(FRIDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY), TimeOfDay.of(19, 0), TimeOfDay.of(20, 0)),
            AllowWindow(setOf(SUNDAY, SATURDAY), TimeOfDay.of(10, 0), TimeOfDay.of(12, 0)),
        ),
    )

    private val golden = """
        {
          "version": 3,
          "defaultPolicy": "ALLOW_ALL",
          "blockedPackages": [
            "com.android.vending",
            "com.google.android.youtube",
            "com.sec.android.app.sbrowser"
          ],
          "allowWindows": [
            {
              "days": [
                "MON",
                "TUE",
                "WED",
                "THU",
                "FRI"
              ],
              "from": "19:00",
              "to": "20:00"
            },
            {
              "days": [
                "SAT",
                "SUN"
              ],
              "from": "10:00",
              "to": "12:00"
            },
            {
              "days": [
                "SAT",
                "SUN"
              ],
              "from": "19:00",
              "to": "21:00"
            }
          ]
        }
    """.trimIndent()

    private fun ok(text: String): Policy = (PolicyCodec.decode(text) as DecodeResult.Ok).policy
    private fun failed(text: String): String = (PolicyCodec.decode(text) as DecodeResult.Failed).message

    @Test fun goldenOutput() {
        assertEquals(golden, PolicyCodec.encode(sample))
    }

    @Test fun roundTrip() {
        val decoded = ok(PolicyCodec.encode(sample))
        assertEquals(sample.blockedPackages, decoded.blockedPackages)
        assertEquals(sample.allowWindows.toSet(), decoded.allowWindows.toSet())
    }

    @Test fun deterministicRegardlessOfInputOrder() {
        val shuffled = Policy(
            blockedPackages = sample.blockedPackages.reversed().toSet(),
            allowWindows = sample.allowWindows.reversed(),
        )
        assertEquals(PolicyCodec.encode(sample), PolicyCodec.encode(shuffled))
    }

    @Test fun emptyPolicy() {
        val text = PolicyCodec.encode(Policy.EMPTY)
        assertEquals(Policy.EMPTY, ok(text))
    }

    @Test fun rejectsOtherVersions() {
        assertTrue(failed(golden.replace("\"version\": 3", "\"version\": 2")).contains("버전"))
    }

    @Test fun rejectsWhitelistEraDefault() {
        assertTrue(failed(golden.replace("ALLOW_ALL", "BLOCK_ALL")).contains("기본 정책"))
    }

    @Test fun rejectsUnknownKeys() {
        val withTypo = golden.replace("\"allowWindows\"", "\"allowWindow\": [], \"allowWindows\"")
        assertTrue(PolicyCodec.decode(withTypo) is DecodeResult.Failed)
    }

    @Test fun rejectsUnknownDay() {
        assertTrue(failed(golden.replace("\"MON\"", "\"MONDAY\"")).contains("요일"))
    }

    @Test fun acceptsLowercaseAndPaddedDays() {
        val relaxed = golden.replace("\"MON\"", "\" mon \"")
        assertEquals(sample.allowWindows.toSet(), ok(relaxed).allowWindows.toSet())
    }

    @Test fun rejectsBadTime() {
        assertTrue(failed(golden.replace("\"19:00\"", "\"19:60\"")).contains("시각"))
    }

    @Test fun rejectsInvalidWindow() {
        val same = golden.replace("\"to\": \"20:00\"", "\"to\": \"19:00\"")
        assertTrue(failed(same).contains("같아요"))
    }

    @Test fun brokenJsonFailsWithoutThrowing() {
        assertTrue(PolicyCodec.decode("{ not json") is DecodeResult.Failed)
        assertTrue(PolicyCodec.decode("") is DecodeResult.Failed)
    }

    @Test fun acceptsMidnightEnd() {
        val p = ok(golden.replace("\"to\": \"21:00\"", "\"to\": \"24:00\""))
        assertTrue(p.allowWindows.any { it.to == TimeOfDay.END_OF_DAY })
    }
}
