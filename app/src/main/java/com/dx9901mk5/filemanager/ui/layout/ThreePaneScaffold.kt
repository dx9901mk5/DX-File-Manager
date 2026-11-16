package com.dx9901mk5.filemanager.ui.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 三區彈性容器 (Pane 1 / Pane 2 / Pane 3)：
 * - isExpandedScreen (摺疊機展開/平板) 預設自動開啟第一區
 * - 任何一區摺疊時強制保留 48.dp 展開按鈕空間
 * - 分隔線可自由左右拖曳調整寬度
 * - 跨區拖曳時自動暫時收合未經過的分區 (autoCollapseUnusedPaneOnDrag)
 */
@Composable
fun ThreePaneScaffold(
    isExpandedScreen: Boolean,
    autoCollapseUnusedPaneOnDrag: Int?,
    pane1ExpandedOverride: Boolean? = null,
    pane3ExpandedOverride: Boolean? = null,
    onPaneBoundsChanged: (paneIndex: Int, leftX: Float, rightX: Float) -> Unit = { _, _, _ -> },
    pane1Content: @Composable () -> Unit,
    pane2Content: @Composable (onCollapse: () -> Unit) -> Unit,
    pane3Content: @Composable (onCollapse: () -> Unit) -> Unit
) {
    val minCollapsedWidth = 48.dp
    val density = LocalDensity.current

    var pane1Expanded by remember(isExpandedScreen) { mutableStateOf(isExpandedScreen) }
    var pane2Expanded by remember { mutableStateOf(true) }
    var pane3Expanded by remember { mutableStateOf(true) }

    pane1ExpandedOverride?.let { pane1Expanded = it }
    pane3ExpandedOverride?.let { pane3Expanded = it }

    var pane1Width by remember { mutableStateOf(280.dp) }
    var pane2Weight by remember { mutableFloatStateOf(0.5f) }

    val effectivePane1Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 1 else pane1Expanded
    val effectivePane2Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 2 else pane2Expanded
    val effectivePane3Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 3 else pane3Expanded

    val animatedPane1Width by animateDpAsState(
        targetValue = if (effectivePane1Expanded) pane1Width else minCollapsedWidth,
        label = "Pane1Width"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "根目錄與導覽",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        IconButton(onClick = { pane1Expanded = false }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "摺疊第一區")
                        }
                    }
                    pane1Content()
                }
            } else {
                CollapsedPaneBar(title = "根目錄", onExpand = { pane1Expanded = true })
            }
        }

        // 分隔線 1
        ResizableDivider(
            onDrag = { deltaPx ->
                if (effectivePane1Expanded) {
                    val deltaDp = with(density) { deltaPx.toDp() }
                    pane1Width = (pane1Width + deltaDp).coerceIn(200.dp, 420.dp)
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
                        pane2Content { pane2Expanded = false }
                    } else {
                        CollapsedPaneBar(title = "視窗 A", onExpand = { pane2Expanded = true })
                    }
                }

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
                        pane3Content { pane3Expanded = false }
                    } else {
                        CollapsedPaneBar(title = "視窗 B", onExpand = { pane3Expanded = true })
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        IconButton(onClick = onExpand) {
            Icon(Icons.Default.ChevronRight, contentDescription = "展開 $title")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            .width(8.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(36.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
    }
}
