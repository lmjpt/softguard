package kr.woorijip.softguard.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kr.woorijip.softguard.core.history.DayKey
import kr.woorijip.softguard.core.history.GapInference
import kr.woorijip.softguard.core.history.LogType
import kr.woorijip.softguard.core.policy.BlockReason
import kr.woorijip.softguard.data.db.EventLogDao
import kr.woorijip.softguard.data.db.EventLogEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 이력 기록 파이프라인 (설계문서 §8.5).
 *   접근성 이벤트 → 메모리 버퍼(상한 200) → 10건 또는 15초마다 / 화면 꺼짐 / 종료 시 Room 일괄 insert
 *   flush 성공 시 lastAliveAt 갱신 → §8.7 공백 추론의 기준점
 *
 * 접근성 콜백을 막지 않도록 IO 는 전부 별도 코루틴에서 한다.
 * GUARD_STOPPED 만은 종료 중이라 다음 기회가 없으므로 동기로 쓴다.
 */
class EventLogger(
    private val dao: EventLogDao,
    private val prefs: GuardPrefs,
    private val diag: DetectionLog,
    private val zone: ZoneId,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Any()
    private val buffer = ArrayDeque<EventLogEntity>()
    private var timer: Job? = null

    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    private fun fmt(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().format(fmt)

    private fun entity(type: LogType, at: Long, inferred: Boolean = false) = EventLogEntity(
        type = type.name,
        occurredAt = at,
        dayKey = DayKey.of(at, zone),
        packageName = null,
        appLabel = null,
        blockReason = null,
        inferred = inferred,
    )

    fun recordLaunch(packageName: String, appLabel: String, at: Long, blocked: Boolean, reason: BlockReason?) {
        val row = EventLogEntity(
            type = LogType.APP_LAUNCH.name,
            occurredAt = at,
            dayKey = DayKey.of(at, zone),
            packageName = packageName,
            appLabel = appLabel,
            blocked = blocked,
            blockReason = reason?.name,
        )
        val count = synchronized(lock) {
            if (buffer.size >= MAX_BUFFER) buffer.removeFirst()
            buffer.addLast(row)
            buffer.size
        }
        if (count >= FLUSH_COUNT) flushAsync() else scheduleTimer()
    }

    private fun scheduleTimer() {
        synchronized(lock) {
            if (timer != null) return
            timer = scope.launch {
                delay(FLUSH_DELAY_MS)
                synchronized(lock) { timer = null }
                flush()
            }
        }
    }

    fun flushAsync() {
        scope.launch { flush() }
    }

    /** 버퍼를 DB 에 쓴다. 비어 있으면 아무 일도 하지 않는다. */
    suspend fun flush() {
        val rows: List<EventLogEntity> = synchronized(lock) {
            if (buffer.isEmpty()) return
            val copy = buffer.toList()
            buffer.clear()
            copy
        }
        withContext(NonCancellable) {
            try {
                dao.insertAll(rows)
                prefs.lastAliveAt = System.currentTimeMillis()
            } catch (e: Exception) {
                diag.log("이력 저장 실패 ${rows.size}건: ${e.message}")
            }
        }
    }

    /** 살아 있음 표시. 화면 켬/꺼짐, 이벤트 수신(1분 간격) 때 부른다 — 유휴 구간의 추정 오차를 줄인다. */
    fun touchAlive(now: Long) {
        prefs.lastAliveAt = now
    }

    /** 서비스 시작. 직전이 정상 종료가 아니면 추정 종료를 먼저 끼워 넣는다 (§8.7). */
    suspend fun recordGuardStarted(now: Long) {
        flush()
        val last = dao.latestNonBoot()?.toLogEvent()
        val inferredAt = GapInference.inferredStopAt(last, prefs.lastAliveAt.takeIf { it > 0L }, now)
        val rows = ArrayList<EventLogEntity>(2)
        if (inferredAt != null) {
            rows += entity(LogType.GUARD_STOPPED, inferredAt, inferred = true)
            diag.log("비정상 종료로 추정 (마지막 생존 ${fmt(inferredAt)})")
        }
        rows += entity(LogType.GUARD_STARTED, now)
        withContext(NonCancellable) {
            try {
                dao.insertAll(rows)
                prefs.lastAliveAt = now
            } catch (e: Exception) {
                diag.log("시작 기록 실패: ${e.message}")
            }
        }
    }

    /** 정상 종료. onUnbind 안에서 동기로 쓴다 — 종료 중이라 다음 기회가 없다. */
    fun recordGuardStoppedNow(now: Long) {
        runBlocking {
            withContext(Dispatchers.IO) {
                flush()
                try {
                    dao.insertAll(listOf(entity(LogType.GUARD_STOPPED, now)))
                    prefs.lastAliveAt = now
                } catch (e: Exception) {
                    diag.log("종료 기록 실패: ${e.message}")
                }
            }
        }
    }

    suspend fun recordBoot(now: Long) {
        try {
            dao.insertAll(listOf(entity(LogType.DEVICE_BOOT, now)))
        } catch (e: Exception) {
            diag.log("부팅 기록 실패: ${e.message}")
        }
    }

    companion object {
        const val MAX_BUFFER = 200
        const val FLUSH_COUNT = 10
        const val FLUSH_DELAY_MS = 15_000L
    }
}
