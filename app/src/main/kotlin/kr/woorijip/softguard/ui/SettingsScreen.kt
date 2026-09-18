package kr.woorijip.softguard.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.core.storage.DecodeResult
import kr.woorijip.softguard.core.storage.PolicyCodec
import kr.woorijip.softguard.data.PinStore

private enum class Sub { NONE, CHANGE_PIN, IMPORT, INFO, DIAG }

@Composable
fun SettingsScreen(graph: Graph) {
    var sub by rememberSaveable { mutableStateOf(Sub.NONE) }
    when (sub) {
        Sub.CHANGE_PIN -> ChangePinFlow(graph.pinStore) { sub = Sub.NONE }
        Sub.IMPORT -> ImportScreen(graph) { sub = Sub.NONE }
        Sub.INFO -> InfoScreen { sub = Sub.NONE }
        Sub.DIAG -> DiagScreen(graph) { sub = Sub.NONE }
        Sub.NONE -> SettingsList(graph) { sub = it }
    }
}

@Composable
private fun SettingsList(graph: Graph, open: (Sub) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val total by remember { graph.db.eventLogDao().observeTotalCount() }.collectAsState(initial = 0)
    var confirmDelete by remember { mutableStateOf(false) }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineSmall)
        Card {
            SettingsItem("PIN 변경", "숫자 6자리") { open(Sub.CHANGE_PIN) }
            HorizontalDivider()
            SettingsItem("정책 내보내기", "차단 목록과 시간대를 텍스트로 공유") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "우리집 소프트가드 정책")
                    putExtra(Intent.EXTRA_TEXT, graph.policies.exportText())
                }
                context.startActivity(Intent.createChooser(send, "정책 내보내기"))
            }
            HorizontalDivider()
            SettingsItem("정책 가져오기", "내보낸 텍스트를 붙여 넣어 적용") { open(Sub.IMPORT) }
        }
        Card {
            SettingsItem("이력 전체 삭제", "${total}건 · 되돌릴 수 없어요") { confirmDelete = true }
            HorizontalDivider()
            SettingsItem("설정 마법사 다시 보기", "권한 → 차단 앱 → 시간대를 처음부터") { graph.prefs.setupDone = false }
        }
        Card {
            SettingsItem("감지 로그 (진단)", "서비스가 무엇을 봤는지 · 메모리에만 남아요") { open(Sub.DIAG) }
            HorizontalDivider()
            SettingsItem("정보", "무엇을 기록하나, 무엇을 막지 못하나") { open(Sub.INFO) }
        }
        Text("우리집 소프트가드 $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이력을 모두 지울까요?") },
            text = { Text("${total}건의 실행 이력과 제어 중단 기록이 사라져요. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch(Dispatchers.IO) {
                        graph.db.eventLogDao().deleteAll()
                        graph.diag.log("이력 전체 삭제")
                    }
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** 기존 PIN 확인 → 새 PIN → 한 번 더 */
@Composable
private fun ChangePinFlow(pinStore: PinStore, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        SubHeader("PIN 변경", onDone)
        when (step) {
            0 -> PinPad("지금 쓰는 PIN 을 입력하세요", error = error) { pin ->
                when (val r = pinStore.verify(pin)) {
                    PinStore.VerifyResult.Ok -> { error = null; step = 1 }
                    is PinStore.VerifyResult.Wrong -> error = "PIN 이 틀렸어요 (${r.failCount}회)"
                    is PinStore.VerifyResult.Locked -> error = "잠시 후 다시 시도하세요"
                }
            }
            1 -> PinPad("새 PIN 6자리를 정하세요", error = error) { pin ->
                first = pin
                error = null
                step = 2
            }
            else -> PinPad("새 PIN 을 한 번 더 입력하세요", error = error) { pin ->
                if (pin == first) {
                    pinStore.set(pin)
                    onDone()
                } else {
                    error = "두 번 입력한 PIN 이 달라요. 처음부터 다시 정해 주세요."
                    first = ""
                    step = 1
                }
            }
        }
    }
}

@Composable
private fun ImportScreen(graph: Graph, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val preview = remember(text) { if (text.isBlank()) null else PolicyCodec.decode(text) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("정책 가져오기", onDone)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("'정책 내보내기'로 받은 텍스트를 그대로 붙여 넣으세요. 지금 정책은 덮어써요.")
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                placeholder = { Text("{ \"version\": 3, ... }") },
            )
            when (preview) {
                null -> Unit
                is DecodeResult.Failed -> Text(preview.message, color = MaterialTheme.colorScheme.error)
                is DecodeResult.Ok -> {
                    Text("차단 앱 ${preview.policy.blockedPackages.size}개 · 시간대 ${preview.policy.allowWindows.size}개")
                    Button(onClick = {
                        graph.policies.importText(text)
                        onDone()
                    }) { Text("이 정책으로 바꾸기") }
                }
            }
        }
    }
}

@Composable
private fun InfoScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("정보", onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("이 앱이 하는 일", style = MaterialTheme.typography.titleMedium)
            Text("차단 목록에 넣은 앱을 정해진 시간대에만 열어 줍니다. 목록에 없는 앱은 항상 자유롭게 쓸 수 있습니다.")
            Text("무엇을 기록하나", style = MaterialTheme.typography.titleMedium)
            Text("앱 이름 · 실행 시각 · 차단 여부, 그리고 제어가 켜지고 꺼진 시각만 기록합니다. 화면 내용, 입력한 글자, 메시지, 방문한 주소는 읽지도 저장하지도 않습니다. 기록은 이 기기 안에만 180일 남고, 어디로도 보내지 않습니다. 이 기기를 쓰는 가족에게 기록이 남는다는 점을 알려 주세요.")
            Text("막지 못하는 것", style = MaterialTheme.typography.titleMedium)
            Text("• 설정 → 접근성에서 이 앱을 끄는 것 (다만 꺼진 구간은 이력에 '기록 공백'으로 남습니다)\n• 앱을 삭제하는 것 (이력도 함께 사라집니다)\n• 기기 시각을 바꿔 허용 시간을 만드는 것\n• 새로 설치한 앱 (자동으로 허용됩니다 — 스토어를 차단 목록에 넣어 두세요)\n• 브라우저로 같은 서비스에 들어가는 것 (브라우저를 차단 목록에 넣어 두세요)")
            Text("이것은 결함이 아니라 선택입니다. 우회를 막는 도구가 아니라, 정해둔 규칙을 기기가 지켜 주는 도구입니다.")
            Text("PIN 을 잊었다면", style = MaterialTheme.typography.titleMedium)
            Text("서버가 없어 복구할 방법이 없습니다. 앱을 삭제하고 다시 설치하세요 (정책과 이력이 초기화됩니다).")
        }
    }
}

@Composable
private fun DiagScreen(graph: Graph, onBack: () -> Unit) {
    val lines by graph.diag.lines.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SubHeader("감지 로그", onBack)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${lines.size}줄 · 메모리에만 남아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { graph.diag.clear() }) { Text("지우기") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines) { line ->
                Text(
                    line,
                    Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
