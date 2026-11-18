package com.avalon.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshots.SnapshotStateMap

internal data class ProbeUiState(
    val running: Boolean,
    val result: ProbeResult? = null,
)

/**
 * 网站测试状态板：按站点 id 保存「进行中 / 结果」。
 * - 同一行测试中禁止重复发起；
 * - 编辑/删除立即 invalidate；旧请求完成后校验 token + 当前 url，
 *   只有过期判定通过才写回，绝不把旧结果挂到改过的网址上；
 * - 协程取消（Activity 销毁等）时清掉状态再抛出，任何路径都不会留下永久“测试中”。
 */
internal class WebsiteProbeBoard {
    val states: SnapshotStateMap<String, ProbeUiState> = mutableStateMapOf()
    private val tokens = mutableMapOf<String, Any>()

    fun invalidate(id: String) {
        tokens.remove(id)
        states.remove(id)
    }

    fun start(scope: CoroutineScope, store: AvalonStore, site: WebsiteRecord) {
        if (states[site.id]?.running == true) return
        val token = Any()
        tokens[site.id] = token
        states[site.id] = ProbeUiState(running = true)
        scope.launch {
            try {
                val result = probeWebsite(site.url)
                if (tokens[site.id] !== token) return@launch
                val unchanged = store.websites.firstOrNull { it.id == site.id }?.url == site.url
                if (unchanged) {
                    states[site.id] = ProbeUiState(running = false, result = result)
                } else {
                    invalidate(site.id)
                }
            } catch (cancelled: CancellationException) {
                if (tokens[site.id] === token) invalidate(site.id)
                throw cancelled
            } catch (failure: Throwable) {
                // probeWebsite 之外的意外异常也必须收敛为可见结果，避免永久忙碌。
                if (tokens[site.id] === token) {
                    states[site.id] = ProbeUiState(
                        running = false,
                        result = ProbeResult(
                            elapsedMs = 0L,
                            httpCode = null,
                            error = "测试出错：${failure.message ?: failure.javaClass.simpleName}",
                        ),
                    )
                }
            }
        }
    }

    fun testAll(scope: CoroutineScope, store: AvalonStore) {
        store.websites.forEach { site ->
            if (states[site.id]?.running != true) start(scope, store, site)
        }
    }
}

