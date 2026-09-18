package kr.woorijip.softguard.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.SoftGuardApp
import kr.woorijip.softguard.core.history.DuplicateSuppressor
import kr.woorijip.softguard.core.policy.Verdict
import kr.woorijip.softguard.core.policy.VerdictTexts
import java.time.Instant
import java.time.LocalDateTime

/**
 * 진입점. 전면 앱이 바뀌는 순간(TYPE_WINDOW_STATE_CHANGED)마다 호출된다 (설계문서 §4, §5.6).
 *
 * 순서가 중요하다:
 *   1. 집행 — 매 이벤트마다 판정하고 오버레이를 켜거나 끈다. 중복 억제를 거치지 않는다.
 *   2. 기록 — 실행 가능 앱만, 10초 중복 억제를 거쳐 버퍼에 넣는다.
 * 중복 억제가 집행보다 앞에 있으면 "차단 앱 → 다른 앱 → 10초 안에 차단 앱" 으로 오버레이가 뜨지 않는다.
 */
class SoftGuardAccessibilityService : AccessibilityService() {

    private lateinit var graph: Graph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: BlockOverlay
    private lateinit var alarms: WindowAlarms
    private val dedup = DuplicateSuppressor()

    /** 접근성 서비스가 기억하는 마지막 전면 패키지 (알람이 울릴 때 재판정 대상) */
    private var lastForeground: String? = null
    private var lastAliveTouch = 0L
    private var lastHomeFallback = 0L
    private var policyJob: Job? = null
    private var shutDown = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    graph.logger.flushAsync()
                    graph.logger.touchAlive(now)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> graph.logger.touchAlive(now)
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            graph.appCatalog.invalidate()
            val pkg = intent.data?.schemeSpecificPart ?: return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !replacing) {
                removeFromBlockList(graph, pkg)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        graph = SoftGuardApp.graph(this)
        shutDown = false
        instance = this
        overlay = BlockOverlay(this) { goHome(fallback = false) }
        alarms = WindowAlarms(this, graph.diag)

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            RECEIVER_EXPORTED,
        )
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            RECEIVER_EXPORTED,
        )

        // 정책이 바뀌면 지금 전면에 있는 앱을 다시 판정한다
        policyJob = scope.launch {
            graph.policies.state.collect { reevaluateForeground("정책 변경") }
        }
        scope.launch(Dispatchers.IO) { graph.logger.recordGuardStarted(System.currentTimeMillis()) }
        StatusNotifier.cancelStopped(this)
        graph.diag.log("접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::graph.isInitialized || shutDown) return
        val pkg = event.packageName?.toString() ?: return
        val nowMs = System.currentTimeMillis()

        if (nowMs - lastAliveTouch > ALIVE_TOUCH_INTERVAL_MS) {
            lastAliveTouch = nowMs
            graph.logger.touchAlive(nowMs)
        }

        if (pkg == packageName) {
            // 우리 화면(액티비티)이 올라온 것과 우리가 띄운 오버레이 창을 구분한다
            val cls = event.className?.toString() ?: ""
            if (cls.startsWith(packageName)) {
                overlay.hide()
                alarms.cancel()
                lastForeground = pkg
            }
            return
        }

        val catalog = graph.appCatalog
        if (catalog.isTransient(pkg)) return // 알림 창·입력기·권한 대화상자: 상태 유지

        lastForeground = pkg
        val verdict = enforce(pkg, nowMs)

        // 여기부터는 기록만. 홈 화면 복귀·시스템 화면은 기록하지 않는다
        if (!catalog.isLoggable(pkg)) return
        if (!dedup.shouldRecord(pkg, nowMs)) return
        val label = catalog.labelOf(pkg)
        graph.logger.recordLaunch(pkg, label, nowMs, verdict is Verdict.Blocked, (verdict as? Verdict.Blocked)?.reason)
        graph.diag.log(if (verdict is Verdict.Blocked) "차단  $label (${verdict.reason})" else "실행  $label")
    }

    /** 판정하고 오버레이·알람을 맞춘다. 매 이벤트마다 부른다. */
    private fun enforce(pkg: String, nowMs: Long): Verdict {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), graph.zone)
        val verdict = graph.policies.engine.evaluate(pkg, now)
        when (verdict) {
            is Verdict.Allowed -> {
                overlay.hide()
                val until = verdict.until
                if (until != null) alarms.schedule(until, graph.zone, "허용 종료") else alarms.cancel()
            }
            is Verdict.Blocked -> {
                showBlock(pkg, verdict, now)
                val next = verdict.nextAvailableAt
                if (next != null) alarms.schedule(next, graph.zone, "허용 시작") else alarms.cancel()
            }
        }
        return verdict
    }

    private fun showBlock(pkg: String, verdict: Verdict.Blocked, now: LocalDateTime) {
        val label = graph.appCatalog.labelOf(pkg)
        val texts = VerdictTexts.forBlocked(verdict, now)
        try {
            overlay.show(label, texts.title, texts.body)
        } catch (e: Exception) {
            graph.diag.log("차단 화면을 띄우지 못해 홈으로 내보냈습니다: ${e.message}")
            goHome(fallback = true)
        }
    }

    private fun goHome(fallback: Boolean) {
        val now = System.currentTimeMillis()
        if (fallback && now - lastHomeFallback < HOME_FALLBACK_MIN_INTERVAL_MS) return
        lastHomeFallback = now
        overlay.hide()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** 알람(허용 시작/종료)이나 정책 변경 때 마지막 전면 앱을 다시 판정한다 (§7.4). */
    fun reevaluateForeground(why: String) {
        if (!::graph.isInitialized || shutDown) return
        val pkg = lastForeground ?: return
        if (pkg == packageName) return
        val nowMs = System.currentTimeMillis()
        val wasShowing = overlay.isShowing
        val verdict = enforce(pkg, nowMs)
        val label = graph.appCatalog.labelOf(pkg)
        if (verdict is Verdict.Blocked && !wasShowing) {
            graph.logger.recordLaunch(pkg, label, nowMs, true, verdict.reason)
            graph.diag.log("$why → 차단  $label")
        } else if (verdict is Verdict.Allowed && wasShowing) {
            graph.diag.log("$why → 열림  $label")
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown("unbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown("destroy")
        super.onDestroy()
    }

    private fun shutdown(why: String) {
        if (shutDown || !::graph.isInitialized) return
        shutDown = true
        instance = null
        graph.diag.log("접근성 서비스 종료 ($why)")
        runCatching { overlay.destroy() }
        runCatching { alarms.cancel() }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        policyJob?.cancel()
        graph.logger.recordGuardStoppedNow(System.currentTimeMillis())
        StatusNotifier.notifyStopped(this)
        scope.cancel()
    }

    companion object {
        @Volatile
        var instance: SoftGuardAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null

        private const val ALIVE_TOUCH_INTERVAL_MS = 60_000L
        private const val HOME_FALLBACK_MIN_INTERVAL_MS = 2_000L

        /** 삭제된 앱을 차단 목록에서 뺀다. 이력은 그대로 둔다. */
        fun removeFromBlockList(graph: Graph, pkg: String) {
            if (pkg !in graph.policies.policy.blockedPackages) return
            graph.policies.update { it.withBlocked(pkg, false) }
            graph.diag.log("삭제된 앱을 차단 목록에서 정리: $pkg")
        }
    }
}
