package kr.woorijip.softguard.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kr.woorijip.softguard.SoftGuardApp
import kr.woorijip.softguard.data.DetectionLog
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 허용 시간대 경계 알람 (설계문서 §7.4). 한 번에 최대 1개.
 *   - 차단 앱이 허용 시간 중 전면에 있으면 → 종료 시각 알람 → 정각에 오버레이
 *   - 차단 앱이 오버레이에 덮여 있으면    → 시작 시각 알람 → 정각에 오버레이 해제
 * 정확 알람 권한이 없으면 부정확 알람으로 폴백한다 (수 분 오차, 대시보드에 표시).
 */
class WindowAlarms(private val context: Context, private val diag: DetectionLog) {
    private val am = context.getSystemService(AlarmManager::class.java)
    private var scheduledAt: Long? = null
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WindowAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(at: LocalDateTime, zone: ZoneId, label: String) {
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        if (scheduledAt == millis) return
        cancel()
        val pi = pendingIntent()
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
            scheduledAt = millis
            diag.log("알람 등록: $label ${at.format(fmt)}")
        } catch (e: SecurityException) {
            diag.log("알람 등록 실패: ${e.message}")
        }
    }

    fun cancel() {
        if (scheduledAt == null) return
        am.cancel(pendingIntent())
        scheduledAt = null
    }
}

class WindowAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = SoftGuardAccessibilityService.instance
        if (service == null) {
            SoftGuardApp.graph(context).diag.log("알람이 울렸지만 서비스가 없음")
            return
        }
        service.reevaluateForeground("알람")
    }
}
