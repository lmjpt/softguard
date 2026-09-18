package kr.woorijip.softguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.woorijip.softguard.data.PinStore

/**
 * 자체 숫자 키패드 (설계문서 §6). 시스템 키보드를 쓰지 않는다.
 * 6자리가 채워지면 onComplete 를 부르고 입력을 비운다.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    onComplete: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PinStore.PIN_LENGTH) { i ->
                val filled = i < pin.length
                Box(
                    Modifier
                        .size(16.dp)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            error ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { key ->
                    Key(key, enabled = enabled && key.isNotEmpty()) {
                        when (key) {
                            "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                            "" -> Unit
                            else -> if (pin.length < PinStore.PIN_LENGTH) {
                                val next = pin + key
                                if (next.length == PinStore.PIN_LENGTH) {
                                    pin = ""
                                    onComplete(next)
                                } else {
                                    pin = next
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, onClick: () -> Unit) {
    if (label.isEmpty()) {
        Spacer(Modifier.size(76.dp))
        return
    }
    Surface(
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(76.dp).clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** 앱 진입 잠금 화면. 실패 백오프 중이면 남은 시간을 보여 준다. */
@Composable
fun PinLockScreen(pinStore: PinStore, onUnlock: () -> Unit) {
    val state by pinStore.state.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.lockedUntil) {
        while (System.currentTimeMillis() < state.lockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }

    val remaining = state.lockedUntil - now
    val locked = remaining > 0
    PinPad(
        title = "PIN 을 입력하세요",
        subtitle = if (locked) "잠시 후 다시 시도하세요 (${remaining / 1000 + 1}초)" else "관리자만 들어갈 수 있어요",
        error = error,
        enabled = !locked,
    ) { pin ->
        when (val r = pinStore.verify(pin)) {
            PinStore.VerifyResult.Ok -> {
                error = null
                onUnlock()
            }
            is PinStore.VerifyResult.Wrong -> {
                error = "PIN 이 틀렸어요 (${r.failCount}회)" +
                    if (r.lockedForMillis > 0) " · ${r.lockedForMillis / 1000}초 동안 잠겨요" else ""
            }
            is PinStore.VerifyResult.Locked -> error = "잠시 후 다시 시도하세요"
        }
    }
}
