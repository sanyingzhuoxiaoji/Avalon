package com.avalon.app

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collectLatest

/**
 * 清单行拖拽排序控制器：把手长按发起拖动，行跟手平移，目标行绘制插入指示线；
 * 移动过程中不改动存储与列表顺序，松手时只回调一次 onDrop。
 * canDrop(from, to) 决定哪些行可作为放置目标（仅同组行、非行条目如编辑器/分组标题应返回 false）。
 */
@Stable
internal interface ChecklistDrag {
    /** 正在被拖动的行 id；无拖动时为 null。 */
    val draggedId: String?

    /** 行卡片修饰符：拖动中的行置于最上层并随手指移动。 */
    fun rowModifier(id: String): Modifier

    /** 在清单顶层绘制插入标记，不被拖动中的卡片遮挡。 */
    @Composable
    fun listModifier(): Modifier

    /** 以下成员仅供 [ChecklistDragHandle] 手势转发使用，业务代码勿直接调用。 */

    /** 尝试发起拖动：行可见且当前无拖动时成功。pointerRootY 为指针在窗口坐标系中的 y。 */
    fun startDrag(id: String, pointerRootY: Float): Boolean

    /** 指针纵向移动增量（像素）。 */
    fun dragBy(deltaY: Float)

    /** 松手：若插入位置发生变化则回调一次 onDrop，否则取消不落盘。 */
    fun endDrag()

    /** 手势被打断（来电、父级拦截等）：取消拖动，绝不落盘。 */
    fun cancelDrag()
}

/**
 * 创建清单拖拽排序控制器。canDrop/onDrop 以最新引用生效，重组不会中断进行中的手势。
 * 控制器存续期间自动逐帧驱动边缘自动滚动（指针贴近列表上下边缘时滚动列表）。
 */
@Composable
internal fun rememberChecklistDrag(
    listState: LazyListState,
    canDrop: (String, String) -> Boolean,
    onDrop: (id: String, targetId: String, before: Boolean) -> Unit,
): ChecklistDrag {
    val latestCanDrop by rememberUpdatedState(canDrop)
    val latestOnDrop by rememberUpdatedState(onDrop)
    val density = LocalDensity.current
    val controller = remember(listState, density) {
        ChecklistDragController(
            listState = listState,
            density = density,
            canDrop = { from, to -> latestCanDrop(from, to) },
            onDrop = { id, targetId, before -> latestOnDrop(id, targetId, before) },
        )
    }
    // 拖动期间每帧驱动一次边缘自动滚动；结束（含取消）即停止。
    LaunchedEffect(controller) {
        snapshotFlow { controller.isDragging }
            .collectLatest { dragging ->
                if (!dragging) return@collectLatest
                var lastNanos = 0L
                while (controller.isDragging) {
                    val now = withFrameNanos { it }
                    if (lastNanos != 0L) {
                        val deltaSeconds = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                        controller.autoScrollStep(deltaSeconds)
                    }
                    lastNanos = now
                }
            }
    }
    return controller
}

