package com.dx9901mk5.filemanager.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 雙指堆疊觸控手勢：
 * 1. 第一指長壓不動滿 1000ms -> 彈出 11 項功能列表 (onLongPressStationary)
 * 2. 長壓期間微幅移動 (< 1000ms 內超過 touchSlop) -> 進入多選拖曳狀態 (onStartDragSelection)
 * 3. 第一指按住不放時，第二指點擊其他檔案 -> 觸發加入選取與卡片疊合動畫 (onSecondFingerTapItem)
 * 4. 跨區移動與空白處/目標資料夾放開 -> 彈出「複製或移動」詢問視窗 (onDropComplete)
 */
fun Modifier.dxFileTouchGesture(
    findItemAtPosition: (Offset) -> String?,
    onLongPressStationary: (filePath: String, pressOffset: Offset) -> Unit,
    onStartDragSelection: (initialFilePath: String, startOffset: Offset) -> Unit,
    onSecondFingerTapItem: (tappedFilePath: String, tapOffset: Offset) -> Unit,
    onPrimaryFingerDrag: (currentOffset: Offset, hoveredFolderPath: String?) -> Unit,
    onDropComplete: (dropOffset: Offset, targetFolderPath: String?) -> Unit,
    onDragCancel: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val primaryPointerId: PointerId = firstDown.id
        val startPosition = firstDown.position
        val touchedFile = findItemAtPosition(startPosition) ?: return@awaitEachGesture

        var isDraggingMode = false
        val touchSlop = viewConfiguration.touchSlop

        val movedSlightlyBeforeTimeout = withTimeoutOrNull(1000L) {
            while (true) {
                val event = awaitPointerEvent()
                val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }
                if (primaryChange == null || !primaryChange.pressed) {
                    return@withTimeoutOrNull false
                }
                if ((primaryChange.position - startPosition).getDistance() > touchSlop) {
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }

        when (movedSlightlyBeforeTimeout) {
            null -> {
                // 滿 1000ms 且原地不動 -> 觸發功能選單
                onLongPressStationary(touchedFile, startPosition)
                return@awaitEachGesture
            }
            true -> {
                isDraggingMode = true
                onStartDragSelection(touchedFile, startPosition)
            }
            false -> return@awaitEachGesture
        }

        while (isDraggingMode) {
            val event = awaitPointerEvent()
            val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }

            // 偵測第二根手指點擊其他檔案
            event.changes
                .filter { it.id != primaryPointerId && it.pressed && !it.previousPressed }
                .forEach { secondFinger ->
                    secondFinger.consume()
                    val secondTappedFile = findItemAtPosition(secondFinger.position)
                    if (secondTappedFile != null) {
                        onSecondFingerTapItem(secondTappedFile, secondFinger.position)
                    }
                }

            if (primaryChange == null || !primaryChange.pressed) {
                val finalPos = primaryChange?.position ?: startPosition
                val targetFolder = findItemAtPosition(finalPos)
                onDropComplete(finalPos, targetFolder)
                isDraggingMode = false
            } else if (primaryChange.positionChange() != Offset.Zero) {
                primaryChange.consume()
                val hoveredItem = findItemAtPosition(primaryChange.position)
                onPrimaryFingerDrag(primaryChange.position, hoveredItem)
            }
        }
        if (isDraggingMode) onDragCancel()
    }
}
