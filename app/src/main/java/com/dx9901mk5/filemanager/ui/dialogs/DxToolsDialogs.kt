package com.dx9901mk5.filemanager.ui.dialogs

import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.server.DxWebShareServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FolderSizeEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val fraction: Float
)

@Composable
fun DxStorageAnalyzerDialog(
    targetDir: File = Environment.getExternalStorageDirectory(),
    onNavigateToFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<FolderSizeEntry>>(emptyList()) }
    var totalScannedBytes by remember { mutableStateOf(0L) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(targetDir) {
        scanning = true
        val (computed, total) = withContext(Dispatchers.IO) {
            val children = targetDir.listFiles().orEmpty().filter { it.name != ".dx_trash" }
            val sizes = children.map { child ->
                val sz = if (child.isDirectory) {
                    child.walkTopDown().maxDepth(4).filter { it.isFile }.sumOf { it.length() }
                } else {
                    child.length()
                }
                Triple(child.name, child.absolutePath, sz)
            }.sortedByDescending { it.third }
            val sum = sizes.sumOf { it.third }.coerceAtLeast(1L)
            sizes.take(20).map { (name, path, sz) ->
                FolderSizeEntry(
                    name = name,
                    path = path,
                    sizeBytes = sz,
                    fraction = (sz.toFloat() / sum.toFloat()).coerceIn(0f, 1f)
                )
            } to sum
        }
        entries = computed
        totalScannedBytes = total
        scanning = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DX 儲存空間分析器") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "掃描目錄：${targetDir.absolutePath}\n總計：${formatFileSize(totalScannedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (scanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("正在計算資料夾空間佔比...", style = MaterialTheme.typography.labelMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(entries) { item ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${formatFileSize(item.sizeBytes)} (${(item.fraction * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { item.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    )
}

@Composable
fun DxWebShareDialog(
    shareRoot: File = Environment.getExternalStorageDirectory(),
    onDismiss: () -> Unit
) {
    var running by remember { mutableStateOf(DxWebShareServer.isRunning) }
    var url by remember { mutableStateOf(DxWebShareServer.getAccessUrl()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DX 區域網路網頁分享 (WebShare)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "開啟後，同一 Wi-Fi 區網內的電腦或手機瀏覽器可透過以下網址直接瀏覽與下載檔案：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (running) url else "尚未啟動",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!running) {
                        Button(
                            onClick = {
                                DxWebShareServer.start(shareRoot)
                                    .onSuccess {
                                        url = it
                                        running = true
                                        error = null
                                    }
                                    .onFailure { error = "啟動失敗：${it.message}" }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("啟動網頁分享伺服器 (:8899)")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                DxWebShareServer.stop()
                                running = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("停止伺服器")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    )
}
