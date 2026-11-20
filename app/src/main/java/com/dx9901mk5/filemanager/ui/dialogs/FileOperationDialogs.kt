package com.dx9901mk5.filemanager.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import com.dx9901mk5.filemanager.core.settings.LocalDxStrings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.archive.ArchiveAndCryptoManager
import com.dx9901mk5.filemanager.core.archive.FileHashes
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import com.dx9901mk5.filemanager.core.vfs.VfsProtocol
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ContextMenuAction {
    MULTI_SELECT,
    RENAME,
    COPY,
    PASTE,
    COMPRESS_OR_EXTRACT,
    ENCRYPT_OR_DECRYPT,
    OPEN_WITH,
    RESET_DEFAULT_APP,
    OPEN_DIRECTORY,
    SEND_TO_OTHER_PANE,
    CREATE_SHORTCUT,
    INSTALL_WITH_SHIZUKU,
    PROPERTIES,
    DELETE
}

private data class MenuOption(
    val action: ContextMenuAction,
    val label: String,
    val icon: ImageVector
)

@Composable
fun FileContextMenuDialog(
    targetFile: VfsFile?,
    selectedCount: Int,
    hasClipboard: Boolean,
    hasApkSelected: Boolean = false,
    onAction: (ContextMenuAction) -> Unit,
    onDismiss: () -> Unit
) {
    val isArchive = targetFile?.name?.lowercase()?.let {
        it.endsWith(".zip") || it.endsWith(".7z") || it.endsWith(".tar") || it.endsWith(".gz") || it.endsWith(".tgz")
    } == true
    val isEncrypted = targetFile?.name?.lowercase()?.endsWith(".dxe") == true
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val showShizukuInstall = com.dx9901mk5.filemanager.core.shizuku.ShizukuApkInstaller.isShizukuPluginInstalled(ctx) &&
        (hasApkSelected || (targetFile?.isDirectory == false && targetFile.name.lowercase().endsWith(".apk")))

    val s = LocalDxStrings.current
    val options = listOfNotNull(
        MenuOption(ContextMenuAction.MULTI_SELECT, s.selectAll, Icons.Default.CheckBox),
        MenuOption(ContextMenuAction.RENAME, s.rename, Icons.Default.DriveFileRenameOutline),
        MenuOption(ContextMenuAction.COPY, s.copy, Icons.Default.ContentCopy),
        if (hasClipboard) MenuOption(ContextMenuAction.PASTE, s.paste, Icons.Default.ContentPaste) else null,
        if (showShizukuInstall) MenuOption(ContextMenuAction.INSTALL_WITH_SHIZUKU, "5. ${s.installWithShizuku}", Icons.Default.Android) else null,
        MenuOption(
            ContextMenuAction.COMPRESS_OR_EXTRACT,
            s.compress,
            Icons.Default.Archive
        ),
        MenuOption(
            ContextMenuAction.ENCRYPT_OR_DECRYPT,
            s.encryptDecrypt,
            Icons.Default.Lock
        ),
        MenuOption(ContextMenuAction.OPEN_WITH, s.open, Icons.Default.OpenInNew),
        if (targetFile?.isDirectory == false) MenuOption(ContextMenuAction.RESET_DEFAULT_APP, "重置預設開啟程式", Icons.Default.SwapHoriz) else null,
        MenuOption(ContextMenuAction.OPEN_DIRECTORY, s.goHome, Icons.Default.FolderOpen),
        MenuOption(ContextMenuAction.SEND_TO_OTHER_PANE, s.sendToOtherPane, Icons.Default.SwapHoriz),
        MenuOption(ContextMenuAction.CREATE_SHORTCUT, s.pinDesktopShortcut, Icons.Default.AddToHomeScreen),
        MenuOption(ContextMenuAction.PROPERTIES, s.properties, Icons.Default.Info),
        MenuOption(ContextMenuAction.DELETE, s.delete, Icons.Default.Delete)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = targetFile?.name ?: s.selectedCountLabel(selectedCount),
                maxLines = 1
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                items(options) { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onAction(opt.action)
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = opt.icon,
                            contentDescription = opt.label,
                            tint = if (opt.action == ContextMenuAction.DELETE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = opt.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (opt.action == ContextMenuAction.DELETE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    )
}

@Composable
fun RenameDialog(
    initialName: String,
    isBatch: Boolean,
    onConfirm: (newNameOrPrefix: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBatch) "批次重新命名 (輸入前綴)" else "重新命名") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(if (isBatch) "前綴名稱 (將自動加上序號)" else "新名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()); onDismiss() }
            ) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun PasswordInputDialog(
    title: String,
    label: String,
    allowEmpty: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = allowEmpty || password.isNotEmpty(),
                onClick = { onConfirm(password); onDismiss() }
            ) { Text("執行") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun NewFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("新資料夾") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立新資料夾") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("資料夾名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = folderName.isNotBlank(),
                onClick = { onConfirm(folderName.trim()); onDismiss() }
            ) { Text("建立") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun FilePropertiesDialog(
    file: VfsFile,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hashes by remember { mutableStateOf<FileHashes?>(null) }
    var computing by remember { mutableStateOf(false) }

    val dateStr = remember(file.lastModified) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("檔案屬性") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("名稱：${file.name}", style = MaterialTheme.typography.bodyMedium)
                Text("路徑：${file.path}", style = MaterialTheme.typography.bodySmall)
                Text("類型：${if (file.isDirectory) "資料夾" else file.mimeType}", style = MaterialTheme.typography.bodySmall)
                Text("大小：${formatFileSize(file.size)} (${file.size} Bytes)", style = MaterialTheme.typography.bodySmall)
                Text("修改時間：$dateStr", style = MaterialTheme.typography.bodySmall)

                if (file.protocol == VfsProtocol.LOCAL && !file.isDirectory) {
                    val f = File(file.path)
                    Text(
                        "權限：可讀=${f.canRead()} / 可寫=${f.canWrite()} / 執行=${f.canExecute()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    if (hashes == null) {
                        OutlinedButton(
                            enabled = !computing,
                            onClick = {
                                computing = true
                                scope.launch {
                                    hashes = ArchiveAndCryptoManager.computeHashes(f).getOrNull()
                                    computing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (computing) "計算雜湊中..." else "計算 MD5 / SHA-1 / SHA-256")
                        }
                    } else {
                        val h = hashes!!
                        Text("MD5: ${h.md5}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                        Text("SHA-1: ${h.sha1}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                        Text("SHA-256: ${h.sha256}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        }
    )
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
