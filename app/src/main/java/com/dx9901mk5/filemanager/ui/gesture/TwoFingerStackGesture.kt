package com.dx9901mk5.filemanager.ui.gesture

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * 雙指堆疊觸控手勢：
 * 1. 滑動過程 (> touchSlop) 立即放行，絕不誤入選擇或拖曳模式
 * 2. 觸控停住不動滿 2000ms (2秒) -> 觸發「大振動」進入多選拖曳狀態
 * 3. 若滿 2 秒後繼續完全不動達 3000ms (再停 1 秒) 且未拖動/未第二指點擊 -> 彈出完整功能選單
 * 4. 一旦開始拖動檔案，過程絕不判定觸發選單；第二指點擊給「小震動」並播放卡片飛入疊合動畫；放開才詢問「複製 / 移動 / 取消」
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

        // 第 1 階段：使用 AwaitPointerEventScope 內建 withTimeoutOrNull 監測原地停住 2000ms
        val movedOrReleasedBefore2s = withTimeoutOrNull(2000L) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
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

        // 滿 2 秒 -> 大振動並進入多選狀態
        DxHaptics.strongVibrate(context)

        if (touchedFile == null) {
            onEmptySpaceLongPress(startPosition)
            return@awaitEachGesture
        }

        onStartDragSelection(touchedFile, startPosition)
        var isDraggingMode = true
        var didDragBeyondSlop = false

        // 第 2 階段：再等待 1000ms（總計 3 秒）判斷是否完全不動以觸發功能選單；一旦動了或第二指點擊就跳入純拖曳迴圈
        val movedOrSecondFingerBefore3s = withTimeoutOrNull(1000L) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }

                val secondTaps = event.changes.filter {
                    it.id != primaryPointerId && it.pressed && !it.previousPressed
                }
                if (secondTaps.isNotEmpty()) {
                    secondTaps.forEach { secondFinger ->
                        secondFinger.consume()
                        val tappedPath = findItemAtPosition(secondFinger.position)
                        if (tappedPath != null) {
                            DxHaptics.lightVibrate(context)
                            onSecondFingerTapItem(tappedPath, secondFinger.position)
                        }
                    }
                    return@withTimeoutOrNull true
                }

                if (primaryChange == null || !primaryChange.pressed) {
                    onDropComplete(primaryChange?.position ?: startPosition, null, false)
                    isDraggingMode = false
                    return@withTimeoutOrNull true
                }

                if ((primaryChange.position - startPosition).getDistance() > touchSlop) {
                    didDragBeyondSlop = true
                    primaryChange.consume()
                    val hovered = findItemAtPosition(primaryChange.position)
                    onPrimaryFingerDrag(primaryChange.position, hovered)
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }

        // 若滿 3 秒完全沒動也沒第二指點擊 -> 取消拖曳浮層，彈出完整功能選單
        if (movedOrSecondFingerBefore3s == null) {
            onDragCancel()
            DxHaptics.lightVibrate(context)
            onLongPressStationary(touchedFile, startPosition)
            return@awaitEachGesture
        }

        // 第 3 階段：純拖曳 + 雙指點擊疊合迴圈（過程絕不觸發選單）
        while (isDraggingMode) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }

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
                val finalPos = primaryChange?.position ?: startPosition
                val targetFolder = findItemAtPosition(finalPos)
                onDropComplete(finalPos, targetFolder, didDragBeyondSlop)
                isDraggingMode = false
            } else if (primaryChange.positionChange() != Offset.Zero) {
                if ((primaryChange.position - startPosition).getDistance() > touchSlop) {
                    didDragBeyondSlop = true
                }
                primaryChange.consume()
                val hoveredItem = findItemAtPosition(primaryChange.position)
                onPrimaryFingerDrag(primaryChange.position, hoveredItem)
            }
        }
    }
}
