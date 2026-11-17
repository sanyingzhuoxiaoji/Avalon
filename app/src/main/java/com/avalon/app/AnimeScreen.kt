package com.avalon.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class AnimeFilter(val label: String) {
    All("全部"),
    Unfinished("未看完"),
    Finished("已看完"),
}

/** 追番记录页：Notes 式行内清单——行内输入、回车下一行、勾选下沉、集数步进。 */
@Composable
internal fun AnimeScreen(
    store: AvalonStore,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    // 行内标题草稿：记录 id → 未提交文本；提交成功才移除，失败保留并弹条。
    val titleDrafts = rememberSaveable(saver = TitleDraftsSaver) { mutableStateMapOf<String, String>() }
    // 新行编辑器草稿，可暂存未提交的多行文本。
    var editorDraft by rememberSaveable { mutableStateOf("") }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuRecordId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val newLineFocus = remember { FocusRequester() }
    val entryScope = rememberCoroutineScope()

    fun focusNewLine() {
        entryScope.launch {
            withFrameNanos { }
            val index = if (AnimeFilter.entries[filterIndex] == AnimeFilter.Finished) {
                0
            } else {
                store.anime.count { !it.finished }
            }
            listState.scrollToItem(index)
            withFrameNanos { }
            newLineFocus.requestFocus()
        }
    }

    fun commitTitle(record: AnimeRecord) {
        val draft = titleDrafts[record.id] ?: return
        val trimmed = draft.trim()
        if (trimmed.isEmpty() || trimmed == record.title) {
            titleDrafts.remove(record.id)
            return
        }
        try {
            store.saveAnime(existingId = record.id, title = trimmed, note = record.note)
            titleDrafts.remove(record.id)
        } catch (e: StorageWriteException) {
            // 失败保留草稿，展示数据层的中文错误。
            scope.launch { snackbarHostState.showStorageFailure(e) }
        }
    }

    fun changeTitle(record: AnimeRecord, input: String) {
        titleDrafts[record.id] = input.replace('\n', ' ')
        if ('\n' in input) commitTitle(record)
    }

    // 按行提交编辑器内容，返回应保留的剩余草稿（失败的行原样带回，绝不静默丢弃）。
    fun commitEditorLines(raw: String, flushTail: Boolean = false): String {
        val parts = raw.split('\n')
        val tail = if (flushTail) "" else parts.last()
        val failed = StringBuilder()
        var failure: StorageWriteException? = null
        val lines = if (flushTail) parts else parts.dropLast(1)
        lines.forEach { line ->
            val title = line.trim()
            if (title.isEmpty()) return@forEach
            try {
                store.saveAnime(existingId = null, title = title, note = "")
            } catch (e: StorageWriteException) {
                failed.appendLine(title)
                failure = e
            }
        }
        failure?.let { error ->
            scope.launch { snackbarHostState.showStorageFailure(error) }
        }
        return if (failed.isEmpty()) tail else failed.toString() + tail
    }

    fun changeEpisodes(record: AnimeRecord, delta: Int) {
        try {
            store.setAnimeEpisodes(record.id, record.episodes + delta)
        } catch (e: StorageWriteException) {
            scope.launch { snackbarHostState.showStorageFailure(e) }
        }
    }

    fun commitPending() {
        titleDrafts.toMap().keys.forEach { id ->
            store.anime.firstOrNull { it.id == id }?.let { commitTitle(it) }
        }
        editorDraft = commitEditorLines(editorDraft, flushTail = true)
    }

    // 活动暂停或本页被移除（切到网站连接页）时提交未保存输入，避免静默丢失。
    val context = LocalContext.current
    DisposableEffect(context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) commitPending()
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            commitPending()
        }
    }

    val filter = AnimeFilter.entries[filterIndex]
    val unfinished = store.anime.filter { !it.finished }
    val completed = store.anime.filter { it.finished }
    val showUnfinished = filter != AnimeFilter.Finished
    val showCompleted = filter != AnimeFilter.Unfinished

    Column(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "追番记录",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "共 ${store.anime.size} 部 · 已看完 ${completed.size} 部",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimeFilter.entries.forEachIndexed { index, item ->
                    FilterChip(
                        selected = filterIndex == index,
                        onClick = { filterIndex = index },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showUnfinished) {
                items(unfinished, key = { it.id }) { record ->
                    AnimeRow(
                        record = record,
                        draft = titleDrafts[record.id],
                        onDraftChange = { changeTitle(record, it) },
                        onCommitTitle = { commitTitle(record) },
                        onToggle = {
                            try {
                                store.setAnimeFinished(record.id, !record.finished)
                            } catch (e: StorageWriteException) {
                                scope.launch { snackbarHostState.showStorageFailure(e) }
                            }
                        },
                        onEpisodesMinus = { changeEpisodes(record, -1) },
                        onEpisodesPlus = { changeEpisodes(record, 1) },
                        showMenu = menuRecordId == record.id,
                        onDismissMenu = { menuRecordId = null },
                        onOpenMenu = { menuRecordId = record.id },
                        onDeleteRequest = { pendingDeleteId = record.id },
                    )
                }
            }
            // 新行编辑器固定排在未看完之后、已看完之前，方便连续输入。
            item(key = "new-anime-editor") {
                NewAnimeEditor(
                    draft = editorDraft,
                    focusRequester = newLineFocus,
                    onDraftChange = { input ->
                        if ('\n' in input) {
                            editorDraft = commitEditorLines(input)
                            focusNewLine()
                        } else {
                            editorDraft = input
                        }
                    },
                    onCommit = { editorDraft = commitEditorLines(editorDraft, flushTail = true) },
                    onSubmit = {
                        editorDraft = commitEditorLines(editorDraft, flushTail = true)
                        focusNewLine()
                    },
                )
            }
            if (showCompleted && completed.isNotEmpty()) {
                item(key = "done-header") {
                    Text(
                        "已看完 ${completed.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
            if (showCompleted) {
                items(completed, key = { it.id }) { record ->
                    AnimeRow(
                        record = record,
                        draft = titleDrafts[record.id],
                        onDraftChange = { changeTitle(record, it) },
                        onCommitTitle = { commitTitle(record) },
                        onToggle = {
                            try {
                                store.setAnimeFinished(record.id, !record.finished)
                            } catch (e: StorageWriteException) {
                                scope.launch { snackbarHostState.showStorageFailure(e) }
                            }
                        },
                        onEpisodesMinus = { changeEpisodes(record, -1) },
                        onEpisodesPlus = { changeEpisodes(record, 1) },
                        showMenu = menuRecordId == record.id,
                        onDismissMenu = { menuRecordId = null },
                        onOpenMenu = { menuRecordId = record.id },
                        onDeleteRequest = { pendingDeleteId = record.id },
                    )
                }
            }
        }
    }

    val pendingDelete = pendingDeleteId?.let { id -> store.anime.firstOrNull { it.id == id } }
    pendingDelete?.let { record ->
        AvalonDialog(
            title = "删除这条记录？",
            confirmLabel = "删除",
            onConfirm = {
                try {
                    store.deleteAnime(record.id)
                    pendingDeleteId = null
                } catch (e: StorageWriteException) {
                    scope.launch { snackbarHostState.showStorageFailure(e) }
                }
            },
            onDismiss = { pendingDeleteId = null },
        ) {
            Text(
                "《${record.title}》将被删除，操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 行标题草稿保存器：旋转重建不丢未提交的输入。 */
private val TitleDraftsSaver: Saver<SnapshotStateMap<String, String>, Any> = mapSaver(
    save = { state -> state.toMap() },
    restore = { saved ->
        val restored = mutableStateMapOf<String, String>()
        saved.forEach { (key, value) ->
            if (value is String) restored[key] = value
        }
        restored
    },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimeRow(
    record: AnimeRecord,
    draft: String?,
    onDraftChange: (String) -> Unit,
    onCommitTitle: () -> Unit,
    onToggle: () -> Unit,
    onEpisodesMinus: () -> Unit,
    onEpisodesPlus: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onOpenMenu: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClickLabel = "删除这条记录",
                    onLongClick = onOpenMenu,
                    onClick = { },
                )
                .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = record.finished,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = record.title },
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                AnimeTitleField(
                    value = draft ?: record.title,
                    onValueChange = onDraftChange,
                    onCommit = onCommitTitle,
                )
                if (record.note.isNotBlank()) {
                    Text(
                        record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            EpisodeStepper(
                episodes = record.episodes,
                onMinus = onEpisodesMinus,
                onPlus = onEpisodesPlus,
            )
            Box {
                DropdownMenu(expanded = showMenu, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDismissMenu()
                            onDeleteRequest()
                        },
                    )
                }
            }
        }
    }
}

/** 行内标题编辑：点击即改，回车或失焦提交（提交逻辑由调用方处理）。 */
@Composable
private fun AnimeTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    var wasFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onCommit() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) {
                    wasFocused = true
                } else if (wasFocused) {
                    wasFocused = false
                    onCommit()
                }
            },
    )
}

/** 已看集数步进器：0 时减号禁用；保存失败由 store 保持原值，不虚增。 */
@Composable
private fun EpisodeStepper(
    episodes: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, enabled = episodes > 0) {
            Icon(AvalonIcons.Minus, contentDescription = "减少已看集数")
        }
        Text(
            "$episodes 集",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 42.dp),
        )
        IconButton(onClick = onPlus) {
            Icon(AvalonIcons.Add, contentDescription = "增加已看集数")
        }
    }
}

/** 新行编辑器：回车/下一动作提交并保留焦点；粘贴多行按行拆分入库。 */
@Composable
private fun NewAnimeEditor(
    draft: String,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onSubmit: () -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val focusScope = rememberCoroutineScope()
    var wasFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        placeholder = { Text("输入番名，回车下一行") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onSubmit() }),
        minLines = 1,
        maxLines = 5,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    wasFocused = true
                    focusScope.launch { bringIntoView.bringIntoView() }
                } else if (wasFocused) {
                    wasFocused = false
                    onCommit()
                }
            },
    )
}

