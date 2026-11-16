package com.dx9901mk5.filemanager.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
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
 * - 任一區摺疊後完全消除邊界與側條，僅在螢幕邊緣保留一個小半圓形展開按鈕
 * - 手機直向預設第 1+2 區展開、第 3 區摺疊（留右側半圓展開鈕）；平板/摺疊/橫向三區全開
 * - 中間比例預設鎖定，需點擊左上角鎖頭按鈕才進入拖曳比例編輯模式
 */
@Composable
fun ThreePaneScaffold(
    isExpandedScreen: Boolean,
    isLargeTablet: Boolean,
    showPane3: Boolean,
    isRatioEditMode: Boolean,
    onToggleRatioEditMode: () -> Unit,
    onOpenSettings: () -> Unit,
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
    val density = LocalDensity.current

    var pane1WidthInitialized by remember { mutableStateOf(false) }
    var pane1Width by remember { mutableStateOf(150.dp) }
    var pane2Weight by remember { mutableFloatStateOf(0.5f) }

    val effectivePane1Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 1 else pane1Expanded
    val effectivePane2Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 2 else pane2Expanded
    val effectivePane3Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 3 else pane3Expanded

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!pane1WidthInitialized) {
            // 根目錄固定比例（約 24%，介於 180dp ~ 240dp，不開放變更）
            pane1Width = (maxWidth * 0.24f).coerceIn(180.dp, 240.dp)
            pane1WidthInitialized = true
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // 第一區：展開時顯示完整側欄；摺疊時完全隱藏（0dp，無邊界）
            if (effectivePane1Expanded) {
                Box(
                    modifier = Modifier
                        .width(pane1Width)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            val b = coords.boundsInRoot()
                            onPaneBoundsChanged(1, b.left, b.right)
                        }
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "設定與關於我",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onToggleRatioEditMode,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRatioEditMode) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (isRatioEditMode) "鎖定分區比例" else "編輯分區拖曳比例",
                                    tint = if (isRatioEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
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
                }

                // 根目錄分隔線固定，不給予拖曳變更
                ResizableDivider(
                    editable = false,
                    onDrag = {}
                )
            }

            // 第二區與第三區容器
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val totalWidth = maxWidth
                Row(Modifier.fillMaxSize()) {
                    if (effectivePane2Expanded) {
                        val pane2Modifier = if (showPane3 && effectivePane3Expanded) {
                            Modifier.weight(pane2Weight.coerceIn(0.2f, 0.8f))
                        } else {
                            Modifier.weight(1f)
                        }
                        Box(
                            modifier = pane2Modifier
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val b = coords.boundsInRoot()
                                    onPaneBoundsChanged(2, b.left, b.right)
                                }
                        ) {
                            pane2Content { onPaneExpandedChange(2, false) }
                        }
                    }

                    // 僅在第二與第三區同時展開時才顯示分隔線 2
                    if (showPane3 && effectivePane2Expanded && effectivePane3Expanded) {
                        ResizableDivider(
                            editable = isRatioEditMode,
                            onDrag = { deltaPx ->
                                val totalPx = with(density) { totalWidth.toPx() }.coerceAtLeast(1f)
                                pane2Weight = (pane2Weight + (deltaPx / totalPx)).coerceIn(0.2f, 0.8f)
                            }
                        )
                    }

                    if (showPane3 && effectivePane3Expanded) {
                        val pane3Modifier = if (effectivePane2Expanded) {
                            Modifier.weight((1f - pane2Weight).coerceIn(0.2f, 0.8f))
                        } else {
                            Modifier.weight(1f)
                        }
                        Box(
                            modifier = pane3Modifier
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val b = coords.boundsInRoot()
                                    onPaneBoundsChanged(3, b.left, b.right)
                                }
                        ) {
                            pane3Content { onPaneExpandedChange(3, false) }
                        }
                    }
                }
            }
        }

        // 第一區摺疊後：左緣保留一個小半圓形展開按鈕（無整條邊界）
        if (!effectivePane1Expanded) {
            Surface(
                shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 56.dp)
                    .width(26.dp)
                    .height(48.dp)
                    .clickable { onPaneExpandedChange(1, true) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "展開第一區",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 第二區摺疊後：上方保留一個小半圓形展開按鈕
        if (!effectivePane2Expanded) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(48.dp)
                    .height(26.dp)
                    .clickable { onPaneExpandedChange(2, true) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "展開視窗 A",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 第三區摺疊後：右緣保留一個小半圓形展開按鈕（無整條邊界）
        if (showPane3 && !effectivePane3Expanded) {
            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp)
                    .width(26.dp)
                    .height(48.dp)
                    .clickable { onPaneExpandedChange(3, true) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "展開視窗 B",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
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
