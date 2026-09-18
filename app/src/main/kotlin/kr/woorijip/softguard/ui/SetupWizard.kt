package kr.woorijip.softguard.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.data.PinStore
import kr.woorijip.softguard.util.Permissions

private const val STEPS = 7

/**
 * 초기 설정 마법사 (설계문서 §9).
 * ① PIN ② 접근성(제한된 설정) ③ 배터리 ④ 정확 알람·알림 ⑤ 차단 앱 ⑥ 허용 시간대 ⑦ 완료
 * 권한 단계는 건너뛸 수 있다 — 대시보드가 빠진 것을 계속 알려 준다.
 */
@Composable
fun SetupWizard(graph: Graph, resumeTick: Int, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(if (graph.pinStore.isSet) 1 else 0) }

    Scaffold(
        bottomBar = {
            if (step >= 1) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { step-- }, enabled = step > 1) { Text("이전") }
                    Button(onClick = { if (step == STEPS - 1) onFinished() else step++ }) {
                        Text(if (step == STEPS - 1) "시작하기" else "다음")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LinearProgressIndicator(progress = { (step + 1) / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text(
                "${step + 1} / $STEPS",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (step) {
                0 -> PinSetup(graph.pinStore) { step = 1 }
                1 -> StepAccessibility(resumeTick)
                2 -> StepBattery(resumeTick)
                3 -> StepAlarmNotification(resumeTick)
                4 -> Column(Modifier.fillMaxSize()) {
                    StepHeader("차단할 앱을 고르세요", "여기 넣은 앱만 시간 제한을 받아요. 나머지는 항상 자유예요. 스토어와 브라우저를 함께 넣는 것을 추천해요.")
                    BlockListScreen(graph, resumeTick, Modifier.weight(1f))
                }
                5 -> Column(Modifier.fillMaxSize()) {
                    StepHeader("열어 줄 시간을 정하세요", "차단 앱이 열리는 시간이에요. 비워 두면 항상 차단돼요.")
                    ScheduleScreen(graph, Modifier.weight(1f))
                }
                else -> StepDone()
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepBody(title: String, body: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun PinSetup(pinStore: PinStore, onDone: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    if (first.isEmpty()) {
        PinPad("관리자 PIN 6자리를 정하세요", "정책과 이력은 이 PIN 을 아는 사람만 볼 수 있어요. 잊으면 복구할 수 없어요.", error = error) { pin ->
            first = pin
            error = null
        }
    } else {
        PinPad("한 번 더 입력하세요", error = error) { pin ->
            if (pin == first) {
                pinStore.set(pin)
                onDone()
            } else {
                first = ""
                error = "두 번 입력한 PIN 이 달라요. 다시 정해 주세요."
            }
        }
    }
}

@Composable
private fun StepAccessibility(resumeTick: Int) {
    val context = LocalContext.current
    val enabled = remember(resumeTick) { Permissions.isAccessibilityEnabled(context) }
    StepBody(
        "접근성 서비스를 켜 주세요",
        "어떤 앱이 화면에 올라오는지 알기 위한 유일한 권한이에요. 화면 내용은 읽지 않아요.",
    ) {
        CheckRow(ok = enabled, label = if (enabled) "켜져 있어요" else "아직 꺼져 있어요")
        SectionCard("순서") {
            Text("1. 아래 [앱 정보 열기] → 오른쪽 위 ⋮ → '제한된 설정 허용'\n    (스토어 밖에서 설치한 앱은 이걸 먼저 해야 접근성 토글이 풀려요. 토글이 회색이면 이 단계예요.)")
            Text("2. 아래 [접근성 설정 열기] → 설치된 앱 → 우리집 소프트가드 → 켜기")
            Text("3. 이 앱으로 돌아오면 위 표시가 초록색으로 바뀌어요")
        }
        OutlinedButton(onClick = { Permissions.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) { Text("1. 앱 정보 열기 (제한된 설정 허용)") }
        Button(onClick = { Permissions.openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("2. 접근성 설정 열기") }
    }
}

@Composable
private fun StepBattery(resumeTick: Int) {
    val context = LocalContext.current
    val ignoring = remember(resumeTick) { Permissions.isIgnoringBatteryOptimizations(context) }
    StepBody(
        "절전 기능이 끄지 않게 해 주세요",
        "삼성 기기는 오래 안 쓴 앱을 잠들게 해요. 그러면 차단도 기록도 멈춰요. 세 가지를 해 두면 안전해요.",
    ) {
        CheckRow(
            ok = ignoring,
            label = "배터리 최적화 제외",
            detail = "대화상자가 뜨면 '허용'",
            actionLabel = "요청",
        ) { Permissions.requestIgnoreBatteryOptimizations(context) }
        SectionCard("설정 앱에서 직접") {
            Text("• 앱 정보 → 배터리 → '제한 없음'")
            Text("• 설정 → 배터리 → 백그라운드 사용 제한 → '절전 모드로 전환하지 않을 앱' 에 추가")
            Text("• 설정 → 디바이스 케어 → ⋮ → 자동 최적화가 켜져 있으면 재부팅 후 앱 상태를 한 번 확인")
        }
        OutlinedButton(onClick = { Permissions.openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) { Text("앱 정보 열기") }
        OutlinedButton(onClick = { Permissions.openBatterySaverSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("배터리 설정 열기") }
    }
}

@Composable
private fun StepAlarmNotification(resumeTick: Int) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val exact = remember(resumeTick, tick) { Permissions.canScheduleExactAlarms(context) }
    val notif = remember(resumeTick, tick) { Permissions.notificationsEnabled(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    StepBody(
        "정각에 맞추고, 문제가 생기면 알려 드릴게요",
        "둘 다 없어도 동작하지만, 있으면 더 정확하고 안심돼요.",
    ) {
        CheckRow(
            ok = exact,
            label = "정확한 알람",
            detail = "허용 시간이 끝나는 정각에 바로 닫아요. 없으면 몇 분 늦을 수 있어요.",
            actionLabel = "설정",
        ) { Permissions.openExactAlarmSettings(context) }
        CheckRow(
            ok = notif,
            label = "알림",
            detail = "누가 접근성을 끄면 관리자에게 알려요.",
            actionLabel = if (notif) null else "허용",
        ) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

@Composable
private fun StepDone() {
    StepBody(
        "준비가 끝났어요",
        "이제 차단 목록의 앱은 정한 시간에만 열려요. 언제든 [상태] 탭에서 동작 여부를 확인하고, [이력] 탭에서 날짜별 기록을 볼 수 있어요.",
    ) {
        SectionCard("잊지 마세요") {
            Text("• 앱을 다시 열 때는 PIN 이 필요해요")
            Text("• [상태] 탭 맨 위가 '제어 동작 중'이어야 정상이에요")
            Text("• 이 기기를 쓰는 가족에게 실행 기록이 남는다는 걸 알려 주세요")
        }
    }
}
