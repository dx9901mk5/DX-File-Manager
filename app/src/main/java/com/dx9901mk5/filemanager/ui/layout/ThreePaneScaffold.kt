package com.dx9901mk5.filemanager.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
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
    onOpenAiAssistant: () -> Unit = {},
    onOpenSettings: () -> Unit,
    pane1Expanded: Boolean,
    pane2Expanded: Boolean,
    pane3Expanded: Boolean,
    onPaneExpandedChange: (paneIndex: Int, expanded: Boolean) -> Unit,
    autoCollapseUnusedPaneOnDrag: Int?,
    keepAliveSourcePane: Int? = null,
    hoveredExpandButtonPane: Int? = null,
    onExpandButtonBoundsChanged: (paneIndex: Int, rect: Rect?) -> Unit = { _, _ -> },
    onPaneBoundsChanged: (paneIndex: Int, leftX: Float, rightX: Float) -> Unit = { _, _, _ -> },
    pane1Content: @Composable () -> Unit,
    pane2Content: @Composable (onCollapse: () -> Unit) -> Unit,
    pane3Content: @Composable (onCollapse: () -> Unit) -> Unit
) {
    val density = LocalDensity.current

    var pane1WidthInitialized by remember { mutableStateOf(false) }
    var pane1Width by remember { mutableStateOf(150.dp) }
    var pane2Weight by remember { mutableFloatStateOf(0.5f) }

    val effectivePane1Expanded = if (autoCollapseUnusedPaneOnDrag != null) (autoCollapseUnusedPaneOnDrag == 1 || pane1Expanded) else pane1Expanded
    val effectivePane2Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 2 else pane2Expanded
    val effectivePane3Expanded = if (autoCollapseUnusedPaneOnDrag != null) autoCollapseUnusedPaneOnDrag == 3 else pane3Expanded

    val paneSpringDp = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.68f,
        stiffness = Spring.StiffnessMediumLow
    )
    val paneSpringFloat = spring<Float>(
        dampingRatio = 0.68f,
        stiffness = Spring.StiffnessMediumLow
    )

    val animatedPane1Width by animateDpAsState(
        targetValue = if (effectivePane1Expanded) pane1Width else 0.dp,
        animationSpec = paneSpringDp,
        label = "Pane1WidthSpring"
    )

    val targetPane2Weight = when {
        effectivePane2Expanded && showPane3 && effectivePane3Expanded -> pane2Weight.coerceIn(0.2f, 0.8f)
        effectivePane2Expanded -> 1f
        else -> 0f
    }
    val targetPane3Weight = when {
        showPane3 && effectivePane3Expanded && effectivePane2Expanded -> (1f - pane2Weight).coerceIn(0.2f, 0.8f)
        showPane3 && effectivePane3Expanded -> 1f
        else -> 0f
    }
    val animPane2Weight by animateFloatAsState(
        targetValue = targetPane2Weight,
        animationSpec = paneSpringFloat,
        label = "Pane2WeightSpring"
    )
    val animPane3Weight by animateFloatAsState(
        targetValue = targetPane3Weight,
        animationSpec = paneSpringFloat,
        label = "Pane3WeightSpring"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent)
    ) {
        if (!pane1WidthInitialized) {
            // 根目錄固定比例（約 24%，介於 180dp ~ 240dp，不開放變更）
            pane1Width = (maxWidth * 0.24f).coerceIn(180.dp, 240.dp)
            pane1WidthInitialized = true
        }

        val safePane1Width = animatedPane1Width.coerceAtLeast(0.dp)
        val safeW2 = animPane2Weight.coerceAtLeast(0f)
        val safeW3 = animPane3Weight.coerceAtLeast(0f)

        Row(modifier = Modifier.fillMaxSize()) {
            // 第一區：彈簧回彈展開/摺疊動畫；完全摺疊時消除寬度與邊界
            if (safePane1Width > 1.dp) {
                Box(
                    modifier = Modifier
                        .width(safePane1Width)
                        .fillMaxHeight()
                        .clipToBounds()
                        .onGloballyPositioned { coords ->
                            if (effectivePane1Expanded) {
                                val b = coords.boundsInRoot()
                                onPaneBoundsChanged(1, b.left, b.right)
                            }
                        }
                ) {
                    Column(
                        Modifier
                            .requiredWidth(pane1Width)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable(onClick = onOpenAiAssistant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI 檔案分析與整理",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "AI",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
                    if (safeW2 > 0.005f || keepAliveSourcePane == 2) {
                        val pane2WidthMod = if (safeW2 > 0.005f) Modifier.weight(safeW2) else Modifier.width(0.dp)
                        Box(
                            modifier = pane2WidthMod
                                .fillMaxHeight()
                                .clipToBounds()
                                .onGloballyPositioned { coords ->
                                    if (effectivePane2Expanded) {
                                        val b = coords.boundsInRoot()
                                        onPaneBoundsChanged(2, b.left, b.right)
                                    }
                                }
                        ) {
                            pane2Content { onPaneExpandedChange(2, false) }
                        }
                    }

                    // 僅在第二與第三區同時展開時才顯示分隔線 2
                    if (showPane3 && safeW2 > 0.02f && safeW3 > 0.02f) {
                        ResizableDivider(
                            editable = isRatioEditMode,
                            onDrag = { deltaPx ->
                                val totalPx = with(density) { totalWidth.toPx() }.coerceAtLeast(1f)
                                pane2Weight = (pane2Weight + (deltaPx / totalPx)).coerceIn(0.2f, 0.8f)
                            }
                        )
                    }

                    if (showPane3 && (safeW3 > 0.005f || keepAliveSourcePane == 3)) {
                        val pane3WidthMod = if (safeW3 > 0.005f) Modifier.weight(safeW3) else Modifier.width(0.dp)
                        Box(
                            modifier = pane3WidthMod
                                .fillMaxHeight()
                                .clipToBounds()
                                .onGloballyPositioned { coords ->
                                    if (effectivePane3Expanded) {
                                        val b = coords.boundsInRoot()
                                        onPaneBoundsChanged(3, b.left, b.right)
                                    }
                                }
                        ) {
                            pane3Content { onPaneExpandedChange(3, false) }
                        }
                    }
                }
            }
        }

        // 第一區摺疊後：左緣保留一個小半圓形展開按鈕（拖曳經過時放大提示）
        AnimatedVisibility(
            visible = !effectivePane1Expanded,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 56.dp)
        ) {
            val scale1 by animateFloatAsState(
                targetValue = if (hoveredExpandButtonPane == 1) 1.35f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "ExpandBtn1Scale"
            )
            DisposableEffect(Unit) {
                onDispose { onExpandButtonBoundsChanged(1, null) }
            }
            Surface(
                shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .scale(scale1)
                    .width(26.dp)
                    .height(48.dp)
                    .onGloballyPositioned { onExpandButtonBoundsChanged(1, it.boundsInRoot()) }
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

        // 第二區摺疊後：上方保留一個小半圓形展開按鈕（拖曳經過時放大提示）
        AnimatedVisibility(
            visible = !effectivePane2Expanded,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val scale2 by animateFloatAsState(
                targetValue = if (hoveredExpandButtonPane == 2) 1.35f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "ExpandBtn2Scale"
            )
            DisposableEffect(Unit) {
                onDispose { onExpandButtonBoundsChanged(2, null) }
            }
            Surface(
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .scale(scale2)
                    .width(48.dp)
                    .height(26.dp)
                    .onGloballyPositioned { onExpandButtonBoundsChanged(2, it.boundsInRoot()) }
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

        // 第三區摺疊後：右緣保留一個小半圓形展開按鈕（拖曳經過時放大提示）
        AnimatedVisibility(
            visible = showPane3 && !effectivePane3Expanded,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp)
        ) {
            val scale3 by animateFloatAsState(
                targetValue = if (hoveredExpandButtonPane == 3) 1.35f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "ExpandBtn3Scale"
            )
            DisposableEffect(Unit) {
                onDispose { onExpandButtonBoundsChanged(3, null) }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .scale(scale3)
                    .width(26.dp)
                    .height(48.dp)
                    .onGloballyPositioned { onExpandButtonBoundsChanged(3, it.boundsInRoot()) }
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
