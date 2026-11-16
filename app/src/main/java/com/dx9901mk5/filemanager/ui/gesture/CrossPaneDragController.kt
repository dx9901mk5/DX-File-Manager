package com.dx9901mk5.filemanager.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import kotlin.math.roundToInt

data class FlyingStackCard(
    val id: Long,
    val file: VfsFile,
    val startOffset: Offset
)

class CrossPaneDragController {
    var isDragging by mutableStateOf(false)
    var sourcePaneIndex by mutableStateOf(2)
    var draggedFiles by mutableStateOf<List<VfsFile>>(emptyList())
    var pointerRootOffset by mutableStateOf(Offset.Zero)
    var activePaneIndexDuringDrag by mutableStateOf<Int?>(null)
    var hoveredFolderPath by mutableStateOf<String?>(null)
    var flyingCard by mutableStateOf<FlyingStackCard?>(null)

    var pendingDropTarget by mutableStateOf<PendingDropRequest?>(null)

    private val paneBounds = mutableMapOf<Int, Pair<Float, Float>>()

    fun updatePaneBounds(paneIndex: Int, leftX: Float, rightX: Float) {
        paneBounds[paneIndex] = leftX to rightX
    }

    fun startDrag(paneIndex: Int, initialFile: VfsFile, rootOffset: Offset) {
        isDragging = true
        sourcePaneIndex = paneIndex
        draggedFiles = listOf(initialFile)
        pointerRootOffset = rootOffset
        activePaneIndexDuringDrag = paneIndex
        hoveredFolderPath = null
    }

    fun addSecondFingerFile(file: VfsFile, tapRootOffset: Offset) {
        if (draggedFiles.none { it.path == file.path }) {
            draggedFiles = draggedFiles + file
            flyingCard = FlyingStackCard(
                id = System.nanoTime(),
                file = file,
                startOffset = tapRootOffset
            )
        }
    }

    fun updateDrag(rootOffset: Offset, hoveredFolder: String?) {
        pointerRootOffset = rootOffset
        hoveredFolderPath = hoveredFolder
        val detectedPane = paneBounds.entries.firstOrNull { (_, range) ->
            rootOffset.x in range.first..range.second
        }?.key
        if (detectedPane != null) {
            activePaneIndexDuringDrag = detectedPane
        }
    }

    fun finishDrag(
        targetPaneIndex: Int,
        targetMountId: String,
        targetDirPath: String
    ) {
        if (isDragging && draggedFiles.isNotEmpty()) {
            pendingDropTarget = PendingDropRequest(
                files = draggedFiles,
                sourcePaneIndex = sourcePaneIndex,
                targetPaneIndex = targetPaneIndex,
                targetMountId = targetMountId,
                targetDirPath = targetDirPath
            )
        }
        resetDrag()
    }

    fun resetDrag() {
        isDragging = false
        activePaneIndexDuringDrag = null
        hoveredFolderPath = null
        flyingCard = null
    }
}

data class PendingDropRequest(
    val files: List<VfsFile>,
    val sourcePaneIndex: Int,
    val targetPaneIndex: Int,
    val targetMountId: String,
    val targetDirPath: String
)

@Composable
fun DragAndDropOverlay(controller: CrossPaneDragController) {
    if (!controller.isDragging || controller.draggedFiles.isEmpty()) return

    val count = controller.draggedFiles.size
    val leadFile = controller.draggedFiles.first()

    Box(modifier = Modifier.fillMaxSize()) {
        // 第二指點擊飛入第一指座標的卡片疊合動畫
        controller.flyingCard?.let { card ->
            val animOffset = remember(card.id) { Animatable(card.startOffset, Offset.VectorConverter) }
            LaunchedEffect(card.id) {
                animOffset.animateTo(
                    targetValue = controller.pointerRootOffset,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
                controller.flyingCard = null
            }
            Surface(
                modifier = Modifier
                    .offset { IntOffset(animOffset.value.x.roundToInt() - 60, animOffset.value.y.roundToInt() - 24) }
                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(card.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 跟隨第一指的堆疊預覽卡片 + 右上角數量徽章
        Box(
            modifier = Modifier.offset {
                IntOffset(
                    (controller.pointerRootOffset.x - 80).roundToInt(),
                    (controller.pointerRootOffset.y - 44).roundToInt()
                )
            }
        ) {
            if (count > 1) {
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = 6.dp)
                        .size(width = 180.dp, height = 46.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                )
            }
            Surface(
                modifier = Modifier
                    .size(width = 180.dp, height = 46.dp)
                    .shadow(12.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = leadFile.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun CopyOrMoveDialog(
    request: PendingDropRequest,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("拖曳放開確認") },
        text = {
            Text("要複製還是移動這 ${request.files.size} 個項目到此處？\n目標路徑：${request.targetDirPath}")
        },
        confirmButton = {
            Row {
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("複製")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onMove) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("移動")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
