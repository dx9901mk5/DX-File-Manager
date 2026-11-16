package com.dx9901mk5.filemanager.ui.gesture

import android.content.Context
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * 多選與雙指堆疊觸控手勢：
 * 1. 滑動過程 (> touchSlop) 立即放行，絕不誤入選擇或拖曳模式
 * 2. 長壓原地停住滿 1500ms (1.5秒) -> 大振動進入多選狀態，底部顯示操作選單
 * 3. 手指未離開時，第二指點擊其他檔案 -> 連續多選並播放檔案聚合飛入動畫，底部選單暫時隱藏
 * 4. 第一指開始挪動 (> touchSlop) -> 進入多檔案拖曳狀態（資料夾顯示短暫放大鏡特效，移至分區展開鈕可展開目標分區），放開詢問複製或移動
 * 5. 未拖曳而原地放開手指 -> 保留多選狀態並顯示優先含「新增資料夾 / 壓縮」的底部選單
 */
fun Modifier.dxFileTouchGesture(
    context: Context,
    hapticView: View? = null,
    findItemAtPosition: (Offset) -> String?,
    onEmptySpaceLongPress: (pressOffset: Offset) -> Unit = {},
    onLongPressEnterMultiSelect: (initialFilePath: String, startOffset: Offset) -> Unit,
    onSecondFingerTapItem: (tappedFilePath: String, tapOffset: Offset) -> Unit,
    onSweepSelectItem: (sweptFilePath: String, sweepOffset: Offset) -> Unit = { _, _ -> },
    onPrimaryFingerDrag: (currentOffset: Offset, hoveredFolderPath: String?) -> Unit,
    onDropComplete: (dropOffset: Offset, targetFolderPath: String?, didDrag: Boolean) -> Unit,
    onStationaryRelease: (initialFilePath: String, usedSecondFinger: Boolean) -> Unit,
    onCancelDrag: () -> Unit = {}
): Modifier = composed {
    val currentFindItem by rememberUpdatedState(findItemAtPosition)
    val currentOnEmptySpaceLongPress by rememberUpdatedState(onEmptySpaceLongPress)
    val currentOnLongPressEnterMultiSelect by rememberUpdatedState(onLongPressEnterMultiSelect)
    val currentOnSecondFingerTapItem by rememberUpdatedState(onSecondFingerTapItem)
    val currentOnSweepSelectItem by rememberUpdatedState(onSweepSelectItem)
    val currentOnPrimaryFingerDrag by rememberUpdatedState(onPrimaryFingerDrag)
    val currentOnDropComplete by rememberUpdatedState(onDropComplete)
    val currentOnStationaryRelease by rememberUpdatedState(onStationaryRelease)
    val currentOnCancelDrag by rememberUpdatedState(onCancelDrag)

    this.pointerInput(Unit) {
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val primaryPointerId: PointerId = firstDown.id
        val startPosition = firstDown.position
        val touchedFile = currentFindItem(startPosition)
        val touchSlop = viewConfiguration.touchSlop
        val holdSlop = touchSlop * 1.8f

        // 第 1 階段：原地停住滿 1500ms (1.5 秒)
        val movedOrReleasedBeforeHold = withTimeoutOrNull(1500L) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }
                if (primaryChange == null || !primaryChange.pressed) {
                    return@withTimeoutOrNull true
                }
                if ((primaryChange.position - startPosition).getDistance() > holdSlop) {
                    return@withTimeoutOrNull true
                }
            }
            @Suppress("UNREACHABLE_CODE")
            true
        }

        if (movedOrReleasedBeforeHold != null) {
            return@awaitEachGesture
        }

        // 滿 1.5 秒 -> 大振動並進入多選狀態
        DxHaptics.strongVibrate(context, hapticView)

        if (touchedFile == null) {
            currentOnEmptySpaceLongPress(startPosition)
            return@awaitEachGesture
        }

        currentOnLongPressEnterMultiSelect(touchedFile, startPosition)
        var isHolding = true
        var didDragBeyondSlop = false
        var usedSecondFinger = false
        val visitedInSweep = mutableSetOf(touchedFile)

        // 第 2 階段：持續按住監聽第二指多選點擊、第一指挪動拖曳、或原地放開
        try {
            while (isHolding) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val primaryChange = event.changes.firstOrNull { it.id == primaryPointerId }

                event.changes
                    .filter { it.id != primaryPointerId && it.pressed && !it.previousPressed }
                    .forEach { secondFinger ->
                        secondFinger.consume()
                        val secondTappedFile = currentFindItem(secondFinger.position)
                        if (secondTappedFile != null) {
                            usedSecondFinger = true
                            DxHaptics.lightVibrate(context, hapticView)
                            currentOnSecondFingerTapItem(secondTappedFile, secondFinger.position)
                        }
                    }

                if (primaryChange == null || !primaryChange.pressed) {
                    primaryChange?.consume()
                    val finalPos = primaryChange?.position ?: startPosition
                    isHolding = false
                    if (didDragBeyondSlop) {
                        val targetFolder = currentFindItem(finalPos)
                        currentOnDropComplete(finalPos, targetFolder, true)
                    } else {
                        currentOnStationaryRelease(touchedFile, usedSecondFinger)
                    }
                } else if (primaryChange.positionChange() != Offset.Zero) {
                    if (!didDragBeyondSlop && (primaryChange.position - startPosition).getDistance() > touchSlop) {
                        didDragBeyondSlop = true
                    }
                    if (didDragBeyondSlop) {
                        primaryChange.consume()
                        val hoveredItem = currentFindItem(primaryChange.position)
                        if (hoveredItem != null && visitedInSweep.add(hoveredItem)) {
                            DxHaptics.lightVibrate(context, hapticView)
                            currentOnSweepSelectItem(hoveredItem, primaryChange.position)
                        }
                        currentOnPrimaryFingerDrag(primaryChange.position, hoveredItem)
                    }
                }
            }
        } finally {
            if (isHolding) {
                currentOnCancelDrag()
            }
        }
    }
}
}
