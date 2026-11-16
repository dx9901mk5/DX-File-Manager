package com.dx9901mk5.filemanager.ui.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 三區彈性容器 (Pane 1 / Pane 2 / Pane 3)：
 * - 手機直向預設只顯示第 1、2 區；打橫或平板/摺疊顯示第 1、2、3 區
 * - 中間比例預設鎖定，需點擊左上角「拖曳比例」按鈕才可拖曳調整
 * - 三區上方右側皆有摺疊按鈕，摺疊後頂部保留顯眼的展開按鈕
 */
@Composable
fun ThreePaneScaffold(
    isExpandedScreen: Boolean,
    isLargeTablet: Boolean,
    showPane3: Boolean,
    isRatioEditMode: Boolean,
    onToggleRatioEditMode: () -> Unit,
    pane1Expanded: Boolean,
    pane2Expanded: Boolean,
    pane3Expanded: Boolean,
    onPaneExpandedChange: (paneIndex: Int, expanded: Boolean) -> Unit,
    autoCollapseUnusedPaneOnDrag: Int?,
    onPaneBoundsChanged: (paneIndex: Int, leftX: Float, rightX: Float) -> Unit = { _, _, _ -> },
    pane1Content: @Composable () -> Unit,
    pane2Content: @Composable (onCollapse: () -> Unit) -> Unit,
    pane3Content: @Composable (onCollapse: () -> Unit) -> Unit
) {
    val minCollapsedWidth = 48.dp
    val density = LocalDensity.current

    var pane1WidthInitialized by remember { mutableStateOf(false) }
    var pane1Width by remember { mutableStateOf(150.dp) }
    var pane2Weight by remember { mutableFloatStateOf(0.5f) }

    val effectivePane1Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 1 else pane1Expanded
    val effectivePane2Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 2 else pane2Expanded
    val effectivePane3Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 3 else pane3Expanded

    val animatedPane1Width by animateDpAsState(
        targetValue = if (effectivePane1Expanded) pane1Width else minCollapsedWidth,
        label = "Pane1Width"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!pane1WidthInitialized) {
            pane1Width = (maxWidth * (if (showPane3) 0.143f else 0.30f)).coerceIn(116.dp, 200.dp)
            pane1WidthInitialized = true
        }
        Row(modifier = Modifier.fillMaxSize()) {
            // 第一區：根目錄與側欄導覽
            Box(
                modifier = Modifier
                    .width(animatedPane1Width)
                    .fillMaxHeight()
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInRoot()
                        onPaneBoundsChanged(1, b.left, b.right)
                    }
            ) {
                if (effectivePane1Expanded) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左上角：拖曳比例鎖定/編輯按鈕
                            IconButton(
                                onClick = onToggleRatioEditMode,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRatioEditMode) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (isRatioEditMode) "鎖定分區比例" else "編輯分區拖曳比例",
                                    tint = if (isRatioEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "根目錄",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                            )
                            // 第一區右上角：摺疊按鈕
                            IconButton(
                                onClick = { onPaneExpandedChange(1, false) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "摺疊第一區",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        pane1Content()
                    }
                } else {
                    CollapsedPaneBar(
                        title = "根目錄",
                        isRatioEditMode = isRatioEditMode,
                        onToggleRatioEditMode = onToggleRatioEditMode,
                        onExpand = { onPaneExpandedChange(1, true) }
                    )
                }
            }

            // 分隔線 1 (鎖定時固定為 2dp，開啟拖曳比例編輯時變為 18dp 可拖曳把手)
            ResizableDivider(
                editable = isRatioEditMode,
                onDrag = { deltaPx ->
                    if (effectivePane1Expanded) {
                        val deltaDp = with(density) { deltaPx.toDp() }
                        val maxW = if (isExpandedScreen) 320.dp else 240.dp
                        pane1Width = (pane1Width + deltaDp).coerceIn(100.dp, maxW)
                    }
                }
            )

            // 第二區與第三區容器
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val totalWidth = maxWidth
                Row(Modifier.fillMaxSize()) {
                    val pane2Modifier = when {
                        !showPane3 -> Modifier.weight(1f)
                        !effectivePane2Expanded -> Modifier.width(minCollapsedWidth)
                        !effectivePane3Expanded -> Modifier.weight(1f)
                        else -> Modifier.weight(pane2Weight.coerceIn(0.2f, 0.8f))
                    }
                    Box(
                        modifier = pane2Modifier
                            .fillMaxHeight()
                            .onGloballyPositioned { coords ->
                                val b = coords.boundsInRoot()
                                onPaneBoundsChanged(2, b.left, b.right)
                            }
                    ) {
                        if (!showPane3 || effectivePane2Expanded) {
                            pane2Content {
                                if (showPane3) onPaneExpandedChange(2, false)
                            }
                        } else {
                            CollapsedPaneBar(title = "視窗 A", onExpand = { onPaneExpandedChange(2, true) })
                        }
                    }

                    if (showPane3) {
                        ResizableDivider(
                            editable = isRatioEditMode,
                            onDrag = { deltaPx ->
                                if (effectivePane2Expanded && effectivePane3Expanded) {
                                    val totalPx = with(density) { totalWidth.toPx() }.coerceAtLeast(1f)
                                    pane2Weight = (pane2Weight + (deltaPx / totalPx)).coerceIn(0.2f, 0.8f)
                                }
                            }
                        )

                        val pane3Modifier = when {
                            !effectivePane3Expanded -> Modifier.width(minCollapsedWidth)
                            !effectivePane2Expanded -> Modifier.weight(1f)
                            else -> Modifier.weight((1f - pane2Weight).coerceIn(0.2f, 0.8f))
                        }
                        Box(
                            modifier = pane3Modifier
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val b = coords.boundsInRoot()
                                    onPaneBoundsChanged(3, b.left, b.right)
                                }
                        ) {
                            if (effectivePane3Expanded) {
                                pane3Content { onPaneExpandedChange(3, false) }
                            } else {
                                CollapsedPaneBar(title = "視窗 B", onExpand = { onPaneExpandedChange(3, true) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedPaneBar(
    title: String,
    isRatioEditMode: Boolean? = null,
    onToggleRatioEditMode: (() -> Unit)? = null,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        if (isRatioEditMode != null && onToggleRatioEditMode != null) {
            IconButton(
                onClick = onToggleRatioEditMode,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (isRatioEditMode) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = "切換拖曳比例",
                    tint = if (isRatioEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onExpand)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "展開 $title",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.rotate(90f)
        )
    }
}

@Composable
private fun ResizableDivider(
    editable: Boolean,
    onDrag: (Float) -> Unit
) {
    if (!editable) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(18.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(3.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "拖曳調整分區寬度",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(12.dp).rotate(90f)
            )
        }
    }
}
