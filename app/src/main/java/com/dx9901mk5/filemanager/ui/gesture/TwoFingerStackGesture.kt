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
 * 2. 觸控停住不動滿 2000ms (2秒) -> 觸發「大振動」進入多選拖曳狀態
 * 3. 若滿 2 秒後「繼續完全不動」達 3000ms (總計 3 秒) 且未拖動/未第二指點擊 -> 彈出完整功能選單 (複製、移動、開啟方式等)
 * 4. 一旦開始拖動檔案，過程絕不判定觸發選單；第二指點擊給「小震動」並疊合；放開才詢問「複製 / 移動 / 取消」
 */
fun Modifier.dxFileTouchGesture(
    context: Context,
    findItemAtPosition: (Offset) -> String?,
    onLongPressStationary: (filePath: String, pressOffset: Offset) -> Unit,
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

        // 第 1 階段：必須原地停住不動滿 2000ms；任何滑動或放開皆立即結束
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

        // 滿 2 秒 -> 大振動並進入選擇狀態
        DxHaptics.strongVibrate(context)

        if (touchedFile == null) {
            onEmptySpaceLongPress(startPosition)
            return@awaitEachGesture
        }

        onStartDragSelection(touchedFile, startPosition)
        var isDraggingMode = true
        var didDragBeyondSlop = false
        var stillStationaryFor3s = true
        val holdStartMs = System.currentTimeMillis()

        // 第 2 階段：若繼續完全不動達 1000ms (總計 3 秒) 則彈出功能選單；一旦開始拖動或第二指點擊則永遠取消選單判定
        while (isDraggingMode) {
            val remainingFor3s = if (stillStationaryFor3s) {
                (1000L - (System.currentTimeMillis() - holdStartMs)).coerceAtLeast(1L)
            } else {
                Long.MAX_VALUE
            }

            val event = if (stillStationaryFor3s) {
                withTimeoutOrNull(remainingFor3s) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }

            if (event == null && stillStationaryFor3s) {
                // 滿 3 秒且完全沒拖動 -> 取消拖曳浮層，改彈出完整功能選單
                onDragCancel()
                DxHaptics.lightVibrate(context)
                onLongPressStationary(touchedFile, startPosition)
                return@awaitEachGesture
            }

            if (event == null) continue
            val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }

            // 第二指點擊其他檔案 -> 小震動 + 疊合動畫 + 取消 3 秒選單判定
            event.changes
                .filter { it.id != primaryPointerId && it.pressed && !it.previousPressed }
                .forEach { secondFinger ->
                    stillStationaryFor3s = false
                    secondFinger.consume()
                    val secondTappedFile = findItemAtPosition(secondFinger.position)
                    if (secondTappedFile != null) {
                        DxHaptics.lightVibrate(context)
                        onSecondFingerTapItem(secondTappedFile, secondFinger.position)
                    }
                }

            if (primaryChange == null || !primaryChange.pressed) {
                val finalPos = primaryChange?.position ?: startPosition
                val targetFolder = findItemAtPosition(finalPos)
                onDropComplete(finalPos, targetFolder, didDragBeyondSlop)
                isDraggingMode = false
            } else if (primaryChange.positionChange() != Offset.Zero) {
                if ((primaryChange.position - startPosition).getDistance() > touchSlop) {
                    stillStationaryFor3s = false
                    didDragBeyondSlop = true
                }
                primaryChange.consume()
                val hoveredItem = findItemAtPosition(primaryChange.position)
                onPrimaryFingerDrag(primaryChange.position, hoveredItem)
            }
        }
        if (isDraggingMode) onDragCancel()
    }
}
