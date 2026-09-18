package kr.woorijip.softguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 상태 표시에 쓰는 두 색. 초록 = 정상, 호박색 = 주의. 빨강은 쓰지 않는다. */
object StatusColors {
    val Ok = Color(0xFF2E7D32)
    val Warn = Color(0xFFF9A825)
}

@Composable
fun SoftGuardTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF9DB8FF), secondary = Color(0xFFB8C4E0))
    } else {
        lightColorScheme(primary = Color(0xFF2F5BEA), secondary = Color(0xFF5A6B8C))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
