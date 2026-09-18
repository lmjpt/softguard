package kr.woorijip.softguard.core.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * yyyyMMdd 정수. 날짜별 조회의 기준 컬럼 (설계문서 §8.4).
 * 기록 시점의 로컬 날짜로 한 번 계산해 저장하므로, 나중에 타임존이 바뀌어도 소속 날짜가 흔들리지 않는다.
 */
object DayKey {
    fun of(date: LocalDate): Int = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

    fun of(epochMillis: Long, zone: ZoneId): Int =
        of(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate())

    fun toDate(key: Int): LocalDate = LocalDate.of(key / 10000, key / 100 % 100, key % 100)

    /**
     * 보관 기준. 오늘을 포함해 retentionDays 일을 남기고, 이 값보다 작은 dayKey 는 지운다.
     * 예) 180일 보관이면 cutoff = 오늘 - 179일.
     */
    fun cutoff(today: LocalDate, retentionDays: Int): Int =
        of(today.minusDays((retentionDays - 1).toLong()))

    /** 그 날의 시작(00:00) epoch millis */
    fun startOfDayMillis(key: Int, zone: ZoneId): Long =
        toDate(key).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 다음 날의 시작 = 그 날의 끝 (반열린 구간의 끝) */
    fun endOfDayMillis(key: Int, zone: ZoneId): Long =
        toDate(key).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