/** 行尾拖动把手：按住约长按时长后上下拖动排序。只承担拖动手势，不触发行内点击/长按删除。 */
@Composable
internal fun ChecklistDragHandle(id: String, drag: ChecklistDrag, enabled: Boolean) {
    val pinnable = LocalPinnableContainer.current
    val session = remember { DragHandleSession() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .onGloballyPositioned { session.coordinates = it }
            .then(
                if (enabled) {
                    Modifier.pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                val handleRootY = session.coordinates?.positionInRoot()?.y
                                if (handleRootY != null && drag.startDrag(id, handleRootY + position.y)) {
                                    session.started = true
                                    // 钉住被拖行：拖出视口后行保持组合，拖回时不丢状态。
                                    session.pinned = pinnable?.pin()
                                }
                            },
                            onDrag = { _, amount ->
                                if (session.started) drag.dragBy(amount.y)
                            },
                            onDragEnd = {
                                if (session.started) {
                                    session.started = false
                                    drag.endDrag()
                                    session.pinned?.release()
                                    session.pinned = null
                                }
                            },
                            onDragCancel = {
                                if (session.started) {
                                    session.started = false
                                    drag.cancelDrag()
                                    session.pinned?.release()
                                    session.pinned = null
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AvalonIcons.DragHandle,
            contentDescription = "拖动排序",
            tint = if (drag.draggedId == id) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 把手会话状态：坐标与钉住句柄放在普通对象里，避免布局回调写入状态引发重组。 */
private class DragHandleSession {
    var coordinates: LayoutCoordinates? = null
    var started: Boolean = false
    var pinned: PinnableContainer.PinnedHandle? = null
}

/**
 * 拖拽实现：所有几何量在“行偏移坐标系”（LazyListItemInfo.offset 同一坐标系，随内容滚动变化）
 * 中计算。指针位置在该坐标系中只随手指移动而变化，滚动通过补偿量折算，
 * 因此自动滚动时被拖行保持贴手、目标判定持续有效。
 */
private class ChecklistDragController(
    private val listState: LazyListState,
    density: Density,
    private val canDrop: (String, String) -> Boolean,
    private val onDrop: (String, String, Boolean) -> Unit,
) : ChecklistDrag, Density by density {

    override var draggedId: String? by mutableStateOf<String?>(null)
        private set

    internal val isDragging: Boolean
        get() = draggedId != null

    private var dragDeltaY by mutableFloatStateOf(0f)
    private var placedRootY by mutableFloatStateOf(0f)

    // 放置目标：目标行 id 与方向（before = 插到目标行上方，指示线画在目标行上缘）。
    private var targetId by mutableStateOf<String?>(null)
    private var targetBefore by mutableStateOf(false)

    // 记录卡片的外层布局坐标，不含内层 graphicsLayer 平移。
    private val rowCoordinates = mutableMapOf<String, LayoutCoordinates>()

    // 拖动起点。滚动后的卡片位置以 onPlaced 的实际位置为准。
    private var startIndex = 0
    private var startItemOffset = 0
    private var rowHeightPx = 0f
    private var startPointerY = 0f
    private var startRootY = 0f
    private var hasMoved = false

    override fun rowModifier(id: String): Modifier = Modifier
        .onPlaced { coordinates ->
            rowCoordinates[id] = coordinates
            if (draggedId == id) placedRootY = coordinates.positionInRoot().y
        }
        .zIndex(if (draggedId == id) 1f else 0f)
        .graphicsLayer {
            translationY = if (draggedId == id) startRootY + dragDeltaY - placedRootY else 0f
        }

    @Composable
    override fun listModifier(): Modifier {
        val color = MaterialTheme.colorScheme.primary
        return Modifier.drawWithContent {
            drawContent()
            val id = targetId ?: return@drawWithContent
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.key == id } ?: return@drawWithContent
            val edge = (item.offset - layout.viewportStartOffset +
                (if (targetBefore) 0 else item.size)).toFloat()
            if (edge in 0f..size.height) {
                val inset = 24.dp.toPx()
                val stroke = 2.dp.toPx()
                val y = edge.coerceIn(stroke / 2, size.height - stroke / 2)
                drawLine(
                    color = color,
                    start = Offset(inset, y),
                    end = Offset(size.width - inset, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    override fun startDrag(id: String, pointerRootY: Float): Boolean {
        if (draggedId != null) return false
        // 顺手清掉已离开组合的行坐标，映射上限为清单行数。
        rowCoordinates.entries.removeAll { !it.value.isAttached }
        val coordinates = rowCoordinates[id]?.takeIf { it.isAttached } ?: return false
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return false
        startIndex = info.index
        startItemOffset = info.offset
        rowHeightPx = info.size.toFloat()
        startRootY = coordinates.positionInRoot().y
        placedRootY = startRootY
        startPointerY = pointerRootY - (startRootY - info.offset)
        dragDeltaY = 0f
        targetId = null
        targetBefore = false
        hasMoved = false
        draggedId = id
        return true
    }

    override fun dragBy(deltaY: Float) {
        if (draggedId == null || deltaY == 0f) return
        hasMoved = true
        dragDeltaY += deltaY
        updateTarget()
    }

    override fun endDrag() {
        val id = draggedId ?: return
        if (hasMoved) updateTarget()
        val target = targetId
        val before = targetBefore
        reset()
        if (target != null) onDrop(id, target, before)
    }

    override fun cancelDrag() {
        if (draggedId != null) reset()
    }

    private fun reset() {
        draggedId = null
        targetId = null
        targetBefore = false
        dragDeltaY = 0f
    }

    /** 被拖行中心在行偏移坐标系中的当前位置（与滚动无关，只随手指移动）。 */
    private fun draggedCenter(): Float =
        startItemOffset + dragDeltaY + rowHeightPx / 2f

    /** 只在跨过同组行的中心时更新插入位置；绝对索引不受可见窗口滚动影响。 */
    private fun updateTarget() {
        val id = draggedId ?: return
        val center = draggedCenter()
        var target: LazyListItemInfo? = null
        for (item in listState.layoutInfo.visibleItemsInfo) {
            val key = item.key as? String ?: continue
            if (item.index == startIndex || !canDrop(id, key)) continue
            val itemCenter = item.offset + item.size / 2f
            val current = target
            if (item.index < startIndex && center < itemCenter &&
                (current == null || item.index < current.index)
            ) {
                target = item
            } else if (item.index > startIndex && center > itemCenter &&
                (current == null || item.index > current.index)
            ) {
                target = item
            }
        }
        targetId = target?.key as? String
        targetBefore = target != null && target.index < startIndex
    }

    /**
     * 自动滚动单帧步进：指针进入列表上下 48dp 边缘带时按贴近程度线性加速（上限约每秒
     * 90% 视口高）；滚动以后重新计算放置目标。
     */
    suspend fun autoScrollStep(deltaSeconds: Float) {
        if (draggedId == null || !hasMoved) return
        val viewport = listState.layoutInfo
        val start = viewport.viewportStartOffset
        val end = viewport.viewportEndOffset
        val edgeZone = 48.dp.toPx()
        val viewportSize = (end - start).coerceAtLeast(1)
        val maxSpeed = viewportSize * 0.9f
        val pointerY = startPointerY + dragDeltaY
        val speed = when {
            pointerY < start + edgeZone -> {
                -maxSpeed * (1f - (pointerY - start) / edgeZone).coerceIn(0f, 1f)
            }
            pointerY > end - edgeZone -> {
                maxSpeed * (1f - (end - pointerY) / edgeZone).coerceIn(0f, 1f)
            }
            else -> 0f
        }
        if (speed == 0f) return
        if (listState.scrollBy(speed * deltaSeconds) != 0f) updateTarget()
    }
}
