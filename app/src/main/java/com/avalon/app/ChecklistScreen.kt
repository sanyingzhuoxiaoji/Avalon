package com.avalon.app

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import java.math.BigDecimal

private enum class ChecklistFilter(val label: String) {
    All("全部"),
    Unfinished("未看完"),
    Finished("已看完"),
}

/** 两种清单的差异化文案：页标题、进度单位（集/话）、空行提示。 */
private val ChecklistKind.pageTitle: String
    get() = when (this) {
        ChecklistKind.Anime -> "追番记录"
        ChecklistKind.Manga -> "漫画记录"
    }

private val ChecklistKind.progressUnit: String
    get() = when (this) {
        ChecklistKind.Anime -> "集"
        ChecklistKind.Manga -> "话"
    }

private val ChecklistKind.inputHint: String
    get() = when (this) {
        ChecklistKind.Anime -> "输入番名"
        ChecklistKind.Manga -> "输入漫画名"
    }

/** 清单页（追番/漫画共用）：Notes 式行内清单——行内输入、回车下一行、勾选下沉、进度步进。 */
@Composable
internal fun ChecklistScreen(
    checklist: ChecklistStore,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val titleDrafts = rememberSaveable(saver = TitleDraftsSaver) { mutableStateMapOf<String, String>() }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var newRowId by rememberSaveable { mutableStateOf<String?>(null) }
    var insertAfterId by rememberSaveable { mutableStateOf<String?>(null) }
    var newRowFinished by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun clearNewRow() {
        newRowId?.let { titleDrafts.remove(it) }
        newRowId = null
        insertAfterId = null
    }

    fun commitTitle(record: ChecklistRecord): Boolean {
        val draft = titleDrafts[record.id]?.trim().orEmpty()
        val isNew = record.id == newRowId
        if (draft.isEmpty()) {
            if (!isNew) titleDrafts.remove(record.id)
            return !isNew
        }
        return try {
            if (isNew) {
                checklist.insertAfter(insertAfterId, record.copy(title = draft))
                clearNewRow()
            } else {
                if (draft != record.title) {
                    checklist.save(existingId = record.id, title = draft, note = record.note)
                }
                titleDrafts.remove(record.id)
            }
            true
        } catch (e: StorageWriteException) {
            scope.launch { snackbarHostState.showStorageFailure(e) }
            false
        }
    }

    fun finishEditing(record: ChecklistRecord) {
        if (editingId == record.id) editingId = null
        if (record.id == newRowId && titleDrafts[record.id].isNullOrBlank()) {
            clearNewRow()
        } else {
            commitTitle(record)
        }
    }

    fun closeEditor() {
        val id = editingId ?: return
        val record = if (id == newRowId) {
            ChecklistRecord(id, "", finished = newRowFinished)
        } else {
            checklist.records.firstOrNull { it.id == id }
        }
        if (record != null) finishEditing(record) else editingId = null
    }

    fun insertRow(after: ChecklistRecord?) {
        // 空白行收到回车时不叠加更多空白行。
        if (after != null && !commitTitle(after)) return
        val pending = newRowId
        if (pending != null) {
            if (titleDrafts[pending].isNullOrBlank()) {
                clearNewRow()
            } else if (!commitTitle(ChecklistRecord(pending, "", finished = newRowFinished))) {
                editingId = pending
                return
            }
        }
        insertAfterId = after?.id
        newRowFinished = after?.finished == true
        val id = newId()
        newRowId = id
        editingId = id
    }

    fun changeProgress(record: ChecklistRecord, delta: Int) {
        try {
            checklist.adjustProgress(record.id, delta)
        } catch (e: StorageWriteException) {
            scope.launch { snackbarHostState.showStorageFailure(e) }
        }
    }

    fun commitPending() {
        for (record in checklist.records.toList()) {
            if (record.id in titleDrafts) commitTitle(record)
        }
        newRowId?.let { commitTitle(ChecklistRecord(it, "", finished = newRowFinished)) }
    }

    // 活动暂停或本页被移除（切到其他页）时提交未保存输入，避免静默丢失。
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

    val filter = ChecklistFilter.entries[filterIndex]
    val savedRows = checklist.records.toList()
    val completedCount = savedRows.count { it.finished }
    val draftRowId = newRowId
    val displayedRows = remember(savedRows, draftRowId, insertAfterId, newRowFinished, filter) {
        val ordered = if (draftRowId == null) savedRows else savedRows.toMutableList().apply {
            val index = indexOfFirst { it.id == insertAfterId } + 1
            add(index, ChecklistRecord(draftRowId, "", finished = newRowFinished))
        }
        buildList {
            if (filter != ChecklistFilter.Finished) addAll(ordered.filterNot { it.finished })
            if (filter != ChecklistFilter.Unfinished) {
                val completed = ordered.filter { it.finished }
                if (completed.isNotEmpty()) {
                    add(null) // 已看完分组的标题。
                    addAll(completed)
                }
            }
        }
    }
    val groups = remember(savedRows) { savedRows.associate { it.id to it.finished } }
    val drag = rememberChecklistDrag(
        listState = listState,
        canDrop = { id, targetId -> groups[id]?.let { it == groups[targetId] } == true },
        onDrop = { id, targetId, before ->
            try {
                checklist.move(id, targetId, before)
            } catch (e: StorageWriteException) {
                scope.launch { snackbarHostState.showStorageFailure(e) }
            }
        },
    )

    LaunchedEffect(displayedRows) {
        drag.cancelDrag()
    }

    LaunchedEffect(draftRowId) {
        if (draftRowId != null) {
            val position = displayedRows.indexOfFirst { it?.id == draftRowId }
            if (position >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == draftRowId }) {
                listState.scrollToItem(position)
            }
        }
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                checklist.kind.pageTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "共 ${savedRows.size} 部 · 已看完 $completedCount 部",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChecklistFilter.entries.forEachIndexed { index, item ->
                    FilterChip(
                        selected = filterIndex == index,
                        onClick = {
                            if (filterIndex != index) {
                                closeEditor()
                                filterIndex = index
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).then(drag.listModifier()),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (displayedRows.isEmpty()) {
                item {
                    if (filter == ChecklistFilter.Finished && savedRows.isNotEmpty()) {
                        Text("还没有已看完的记录", Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                    } else {
                        TextButton(onClick = {
                            if (filter == ChecklistFilter.Finished) {
                                filterIndex = ChecklistFilter.All.ordinal
                            }
                            insertRow(null)
                        }) { Text("添加第一条记录") }
                    }
                }
            }
            items(displayedRows, key = { it?.id ?: "done-header" }) { record ->
                if (record == null) {
                    Text(
                        "已看完 $completedCount",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                } else {
                    val isNew = record.id == draftRowId
                    ChecklistRow(
                        record = record,
                        draft = titleDrafts[record.id],
                        editing = editingId == record.id,
                        isNew = isNew,
                        inputHint = checklist.kind.inputHint,
                        progressUnit = checklist.kind.progressUnit,
                        drag = drag,
                        onEdit = {
                            if (editingId != record.id) {
                                closeEditor()
                                editingId = record.id
                            }
                        },
                        onDraftChange = { input ->
                            titleDrafts[record.id] = input.replace('\n', ' ')
                        },
                        onSubmit = { insertRow(record) },
                        onFinishEditing = { finishEditing(record) },
                        onToggle = {
                            try {
                                checklist.setFinished(record.id, !record.finished)
                            } catch (e: StorageWriteException) {
                                scope.launch { snackbarHostState.showStorageFailure(e) }
                            }
                        },
                        onProgressMinus = { changeProgress(record, -1) },
                        onProgressPlus = { changeProgress(record, 1) },
                        onDeleteRequest = { pendingDeleteId = record.id },
                    )
                }
            }
        }
    }

    val pendingDelete = pendingDeleteId?.let { id -> checklist.records.firstOrNull { it.id == id } }
    pendingDelete?.let { record ->
        AvalonDialog(
            title = "删除这条记录？",
            confirmLabel = "删除",
            onConfirm = {
                try {
                    checklist.delete(record.id)
                    titleDrafts.remove(record.id)
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

@Composable
private fun ChecklistRow(
    record: ChecklistRecord,
    draft: String?,
    editing: Boolean,
    isNew: Boolean,
    inputHint: String,
    progressUnit: String,
    drag: ChecklistDrag,
    onEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFinishEditing: () -> Unit,
    onToggle: () -> Unit,
    onProgressMinus: () -> Unit,
    onProgressPlus: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val title = draft ?: record.title
    Card(
        modifier = drag.rowModifier(record.id),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = record.finished,
                onCheckedChange = { onToggle() },
                enabled = !isNew,
                modifier = Modifier.semantics { contentDescription = record.title },
            )
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .combinedClickable(
                        onLongClickLabel = "删除这条记录",
                        onLongClick = if (isNew) null else onDeleteRequest,
                        onClickLabel = "编辑标题",
                        onClick = onEdit,
                    )
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (editing) {
                    TitleField(
                        value = title,
                        hint = inputHint,
                        onValueChange = onDraftChange,
                        onSubmit = onSubmit,
                        onFinishEditing = onFinishEditing,
                    )
                } else {
                    Text(
                        title.ifEmpty { inputHint },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
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
            ProgressStepper(
                progress = record.progress,
                enabled = !isNew,
                unit = progressUnit,
                onMinus = onProgressMinus,
                onPlus = onProgressPlus,
            )
            ChecklistDragHandle(record.id, drag, enabled = !isNew)
        }
    }
}

/** 编辑态保留原生文本选择；平时由标题区域处理点击和长按。 */
@Composable
private fun TitleField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFinishEditing: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
        bringIntoView.bringIntoView()
    }
    var wasFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoView)
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) onSubmit()
                    true
                }
            }
            .onFocusChanged { state ->
                if (state.isFocused) {
                    wasFocused = true
                } else if (wasFocused) {
                    wasFocused = false
                    onFinishEditing()
                }
            },
        decorationBox = { field ->
            if (value.isEmpty()) {
                Box {
                    Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                }
            } else {
                field()
            }
        },
    )
}

/** 进度文本：精确十进制，去掉尾随 0（2.500 → 2.5，整数不带小数点）。 */
private fun BigDecimal.toProgressText(): String = stripTrailingZeros().toPlainString()

/** 进度步进器：0 时减号禁用；保存失败由 store 保持原值，不虚增。集（追番）/话（漫画）按类型区分。 */
@Composable
private fun ProgressStepper(
    progress: BigDecimal,
    enabled: Boolean,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 图标向数字靠拢，保留原有按钮触控范围。
        IconButton(onClick = onMinus, enabled = enabled && progress > BigDecimal.ZERO) {
            Icon(
                AvalonIcons.Minus,
                contentDescription = "减少已看${unit}数",
                modifier = Modifier.offset(x = 6.dp),
            )
        }
        Text(
            "${progress.toProgressText()} $unit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        IconButton(onClick = onPlus, enabled = enabled) {
            Icon(
                AvalonIcons.Add,
                contentDescription = "增加已看${unit}数",
                modifier = Modifier.offset(x = (-6).dp),
            )
        }
    }
}

