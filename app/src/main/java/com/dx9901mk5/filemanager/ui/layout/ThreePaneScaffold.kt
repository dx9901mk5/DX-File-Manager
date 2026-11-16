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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
 * - 支援手機 / 摺疊 / 展開摺疊 / 小平板 / 大平板 DPI 比例
 * - 記憶各分區上次展開或摺疊狀態
 * - 任何一區摺疊時強制保留 48.dp 展開按鈕空間，上方皆有可點擊的展開/摺疊按鈕
 * - 加寬分區間拖曳把手 (18.dp)，避免誤按
 */
@Composable
fun ThreePaneScaffold(
    isExpandedScreen: Boolean,
    isLargeTablet: Boolean,
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
        // 第一分區預設寬度為第二、三分區的 1/3 (約佔總寬 1/7 ≈ 14.3%)
        if (!pane1WidthInitialized) {
            pane1Width = (maxWidth * 0.143f).coerceIn(116.dp, 185.dp)
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
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "根目錄與導覽",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { onPaneExpandedChange(1, false) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "摺疊第一區",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    pane1Content()
                }
            } else {
                CollapsedPaneBar(title = "根目錄", onExpand = { onPaneExpandedChange(1, true) })
            }
        }

        // 加寬分隔線 1 (第一區與第二區之間，避免誤按)
        ResizableDivider(
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
                    if (effectivePane2Expanded) {
                        pane2Content { onPaneExpandedChange(2, false) }
                    } else {
                        CollapsedPaneBar(title = "視窗 A", onExpand = { onPaneExpandedChange(2, true) })
                    }
                }

                // 加寬分隔線 2 (第二區與第三區之間)
                ResizableDivider(
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

@Composable
private fun CollapsedPaneBar(title: String, onExpand: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        IconButton(
            onClick = onExpand,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "展開 $title",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(16.dp))
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
private fun ResizableDivider(onDrag: (Float) -> Unit) {
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
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
