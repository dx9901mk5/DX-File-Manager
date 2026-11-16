package com.dx9901mk5.filemanager.ui.gesture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.min

/**
 * 滑鼠框選 (Rubber-band Selection)、右鍵選單、Ctrl+滾輪縮放與側鍵返回/前進
 */
fun Modifier.dxMouseHandler(
    onRightClick: (Offset) -> Unit,
    onRubberBandStart: (Offset) -> Unit,
    onRubberBandUpdate: (Rect) -> Unit,
    onRubberBandEnd: () -> Unit,
    onCtrlWheelZoom: (Float) -> Unit,
    onMouseBack: () -> Unit,
    onMouseForward: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        var isRubberBanding = false
        var startPos = Offset.Zero

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue

            when (event.type) {
                PointerEventType.Press -> {
                    if (event.buttons.isSecondaryPressed) {
                        change.consume()
                        onRightClick(change.position)
                    } else if (event.buttons.isBackPressed) {
                        change.consume()
                        onMouseBack()
                    } else if (event.buttons.isForwardPressed) {
                        change.consume()
                        onMouseForward()
                    } else if (event.buttons.isPrimaryPressed &&
                        change.type == androidx.compose.ui.input.pointer.PointerType.Mouse
                    ) {
                        isRubberBanding = true
                        startPos = change.position
                        onRubberBandStart(startPos)
                    }
                }

                PointerEventType.Move -> {
                    if (isRubberBanding && event.buttons.isPrimaryPressed) {
                        val cur = change.position
                        val rect = Rect(
                            left = min(startPos.x, cur.x),
                            top = min(startPos.y, cur.y),
                            right = min(startPos.x, cur.x) + abs(cur.x - startPos.x),
                            bottom = min(startPos.y, cur.y) + abs(cur.y - startPos.y)
                        )
                        onRubberBandUpdate(rect)
                    }
                }

                PointerEventType.Release -> {
                    if (isRubberBanding) {
                        isRubberBanding = false
                        onRubberBandEnd()
                    }
                }

                PointerEventType.Scroll -> {
                    if (event.keyboardModifiers.isCtrlPressed) {
                        val deltaY = change.scrollDelta.y
                        if (deltaY != 0f) {
                            change.consume()
                            onCtrlWheelZoom(-deltaY)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RubberBandOverlay(rect: Rect?) {
    if (rect == null || rect.width < 4f || rect.height < 4f) return
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val strokeColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            color = fillColor,
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height)
        )
        drawRect(
            color = strokeColor,
            topLeft = rect.topLeft,
            size = Size(rect.width, rect.height),
            style = Stroke(width = 2f)
        )
    }
}

enum class DxShortcutAction {
    SELECT_ALL,
    CLEAR_SELECTION,
    COPY,
    CUT,
    PASTE,
    RENAME,
    DELETE_TO_TRASH,
    DELETE_PERMANENT,
    SEND_TO_OTHER_PANE,
    CYCLE_FOCUS_PANE,
    TOGGLE_SIDEBAR,
    TOGGLE_DUAL_PANE,
    COMPRESS,
    EXTRACT,
    ENCRYPT_DECRYPT,
    PROPERTIES,
    SHOW_SHORTCUT_CHEATSHEET
}

/**
 * 仿 Windows 無衝突快捷鍵攔截器
 */
fun Modifier.dxKeyboardShortcuts(
    onAction: (DxShortcutAction) -> Unit
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    val ctrl = event.isCtrlPressed
    val shift = event.isShiftPressed
    val alt = event.isAltPressed

    val action = when {
        ctrl && !shift && event.key == Key.A -> DxShortcutAction.SELECT_ALL
        event.key == Key.Escape -> DxShortcutAction.CLEAR_SELECTION
        ctrl && !shift && event.key == Key.C -> DxShortcutAction.COPY
        ctrl && !shift && event.key == Key.X -> DxShortcutAction.CUT
        ctrl && !shift && event.key == Key.V -> DxShortcutAction.PASTE
        event.key == Key.F2 || (alt && event.key == Key.R) -> DxShortcutAction.RENAME
        shift && event.key == Key.Delete -> DxShortcutAction.DELETE_PERMANENT
        event.key == Key.Delete || (ctrl && event.key == Key.D) -> DxShortcutAction.DELETE_TO_TRASH
        event.key == Key.F5 || (ctrl && shift && event.key == Key.M) -> DxShortcutAction.SEND_TO_OTHER_PANE
        event.key == Key.Tab || (ctrl && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) ->
            DxShortcutAction.CYCLE_FOCUS_PANE
        ctrl && event.key == Key.B -> DxShortcutAction.TOGGLE_SIDEBAR
        ctrl && event.key == Key.Backslash -> DxShortcutAction.TOGGLE_DUAL_PANE
        ctrl && shift && event.key == Key.Z -> DxShortcutAction.COMPRESS
        ctrl && shift && event.key == Key.U -> DxShortcutAction.EXTRACT
        ctrl && shift && event.key == Key.E -> DxShortcutAction.ENCRYPT_DECRYPT
        (alt && event.key == Key.Enter) || (ctrl && event.key == Key.I) -> DxShortcutAction.PROPERTIES
        (ctrl && event.key == Key.Slash) || event.key == Key.F1 -> DxShortcutAction.SHOW_SHORTCUT_CHEATSHEET
        else -> null
    }

    if (action != null) {
        onAction(action)
        true
    } else {
        false
    }
}