/** 网站连接页：书签列表、逐站与整页测试、外链打开、增改删。 */
@Composable
internal fun WebsitesScreen(
    store: AvalonStore,
    board: WebsiteProbeBoard,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    showAdd: Boolean,
    onDismissAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 保存 id 而非对象：旋转重建后从 store 重新解析，对话框与输入不丢。
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> store.websites.firstOrNull { it.id == id } }
    val pendingDelete = pendingDeleteId?.let { id -> store.websites.firstOrNull { it.id == id } }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "网站连接",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "共 ${store.websites.size} 个站点",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TextButton(
                    onClick = { board.testAll(scope, store) },
                    enabled = store.websites.isNotEmpty(),
                ) {
                    Icon(AvalonIcons.Pulse, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("全部测试")
                }
            }
            Text(
                "测试耗时为请求到响应头的时间，不代表视频播放速度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (store.websites.isEmpty()) {
            WebsitesEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(store.websites, key = { it.id }) { site ->
                    WebsiteRow(
                        site = site,
                        state = board.states[site.id],
                        onTest = { board.start(scope, store, site) },
                        onOpen = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site.url)))
                            } catch (e: ActivityNotFoundException) {
                                scope.launch { snackbarHostState.showSnackbar("未找到可以打开网页的浏览器") }
                            } catch (e: SecurityException) {
                                scope.launch { snackbarHostState.showSnackbar("无法打开该链接") }
                            }
                        },
                        onEdit = { editingId = site.id },
                        onDelete = { pendingDeleteId = site.id },
                    )
                }
            }
        }
    }

    if (showAdd) {
        WebsiteEditDialog(
            record = null,
            onDismiss = onDismissAdd,
            onSave = { name, url ->
                try {
                    store.saveWebsite(existingId = null, name = name, url = url)
                    onDismissAdd()
                } catch (e: StorageWriteException) {
                    // 保存失败保持对话框打开，输入不丢。
                    scope.launch { snackbarHostState.showStorageFailure(e) }
                }
            },
        )
    }
    editing?.let { record ->
        WebsiteEditDialog(
            record = record,
            onDismiss = { editingId = null },
            onSave = { name, url ->
                try {
                    store.saveWebsite(existingId = record.id, name = name, url = url)
                    board.invalidate(record.id)
                    editingId = null
                } catch (e: StorageWriteException) {
                    scope.launch { snackbarHostState.showStorageFailure(e) }
                }
            },
        )
    }
    pendingDelete?.let { site ->
        AvalonDialog(
            title = "删除这个网站？",
            confirmLabel = "删除",
            onConfirm = {
                try {
                    store.deleteWebsite(site.id)
                    board.invalidate(site.id)
                    pendingDeleteId = null
                } catch (e: StorageWriteException) {
                    scope.launch { snackbarHostState.showStorageFailure(e) }
                }
            },
            onDismiss = { pendingDeleteId = null },
        ) {
            Text(
                "「${site.name}」将被移除，操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WebsiteRow(
    site: WebsiteRecord,
    state: ProbeUiState?,
    onTest: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val running = state?.running == true
    // 预览的网页藏牌：纸金底、外紫内金双细描边、低圆角。
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = ChampagneGold),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(2.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                site.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            SelectionContainer(Modifier.padding(top = 2.dp)) {
                Text(
                    site.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.76f),
                )
            }
            Box(Modifier.padding(top = 8.dp)) {
                ProbeResultLine(state)
            }
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(
                    onClick = onTest,
                    enabled = !running,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                ) {
                    Icon(AvalonIcons.Pulse, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "测试中" else "测试")
                }
                TextButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                ) {
                    Icon(AvalonIcons.Open, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开")
                }
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                ) {
                    Text("编辑")
                }
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun ProbeResultLine(state: ProbeUiState?) {
    when {
        state == null || (!state.running && state.result == null) -> Text(
            "尚未测试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.76f),
        )
        state.running -> Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.inversePrimary)
            Spacer(Modifier.width(10.dp))
            Text(
                "测试中…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
        else -> {
            val result = state.result ?: return
            val code = result.httpCode
            when {
                result.error != null -> Column {
                    ResultChip(
                        text = "网络错误 · ${result.elapsedMs} ms",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (!result.error.isNullOrBlank()) {
                        Text(
                            result.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                code != null && code in 200..299 -> ResultChip(
                    text = "成功 · HTTP $code · ${result.elapsedMs} ms",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                code != null -> Column {
                    ResultChip(
                        text = "HTTP $code · ${result.elapsedMs} ms",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        "网站有响应，但返回了错误状态码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.76f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                else -> Text(
                    "尚未测试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.76f),
                )
            }
        }
    }
}

@Composable
private fun ResultChip(text: String, container: Color, content: Color) {
    // 低圆角 + 同色细描边：结果徽章落在纸金牌面上仍有清晰边界。
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = container,
        border = BorderStroke(1.dp, content.copy(alpha = 0.5f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun WebsiteEditDialog(
    record: WebsiteRecord?,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit,
) {
    var name by rememberSaveable(record?.id) { mutableStateOf(record?.name ?: "") }
    var url by rememberSaveable(record?.id) { mutableStateOf(record?.url ?: "") }
    var urlError by rememberSaveable(record?.id) { mutableStateOf<String?>(null) }

    AvalonDialog(
        title = if (record == null) "添加网站" else "编辑网站",
        confirmLabel = "保存",
        onConfirm = {
            try {
                val normalized = normalizeWebsiteUrl(url)
                onSave(name.trim(), normalized)
            } catch (e: IllegalArgumentException) {
                urlError = e.message ?: "网址格式不正确"
            }
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("名称（留空使用域名）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.padding(top = 12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                urlError = null
            },
            label = { Text("网址") },
            isError = urlError != null,
            supportingText = { urlError?.let { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WebsitesEmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            AvalonIcons.Globe,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            "还没有网站书签",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "点击「添加网站」保存常用的追番站点",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
