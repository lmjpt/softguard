package kr.woorijip.softguard.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.core.history.DayKey
import kr.woorijip.softguard.core.policy.BlockReason
import kr.woorijip.softguard.core.policy.Verdict
import kr.woorijip.softguard.core.policy.VerdictTexts
import kr.woorijip.softguard.service.SoftGuardAccessibilityService
import kr.woorijip.softguard.ui.theme.StatusColors
import kr.woorijip.softguard.util.Permissions
import java.time.LocalDate
import java.time.LocalDateTime

/** 권한·서비스 상태 묶음. 화면에 들어올 때와 3초마다 다시 읽는다. */
data class GuardStatus(
    val accessibility: Boolean,
    val running: Boolean,
    val battery: Boolean,
    val exactAlarm: Boolean,
    val notifications: Boolean,
) {
    companion object {
        fun read(context: Context) = GuardStatus(
            accessibility = Permissions.isAccessibilityEnabled(context),
            running = SoftGuardAccessibilityService.isRunning,
            battery = Permissions.isIgnoringBatteryOptimizations(context),
            exactAlarm = Permissions.canScheduleExactAlarms(context),
            notifications = Permissions.notificationsEnabled(context),
        )
    }
}

@Composable
fun DashboardScreen(graph: Graph, resumeTick: Int) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeTick) {
        while (true) {
            tick++
            delay(3_000)
        }
    }
    val status = remember(tick) { GuardStatus.read(context) }
    val policyState by graph.policies.state.collectAsState()
    val today = DayKey.of(LocalDate.now(graph.zone))
    val dao = graph.db.eventLogDao()
    val launches by remember(today) { dao.observeLaunchCount(today) }.collectAsState(initial = 0)
    val blocked by remember(today) { dao.observeBlockedCount(today) }.collectAsState(initial = 0)
    val since = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
    val stops by remember { dao.observeGuardStopsSince(since) }.collectAsState(initial = 0)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (headline, detail, ok) = when {
            !status.accessibility -> Triple("제어 중단됨", "접근성 서비스가 꺼져 있어요. 차단도 기록도 멈춘 상태예요.", false)
            !status.running -> Triple("서비스 연결 대기 중", "권한은 켜졌어요. 시스템이 서비스를 붙이기를 기다리고 있어요. 잠시 뒤 다시 열어 보세요.", false)
            else -> Triple("제어 동작 중", "차단 목록의 앱을 정해진 시간에만 열어 주고 있어요.", true)
        }
        val tint = if (ok) StatusColors.Ok else StatusColors.Warn
        Card(colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.15f))) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(headline, style = MaterialTheme.typography.headlineSmall)
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (policyState.loadFailed) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("정책에 문제가 있습니다", style = MaterialTheme.typography.titleMedium)
                    Text("정책 파일을 읽지 못해 아무것도 차단하지 않는 상태로 동작 중이에요. 차단 앱이나 시간대를 한 번 저장하면 새로 만들어져요.")
                    policyState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        SectionCard("지금") {
            val n = policyState.policy.blockedPackages.size
            if (n == 0) {
                Text("차단 목록이 비어 있어요. 모든 앱이 자유롭게 열려요.")
            } else {
                val now = LocalDateTime.now(graph.zone)
                Text("차단 앱 ${n}개", style = MaterialTheme.typography.bodyLarge)
                when (val v = graph.policies.engine.evaluateBlockedApp(now)) {
                    is Verdict.Allowed -> Text(
                        v.until?.let { "지금은 사용 시간이에요 · ${VerdictTexts.hhmm(it)}까지" } ?: "항상 사용할 수 있는 시간대예요",
                        color = StatusColors.Ok,
                    )
                    is Verdict.Blocked -> Text(
                        if (v.reason == BlockReason.ALWAYS_BLOCKED) "항상 차단 (허용 시간대가 없어요)"
                        else "지금은 차단 중 · ${v.nextAvailableAt?.let { "${VerdictTexts.dayWord(now, it)} ${VerdictTexts.hhmm(it)}부터 열려요" } ?: ""}",
                    )
                }
            }
            Text("오늘 실행 ${launches}건 · 차단 ${blocked}건")
            Text(
                "최근 7일 제어 중단 ${stops}회",
                color = if (stops > 0) StatusColors.Warn else MaterialTheme.colorScheme.onSurface,
            )
        }

        SectionCard("점검") {
            CheckRow(
                ok = status.accessibility,
                label = "접근성 서비스",
                detail = if (status.accessibility) null else "꺼져 있으면 차단과 기록이 모두 멈춰요",
                actionLabel = "설정 열기",
            ) { Permissions.openAccessibilitySettings(context) }
            CheckRow(
                ok = status.accessibility,
                label = "제한된 설정 허용",
                detail = "접근성 토글이 회색이면: 앱 정보 → 오른쪽 위 ⋮ → 제한된 설정 허용",
                actionLabel = "앱 정보",
            ) { Permissions.openAppInfo(context) }
            CheckRow(
                ok = status.battery,
                label = "배터리 최적화 제외",
                detail = "절전 기능이 서비스를 끄지 않게 해요",
                actionLabel = "제외 요청",
            ) { Permissions.requestIgnoreBatteryOptimizations(context) }
            CheckRow(
                ok = status.exactAlarm,
                label = "정확한 알람",
                detail = "없어도 되지만 시간대 전환이 몇 분 늦을 수 있어요",
                actionLabel = "설정",
            ) { Permissions.openExactAlarmSettings(context) }
            CheckRow(
                ok = status.notifications,
                label = "알림",
                detail = "제어가 꺼지면 알려 드려요",
                actionLabel = if (status.notifications) "설정" else "허용",
            ) {
                if (status.notifications) Permissions.openNotificationSettings(context)
                else notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        SectionCard("삼성 기기라면 한 번 더") {
            Text("• 설정 → 배터리 → 백그라운드 사용 제한 → '절전 모드로 전환하지 않을 앱'에 이 앱을 추가", style = MaterialTheme.typography.bodyMedium)
            Text("• 설정 → 앱 → 우리집 소프트가드 → 배터리 → '제한 없음'", style = MaterialTheme.typography.bodyMedium)
            Text("• 설정 → 디바이스 케어 → ⋮ → 자동 최적화가 켜져 있으면, 재부팅 뒤 이 화면이 '제어 동작 중'인지 확인", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { Permissions.openAppInfo(context) }) { Text("앱 정보") }
                TextButton(onClick = { Permissions.openBatterySaverSettings(context) }) { Text("배터리 설정") }
            }
        }
    }
}
