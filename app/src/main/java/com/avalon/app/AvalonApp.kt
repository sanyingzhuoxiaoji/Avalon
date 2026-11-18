package com.avalon.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 底部页签序号：0/1 沿用旧版已保存的选中页，漫画页追加为 2；导航顺序为 追番/漫画/网站。 */
private const val TabAnime = 0
private const val TabSites = 1
private const val TabManga = 2

/**
 * 应用骨架：紧凑品牌顶栏 + 三页底部导航 + 页级 FAB。
 * AvalonStore 在根部 remember，一次创建、全局共享；
 * 网站测试状态板也放根部，切页不丢结果，测试在离开页面后仍会按超时收敛，不会永久“测试中”。
 */
@Composable
internal fun AvalonApp(store: AvalonStore) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showWebsiteAdd by rememberSaveable { mutableStateOf(false) }
    val probeScope = rememberCoroutineScope()
    val probeBoard = remember { WebsiteProbeBoard() }
    val snackbarHostState = remember { SnackbarHostState() }
    val pageStateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AvalonHeader() },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(AvalonIcons.Checklist, contentDescription = null) },
                    label = { Text("追番记录") },
                    selected = tab == TabAnime,
                    onClick = { tab = TabAnime },
                )
                NavigationBarItem(
                    icon = { Icon(AvalonIcons.Book, contentDescription = null) },
                    label = { Text("漫画记录") },
                    selected = tab == TabManga,
                    onClick = { tab = TabManga },
                )
                NavigationBarItem(
                    icon = { Icon(AvalonIcons.Globe, contentDescription = null) },
                    label = { Text("网站连接") },
                    selected = tab == TabSites,
                    onClick = { tab = TabSites },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 清单页为行内输入（Notes 式），无需 FAB；网站页保留添加 FAB。
        floatingActionButton = {
            if (tab == TabSites) {
                ExtendedFloatingActionButton(
                    text = { Text("添加网站") },
                    icon = { Icon(AvalonIcons.Add, contentDescription = null) },
                    onClick = { showWebsiteAdd = true },
                )
            }
        },
    ) { inner ->
        pageStateHolder.SaveableStateProvider(tab) {
            when (tab) {
                TabSites -> WebsitesScreen(
                    store = store,
                    board = probeBoard,
                    scope = probeScope,
                    snackbarHostState = snackbarHostState,
                    showAdd = showWebsiteAdd,
                    onDismissAdd = { showWebsiteAdd = false },
                    modifier = Modifier.padding(inner),
                )
                // 追番(0)/漫画(2) 各自独立的 SaveableStateProvider 槽位，共用同一清单页。
                else -> ChecklistScreen(
                    checklist = if (tab == TabManga) store.manga else store.anime,
                    scope = probeScope,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.padding(inner),
                )
            }
        }
    }
}

@Composable
private fun AvalonHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "阿瓦隆",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "追番 · 漫画 · 网站连接",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Image(
            painter = painterResource(R.drawable.avalon_portrait),
            contentDescription = "应用图标",
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * 通用表单/确认对话框：宽度受控、内容可滚动（verticalScroll），
 * 键盘弹出时按 adjustResize 抬升，底部避让导航条。按钮右下对齐。
 */
@Composable
internal fun AvalonDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "取消",
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // 全宽 + 不贴合窗口 inset：由 imePadding/navigationBarsPadding 接管，
            // 键盘弹出时窗口按 ADJUST_RESIZE/ADJUST_NOTHING 变化，内容始终可见。
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding()
                .widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 24.dp,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .heightIn(max = 560.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm, enabled = confirmEnabled) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

/** 本地写入失败的统一提示：展示数据层的中文错误信息，不打断当前对话框。 */
internal suspend fun SnackbarHostState.showStorageFailure(exception: StorageWriteException) {
    showSnackbar(exception.message ?: "写入本地数据失败，请重试")
}
