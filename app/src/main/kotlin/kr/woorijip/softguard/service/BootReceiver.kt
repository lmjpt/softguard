package kr.woorijip.softguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.woorijip.softguard.SoftGuardApp

/**
 * 부팅 완료 (설계문서 §5.5). 접근성 서비스는 시스템이 알아서 다시 바인드하므로 여기서는
 * DEVICE_BOOT 이력을 남기고 정리 작업이 예약돼 있는지만 확인한다. UI 는 띄우지 않는다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val graph = SoftGuardApp.graph(context)
        graph.diag.log("기기 부팅")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                graph.logger.recordBoot(System.currentTimeMillis())
                LogPruneWorker.ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** 앱이 완전히 삭제되면 차단 목록에서 뺀다 (이력은 보존). */
class PackageRemovedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val graph = SoftGuardApp.graph(context)
        graph.appCatalog.invalidate()
        SoftGuardAccessibilityService.removeFromBlockList(graph, pkg)
    }
}
