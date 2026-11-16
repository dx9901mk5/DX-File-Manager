package com.dx9901mk5.filemanager.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ShortcutEntry(val action: String, val keys: String, val note: String)

private val shortcutEntries = listOf(
    ShortcutEntry("全選 / 取消全選", "Ctrl + A / Esc", "標準選取控制"),
    ShortcutEntry("複製 / 剪下 / 貼上", "Ctrl + C / X / V", "支援跨第二區與第三區貼上"),
    ShortcutEntry("重新命名", "F2 或 Alt + R", "避開部分 Android 鍵盤無 F2"),
    ShortcutEntry("刪除 (垃圾桶 / 永久)", "Del / Shift + Del", "啟用垃圾桶時預設移入"),
    ShortcutEntry("傳送到另一面板", "F5 或 Ctrl+Shift+M", "第二區 ⇄ 第三區快速對傳"),
    ShortcutEntry("切換焦點面板", "Tab / Ctrl+← →", "避開 Alt+Tab 系統切換"),
    ShortcutEntry("摺疊 / 展開面板", "Ctrl + B / Ctrl + \\", "側欄 / 雙欄快速收合"),
    ShortcutEntry("壓縮 / 解壓縮", "Ctrl+Shift+Z / U", "呼叫 ZIP/7Z/TAR 引擎"),
    ShortcutEntry("加密 / 解密檔案", "Ctrl+Shift+E", "呼叫 AES-256 加密模組"),
    ShortcutEntry("檢視檔案屬性", "Alt+Enter / Ctrl+I", "大小、MD5/SHA256、權限"),
    ShortcutEntry("快捷鍵速查表", "Ctrl + / 或 F1", "開啟本說明視窗")
)

@Composable
fun ShortcutCheatSheetDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("鍵盤快捷鍵指南 (仿 Windows 無衝突版)") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shortcutEntries) { item ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.action,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = item.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = item.keys,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
