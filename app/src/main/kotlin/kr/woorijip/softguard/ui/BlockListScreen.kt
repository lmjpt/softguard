package kr.woorijip.softguard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.woorijip.softguard.Graph
import kr.woorijip.softguard.data.AppCatalog

/**
 * 차단할 앱 고르기 (설계문서 §7.1, §9).
 *   - 자기 자신·기본 홈·시스템UI·설정 앱은 목록에서 아예 빠진다 (되돌릴 수 없는 선택은 못 하게)
 *   - 그 외 시스템 앱은 '(시스템)' 표시 + 켤 때 확인
 *   - 설치돼 있고 아직 차단하지 않은 추천 앱(스토어·브라우저)을 위에 보여 준다
 */
@Composable
fun BlockListScreen(graph: Graph, resumeTick: Int = 0, modifier: Modifier = Modifier) {
    val policyState by graph.policies.state.collectAsState()
    val policy = policyState.policy
    val catalog = graph.appCatalog
    var query by rememberSaveable { mutableStateOf("") }
    var confirmApp by remember { mutableStateOf<AppCatalog.AppInfo?>(null) }

    val apps by produceState<List<AppCatalog.AppInfo>?>(initialValue = null, key1 = resumeTick) {
        value = withContext(Dispatchers.IO) {
            catalog.apps().filterNot { catalog.isExcludedFromBlockList(it.packageName) }
        }
    }

    fun setBlocked(pkg: String, blocked: Boolean) {
        graph.policies.update { it.withBlocked(pkg, blocked) }
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("앱 이름 검색") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val filtered = if (query.isBlank()) list
        else list.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        val recommended = if (query.isBlank()) {
            catalog.recommended.filter { (pkg, _) -> pkg !in policy.blockedPackages && list.any { it.packageName == pkg } }
        } else emptyList()

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "${policy.blockedPackages.size}개 차단 중 · 목록에 없는 앱은 항상 열려요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (recommended.isNotEmpty()) {
                item {
                    SectionCard("차단을 추천하는 앱", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        recommended.forEach { (pkg, why) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(catalog.labelOf(pkg), style = MaterialTheme.typography.bodyLarge)
                                    Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { setBlocked(pkg, true) }) { Text("차단") }
                            }
                        }
                    }
                }
            }
            items(filtered, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in policy.blockedPackages,
                    catalog = catalog,
                ) { on ->
                    if (on && app.isSystem) confirmApp = app else setBlocked(app.packageName, on)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "찾는 앱이 없어요",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    confirmApp?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmApp = null },
            title = { Text("시스템 앱을 차단할까요?") },
            text = { Text("'${app.label}' 은 기기에 처음부터 들어 있던 앱이에요. 차단하면 기기의 다른 기능이 함께 막힐 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    setBlocked(app.packageName, true)
                    confirmApp = null
                }) { Text("차단") }
            },
            dismissButton = { TextButton(onClick = { confirmApp = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun AppRow(app: AppCatalog.AppInfo, checked: Boolean, catalog: AppCatalog, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, catalog, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (app.isSystem) "${app.packageName} · 시스템" else app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 앱 아이콘. PackageManager 의 Drawable 을 비트맵으로 바꿔 그린다. 없으면 회색 네모. */
@Composable
fun AppIcon(packageName: String, catalog: AppCatalog, size: Dp) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                catalog.iconOf(packageName)?.toBitmap(96, 96)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
    }
}
