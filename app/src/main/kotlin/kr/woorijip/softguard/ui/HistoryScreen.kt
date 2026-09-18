@file:OptIn(ExperimentalMaterial3Api::class)

package kr.woorijip.softguard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.core.history.DayKey
import kr.woorijip.softguard.core.history.LogEvent
import kr.woorijip.softguard.core.history.LogType
import kr.woorijip.softguard.core.history.Timeline
import kr.woorijip.softguard.core.history.TimelineItem
import kr.woorijip.softguard.core.policy.BlockReason
import kr.woorijip.softguard.data.AppCatalog
import kr.woorijip.softguard.service.LogPruneWorker
import kr.woorijip.softguard.ui.theme.StatusColors
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_MS = 24L * 60 * 60 * 1000

/** 날짜별 실행 이력 + 제어 중단 공백 블록 (설계문서 §8.6, §8.7). */
@Composable
fun HistoryScreen(graph: Graph) {
    val zone = graph.zone
    val dao = graph.db.eventLogDao()
    val catalog = graph.appCatalog
    val today = remember { LocalDate.now(zone) }
    val earliest = remember { today.minusDays((LogPruneWorker.RETENTION_DAYS - 1).toLong()) }

    var dayKey by rememberSaveable { mutableIntStateOf(DayKey.of(today)) }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    var pkgFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val date = DayKey.toDate(dayKey)

    val rows by remember(dayKey) { dao.observeDay(dayKey) }.collectAsState(initial = emptyList())
    val dayStart = DayKey.startOfDayMillis(dayKey, zone)
    val dayEnd = DayKey.endOfDayMillis(dayKey, zone)
    val openGap by produceState<LogEvent?>(initialValue = null, key1 = dayKey, key2 = rows.size) {
        value = withContext(Dispatchers.IO) {
            dao.lastGuardEventBefore(dayStart)?.toLogEvent()?.takeIf { it.type == LogType.GUARD_STOPPED }
        }
    }
    val timeline = remember(rows, openGap) {
        Timeline.build(rows.map { it.toLogEvent() }, dayStart, dayEnd, openGap, System.currentTimeMillis())
    }
    val launches = rows.count { it.type == LogType.APP_LAUNCH.name }
    val blockedCount = rows.count { it.type == LogType.APP_LAUNCH.name && it.blocked }
    val shown = timeline.filter { item ->
        when (item) {
            is TimelineItem.Launch ->
                (!blockedOnly || item.event.blocked) && (pkgFilter == null || item.event.packageName == pkgFilter)
            else -> pkgFilter == null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { dayKey = DayKey.of(date.minusDays(1)) }, enabled = date > earliest) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "전날")
            }
            TextButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                Text(if (date == today) "오늘 · ${Fmt.dateLabel(date)}" else Fmt.dateLabel(date), style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { dayKey = DayKey.of(date.plusDays(1)) }, enabled = date < today) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "다음 날")
            }
        }
        Text(
            "실행 ${launches}건 · 차단 ${blockedCount}건",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !blockedOnly && pkgFilter == null, onClick = { blockedOnly = false; pkgFilter = null }, label = { Text("전체") })
            FilterChip(selected = blockedOnly, onClick = { blockedOnly = !blockedOnly }, label = { Text("차단만") })
            pkgFilter?.let { p ->
                FilterChip(
                    selected = true,
                    onClick = { pkgFilter = null },
                    label = { Text(catalog.labelOf(p)) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "해제", Modifier.size(16.dp)) },
                )
            }
        }
        if (pkgFilter == null) {
            Text(
                "앱을 누르면 그 앱만 볼 수 있어요",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("이 날은 기록이 없어요", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown) { item ->
                    when (item) {
                        is TimelineItem.Launch -> LaunchRow(item.event, catalog, zone) { pkgFilter = it }
                        is TimelineItem.Gap -> GapCard(item, zone)
                        is TimelineItem.Boot -> Text(
                            "${Fmt.hhmm(item.at, zone)}  기기 부팅",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        val minMillis = earliest.toEpochDay() * DAY_MS
        val maxMillis = today.toEpochDay() * DAY_MS
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in minMillis..maxMillis
                override fun isSelectableYear(year: Int) = year in earliest.year..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dayKey = DayKey.of(LocalDate.ofEpochDay(it / DAY_MS)) }
                    showPicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("취소") } },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun LaunchRow(event: LogEvent, catalog: AppCatalog, zone: ZoneId, onPick: (String) -> Unit) {
    val pkg = event.packageName ?: return
    Row(
        Modifier.fillMaxWidth().clickable { onPick(pkg) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(pkg, catalog, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.appLabel ?: pkg, style = MaterialTheme.typography.bodyLarge)
            Text(Fmt.hhmm(event.occurredAt, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (event.blocked) {
            val outOfHours = event.blockReason == BlockReason.OUT_OF_HOURS.name
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (outOfHours) StatusColors.Warn.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    if (outOfHours) "시간 외" else "항상 차단",
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun GapCard(gap: TimelineItem.Gap, zone: ZoneId) {
    val what = when {
        gap.rebooted -> "기기가 꺼져 있었습니다"
        gap.inferred -> "제어가 중단되었습니다 (종료 시각은 추정)"
        else -> "제어가 꺼져 있었습니다"
    }
    val range = buildString {
        append(if (gap.startsBeforeDay) "전날부터" else Fmt.hhmm(gap.from, zone))
        append(" ~ ")
        append(
            when {
                gap.ongoing -> "지금"
                gap.endsAfterDay -> "다음 날까지"
                else -> Fmt.hhmm(gap.to, zone)
            },
        )
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = StatusColors.Warn.copy(alpha = 0.15f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusColors.Warn, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("기록 공백 · ${Fmt.duration(gap.to - gap.from)}", style = MaterialTheme.typography.titleSmall)
            }
            Text("$range  $what", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
