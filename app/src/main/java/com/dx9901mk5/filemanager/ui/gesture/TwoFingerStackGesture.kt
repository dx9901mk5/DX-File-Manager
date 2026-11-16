package com.dx9901mk5.filemanager.ui.gesture

import android.content.Context
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
 * 1. 滑動過程 (> touchSlop) 立即放行，絕不誤入選擇或拖曳模式
 * 2. 觸控停住不動至少 2000ms (2秒) -> 觸發「大振動」並進入多選拖曳狀態
 * 3. 第一指按住不放時，第二指點擊其他檔案 -> 觸發「小震動」、加入選取與卡片疊合動畫
 * 4. 確實等到第一指放開且有拖曳位移或跨區時，才判定彈出「複製 / 移動 / 取消」視窗
 */
fun Modifier.dxFileTouchGesture(
    context: Context,
    findItemAtPosition: (Offset) -> String?,
    onEmptySpaceLongPress: (pressOffset: Offset) -> Unit = {},
    onStartDragSelection: (initialFilePath: String, startOffset: Offset) -> Unit,
    onSecondFingerTapItem: (tappedFilePath: String, tapOffset: Offset) -> Unit,
    onPrimaryFingerDrag: (currentOffset: Offset, hoveredFolderPath: String?) -> Unit,
    onDropComplete: (dropOffset: Offset, targetFolderPath: String?, didDrag: Boolean) -> Unit,
    onDragCancel: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val primaryPointerId: PointerId = firstDown.id
        val startPosition = firstDown.position
        val touchedFile = findItemAtPosition(startPosition)
        val touchSlop = viewConfiguration.touchSlop

        // 必須原地停住不動滿 2000ms；任何滑動或放開皆立即結束，不進入選擇模式
        val movedOrReleasedBefore2s = withTimeoutOrNull(2000L) {
            while (true) {
                val event = awaitPointerEvent()
                val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }
                if (primaryChange == null || !primaryChange.pressed) {
                    return@withTimeoutOrNull true
                }
                if ((primaryChange.position - startPosition).getDistance() > touchSlop) {
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }

        if (movedOrReleasedBefore2s != null) {
            return@awaitEachGesture
        }

        // 滿 2000ms 且原地不動 -> 給予大振動！
        DxHaptics.strongVibrate(context)

        if (touchedFile == null) {
            onEmptySpaceLongPress(startPosition)
            return@awaitEachGesture
        }

        // 進入多選＋拖曳狀態
        onStartDragSelection(touchedFile, startPosition)
        var isDraggingMode = true
        var didDragBeyondSlop = false

        while (isDraggingMode) {
            val event = awaitPointerEvent()
            val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }

            // 第二指點擊其他檔案 -> 小震動 + 疊合動畫
            event.changes
                .filter { it.id != primaryPointerId && it.pressed && !it.previousPressed }
                .forEach { secondFinger ->
                    secondFinger.consume()
                    val secondTappedFile = findItemAtPosition(secondFinger.position)
                    if (secondTappedFile != null) {
                        DxHaptics.lightVibrate(context)
                        onSecondFingerTapItem(secondTappedFile, secondFinger.position)
                    }
                }

            if (primaryChange == null || !primaryChange.pressed) {
                // 確實手指觸控放開才判定是否彈出複製/移動/取消
                val finalPos = primaryChange?.position ?: startPosition
                val targetFolder = findItemAtPosition(finalPos)
                onDropComplete(finalPos, targetFolder, didDragBeyondSlop)
                isDraggingMode = false
            } else if (primaryChange.positionChange() != Offset.Zero) {
                primaryChange.consume()
                if ((primaryChange.position - startPosition).getDistance() > touchSlop * 2f) {
                    didDragBeyondSlop = true
                }
                val hoveredItem = findItemAtPosition(primaryChange.position)
                onPrimaryFingerDrag(primaryChange.position, hoveredItem)
            }
        }
        if (isDraggingMode) onDragCancel()
    }
}
