package kr.woorijip.softguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Insert
    suspend fun insertAll(rows: List<EventLogEntity>)

    /** 특정 날짜 전체, 오래된 것부터 (타임라인 계산용. 화면은 뒤집어 최신순으로 보인다) */
    @Query("SELECT * FROM event_log WHERE dayKey = :dayKey ORDER BY occurredAt ASC, id ASC")
    fun observeDay(dayKey: Int): Flow<List<EventLogEntity>>

    /** 부팅을 제외한 마지막 이력 — 비정상 종료 추론의 기준 */
    @Query("SELECT * FROM event_log WHERE type != 'DEVICE_BOOT' ORDER BY occurredAt DESC, id DESC LIMIT 1")
    suspend fun latestNonBoot(): EventLogEntity?

    /** 어느 시각 이전의 마지막 제어 상태 이벤트 — 전날부터 이어진 공백 판별 */
    @Query(
        "SELECT * FROM event_log WHERE occurredAt < :before AND type IN ('GUARD_STARTED','GUARD_STOPPED') " +
            "ORDER BY occurredAt DESC, id DESC LIMIT 1"
    )
    suspend fun lastGuardEventBefore(before: Long): EventLogEntity?

    /** 기록이 있는 날짜 목록, 최신순 */
    @Query("SELECT DISTINCT dayKey FROM event_log ORDER BY dayKey DESC")
    fun observeDaysWithRecords(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM event_log WHERE dayKey = :dayKey AND type = 'APP_LAUNCH'")
    fun observeLaunchCount(dayKey: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM event_log WHERE dayKey = :dayKey AND type = 'APP_LAUNCH' AND blocked = 1")
    fun observeBlockedCount(dayKey: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM event_log WHERE type = 'GUARD_STOPPED' AND occurredAt >= :since")
    fun observeGuardStopsSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM event_log")
    fun observeTotalCount(): Flow<Int>

    /** 180일 정리. cutoff 보다 작은 dayKey 를 지운다 */
    @Query("DELETE FROM event_log WHERE dayKey < :cutoffDayKey")
    suspend fun pruneBefore(cutoffDayKey: Int): Int

    @Query("DELETE FROM event_log")
    suspend fun deleteAll()
}
