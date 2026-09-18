@file:OptIn(ExperimentalMaterial3Api::class)

package kr.woorijip.softguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.core.model.AllowWindow
import kr.woorijip.softguard.core.model.TimeOfDay
import kr.woorijip.softguard.core.policy.WeeklySchedule
import java.time.DayOfWeek

private sealed interface EditTarget {
    data object New : EditTarget
    data class Existing(val index: Int) : EditTarget
}

/** 공통 허용 시간대 편집 (설계문서 §7.2, §9). 비어 있으면 차단 앱은 항상 차단된다. */
@Composable
fun ScheduleScreen(graph: Graph, modifier: Modifier = Modifier) {
    val state by graph.policies.state.collectAsState()
    val windows = state.policy.allowWindows
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("차단 앱을 열어 줄 시간", style = MaterialTheme.typography.titleLarge)
        Text("차단 목록의 모든 앱에 같은 시간대가 적용돼요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (windows.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(
                    "시간대가 없어요. 차단 목록의 앱은 항상 차단돼요.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            WeekPreview(WeeklySchedule.of(windows))
            windows.forEachIndexed { i, w ->
                WindowCard(
                    window = w,
                    onEdit = { editing = EditTarget.Existing(i) },
                    onDelete = {
                        graph.policies.update { p -> p.copy(allowWindows = p.allowWindows.filterIndexed { j, _ -> j != i }) }
                    },
                )
            }
        }

        Button(onClick = { editing = EditTarget.New }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("시간대 추가")
        }
    }

    editing?.let { target ->
        WindowEditorDialog(
            initial = (target as? EditTarget.Existing)?.let { windows.getOrNull(it.index) },
            onDismiss = { editing = null },
            onSave = { w ->
                graph.policies.update { p ->
                    val list = p.allowWindows.toMutableList()
                    if (target is EditTarget.Existing && target.index in list.indices) list[target.index] = w else list += w
                    p.copy(allowWindows = list)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun WindowCard(window: AllowWindow, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.daysText(window.days), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${window.from} ~ ${window.to}" + if (window.crossesMidnight) " (다음 날)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "수정") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
        }
    }
}

/** 한 주 미리보기. 요일마다 한 줄, 허용 구간을 색으로 칠한다. */
@Composable
private fun WeekPreview(schedule: WeeklySchedule) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { day ->
                val dayStart = (day.value - 1) * WeeklySchedule.MINUTES_PER_DAY
                val dayEnd = dayStart + WeeklySchedule.MINUTES_PER_DAY
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.dayShort(day), Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium)
                    Canvas(Modifier.weight(1f).height(14.dp)) {
                        drawRoundRect(track, cornerRadius = CornerRadius(4.dp.toPx()))
                        for (iv in schedule.intervals) {
                            val s = maxOf(iv.start, dayStart)
                            val e = minOf(iv.end, dayEnd)
                            if (e <= s) continue
                            val x0 = size.width * (s - dayStart) / WeeklySchedule.MINUTES_PER_DAY
                            val x1 = size.width * (e - dayStart) / WeeklySchedule.MINUTES_PER_DAY
                            drawRoundRect(
                                fill,
                                topLeft = Offset(x0, 0f),
                                size = Size(x1 - x0, size.height),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0", "6", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(38.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                Fmt.dayShort(day),
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun WindowEditorDialog(initial: AllowWindow?, onDismiss: () -> Unit, onSave: (AllowWindow) -> Unit) {
    var days by remember { mutableStateOf(initial?.days ?: Fmt.WEEKDAYS) }
    val fromState = rememberTimePickerState(
        initialHour = initial?.from?.hour ?: 19,
        initialMinute = initial?.from?.minute ?: 0,
        is24Hour = true,
    )
    val initialTo = initial?.to?.takeIf { it != TimeOfDay.END_OF_DAY }
    val toState = rememberTimePickerState(
        initialHour = initialTo?.hour ?: 20,
        initialMinute = initialTo?.minute ?: 0,
        is24Hour = true,
    )
    var toMidnight by remember { mutableStateOf(initial?.to == TimeOfDay.END_OF_DAY) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "시간대 추가" else "시간대 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("요일", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = days == Fmt.WEEKDAYS, onClick = { days = Fmt.WEEKDAYS }, label = { Text("평일") })
                    FilterChip(selected = days == Fmt.WEEKEND, onClick = { days = Fmt.WEEKEND }, label = { Text("주말") })
                    FilterChip(selected = days == Fmt.EVERYDAY, onClick = { days = Fmt.EVERYDAY }, label = { Text("매일") })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DayOfWeek.entries.forEach { d ->
                        val sel = d in days
                        DayToggle(d, sel) { days = if (sel) days - d else days + d }
                    }
                }
                Text("시작", style = MaterialTheme.typography.labelLarge)
                TimeInput(state = fromState)
                Text("종료", style = MaterialTheme.typography.labelLarge)
                if (!toMidnight) TimeInput(state = toState)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = toMidnight, onCheckedChange = { toMidnight = it })
                    Text("자정(24:00)까지")
                }
                Text(
                    "종료가 시작보다 이르면 다음 날 새벽까지로 봐요 (예: 22:00 ~ 01:00)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = AllowWindow(
                    days = days,
                    from = TimeOfDay.of(fromState.hour, fromState.minute),
                    to = if (toMidnight) TimeOfDay.END_OF_DAY else TimeOfDay.of(toState.hour, toState.minute),
                )
                val problem = w.validate()
                if (problem != null) error = problem else onSave(w)
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
