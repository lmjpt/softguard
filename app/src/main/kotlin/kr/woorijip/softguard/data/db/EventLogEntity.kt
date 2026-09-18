package kr.woorijip.softguard.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kr.woorijip.softguard.core.history.LogEvent
import kr.woorijip.softguard.core.history.LogType

/**
 * 이력 한 줄 (설계문서 §8.4).
 * 스키마를 바꾸면 반드시 Migration 을 쓴다. destructive migration 금지 (180일 이력이 날아간다).
 */
@Entity(
    tableName = "event_log",
    indices = [Index("dayKey"), Index("occurredAt"), Index("packageName")],
)
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LogType 이름 */
    val type: String,
    /** epoch millis */
    val occurredAt: Long,
    /** yyyyMMdd, 기록 시점의 로컬 날짜 */
    val dayKey: Int,
    /** APP_LAUNCH 일 때만 */
    val packageName: String?,
    /** 기록 시점 이름 그대로. 앱이 삭제돼도 이름이 남는다 */
    val appLabel: String?,
    /** APP_LAUNCH 일 때만 의미 있음 */
    val blocked: Boolean = false,
    /** "ALWAYS_BLOCKED" | "OUT_OF_HOURS" | null */
    val blockReason: String?,
    /** GUARD_STOPPED 를 추론으로 만들었는가 */
    val inferred: Boolean = false,
) {
    fun toLogEvent(): LogEvent = LogEvent(
        id = id,
        type = LogType.valueOf(type),
        occurredAt = occurredAt,
        packageName = packageName,
        appLabel = appLabel,
        blocked = blocked,
        blockReason = blockReason,
        inferred = inferred,
    )
}
