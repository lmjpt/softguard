package kr.woorijip.softguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.woorijip.softguard.ui.theme.StatusColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** 점검 항목 한 줄: 아이콘(정상/주의) + 이름 + 설명 + 버튼 */
@Composable
fun CheckRow(
    ok: Boolean,
    label: String,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (ok) "정상" else "확인 필요",
            tint = if (ok) StatusColors.Ok else StatusColors.Warn,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

object Fmt {
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
    private val HHMMSS = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun hhmm(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(HHMM)
    fun hhmmss(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(HHMMSS)
    fun hhmm(t: LocalDateTime): String = t.format(HHMM)

    fun dateLabel(d: LocalDate): String =
        "${d.monthValue}월 ${d.dayOfMonth}일 (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

    fun duration(millis: Long): String {
        val totalMin = millis / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}시간 ${m}분"
            h > 0 -> "${h}시간"
            m > 0 -> "${m}분"
            else -> "${millis / 1000}초"
        }
    }

    private val SHORT_KO = mapOf(
        DayOfWeek.MONDAY to "월", DayOfWeek.TUESDAY to "화", DayOfWeek.WEDNESDAY to "수", DayOfWeek.THURSDAY to "목",
        DayOfWeek.FRIDAY to "금", DayOfWeek.SATURDAY to "토", DayOfWeek.SUNDAY to "일",
    )

    fun dayShort(d: DayOfWeek): String = SHORT_KO.getValue(d)

    val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val EVERYDAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()

    fun daysText(days: Set<DayOfWeek>): String = when (days) {
        EVERYDAY -> "매일"
        WEEKDAYS -> "평일"
        WEEKEND -> "주말"
        else -> days.sortedBy { it.value }.joinToString("·") { dayShort(it) }
    }
}
