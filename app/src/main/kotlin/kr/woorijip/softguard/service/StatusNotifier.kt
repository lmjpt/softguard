package kr.woorijip.softguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kr.woorijip.softguard.R
import kr.woorijip.softguard.ui.MainActivity

/** 제어가 꺼졌을 때 관리자에게 알리는 상태 알림. 평상시에는 아무 알림도 띄우지 않는다. */
object StatusNotifier {
    private const val CHANNEL_ID = "status"
    private const val ID_STOPPED = 1

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_status),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "제어가 꺼졌을 때 알려 줍니다" }
        nm.createNotificationChannel(channel)
    }

    fun notifyStopped(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("제어가 꺼졌어요")
            .setContentText("접근성 설정에서 우리집 소프트가드가 꺼졌습니다. 눌러서 확인하세요.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(ID_STOPPED, n)
    }

    fun cancelStopped(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_STOPPED)
    }
}
