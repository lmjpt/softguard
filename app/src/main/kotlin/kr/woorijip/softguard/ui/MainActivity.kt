package kr.woorijip.softguard.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.SoftGuardApp
import kr.woorijip.softguard.ui.theme.SoftGuardTheme

/**
 * 유일한 액티비티. 화면 전환은 Compose 상태로 한다.
 *   - FLAG_SECURE: 스크린샷·최근 앱 미리보기 차단 (이력 화면 포함)
 *   - 앱을 떠난 뒤 30초가 지나면 다시 PIN 을 묻는다. 설정 화면을 오가는 동안 매번 묻지 않기 위한 여유다.
 */
class MainActivity : ComponentActivity() {

    private val locked = mutableStateOf(true)
    private val resumeTick = mutableIntStateOf(0)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val graph = SoftGuardApp.graph(this)
        setContent {
            SoftGuardTheme {
                AppRoot(
                    graph = graph,
                    locked = locked.value,
                    resumeTick = resumeTick.intValue,
                    onUnlock = { locked.value = false },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > RELOCK_GRACE_MS) {
            locked.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    private companion object {
        const val RELOCK_GRACE_MS = 30_000L
    }
}

@Composable
private fun AppRoot(graph: Graph, locked: Boolean, resumeTick: Int, onUnlock: () -> Unit) {
    val pinState by graph.pinStore.state.collectAsState()
    val setupDone by graph.prefs.setupDoneFlow.collectAsState()
    when {
        !pinState.isSet || !setupDone -> SetupWizard(
            graph = graph,
            resumeTick = resumeTick,
            onFinished = {
                graph.prefs.setupDone = true
                onUnlock()
            },
        )
        locked -> PinLockScreen(graph.pinStore, onUnlock)
        else -> MainScreen(graph, resumeTick)
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    STATUS("상태", Icons.Default.Home),
    BLOCK("차단 앱", Icons.Default.Lock),
    SCHEDULE("시간대", Icons.Default.DateRange),
    HISTORY("이력", Icons.AutoMirrored.Filled.List),
    SETTINGS("설정", Icons.Default.Settings),
}

@Composable
private fun MainScreen(graph: Graph, resumeTick: Int) {
    var tab by rememberSaveable { mutableStateOf(Tab.STATUS) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.STATUS -> DashboardScreen(graph, resumeTick)
                Tab.BLOCK -> BlockListScreen(graph, resumeTick)
                Tab.SCHEDULE -> ScheduleScreen(graph)
                Tab.HISTORY -> HistoryScreen(graph)
                Tab.SETTINGS -> SettingsScreen(graph)
            }
        }
    }
}
