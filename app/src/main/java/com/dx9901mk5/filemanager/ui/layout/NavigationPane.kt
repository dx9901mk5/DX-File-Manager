package com.dx9901mk5.filemanager.ui.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.vfs.FileCategory
import com.dx9901mk5.filemanager.core.vfs.StorageSpaceInfo
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.ui.dialogs.formatFileSize

@Composable
fun NavigationPane(
    localStorageSpace: StorageSpaceInfo?,
    cloudMounts: List<VfsMountConfig>,
    bookmarks: List<String>,
    trashCount: Int,
    onGoHome: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDualPane: () -> Unit,
    onOpenLocalRoot: () -> Unit,
    onOpenCloudMount: (VfsMountConfig) -> Unit,
    onOpenCloudWizard: () -> Unit,
    onOpenCategory: (FileCategory) -> Unit,
    onOpenBookmark: (String) -> Unit,
    onToggleHidden: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenWebShare: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 頂部工具列：首頁、主題切換、設定、分割畫面切換、快捷鍵
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onGoHome) {
                Icon(Icons.Default.Home, contentDescription = "首頁")
            }
            IconButton(onClick = onToggleTheme) {
                Icon(Icons.Default.DarkMode, contentDescription = "切換深淺主題")
            }
            IconButton(onClick = onToggleDualPane) {
                Icon(Icons.Default.Splitscreen, contentDescription = "切換雙欄")
            }
            IconButton(onClick = onOpenShortcuts) {
                Icon(Icons.Default.Keyboard, contentDescription = "快捷鍵指南")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "設定與關於")
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                SectionHeader("儲存裝置")
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLocalRoot() }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "內部儲存空間",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (localStorageSpace != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { localStorageSpace.usedFraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "可用 ${formatFileSize(localStorageSpace.availableBytes)} / 共 ${formatFileSize(localStorageSpace.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(cloudMounts, key = { it.id }) { mount ->
                NavRowItem(
                    icon = Icons.Default.Cloud,
                    label = mount.name,
                    subtitle = mount.protocol.displayName,
                    onClick = { onOpenCloudMount(mount) }
                )
            }

            item {
                NavRowItem(
                    icon = Icons.Default.Add,
                    label = "新增雲端 / 網路掛載",
                    onClick = onOpenCloudWizard
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                SectionHeader("檔案分類 & 專區")
            }

            val categories = listOf(
                FileCategory.MEDIA to Icons.Default.PhotoLibrary,
                FileCategory.AUDIO to Icons.Default.AudioFile,
                FileCategory.DOCUMENTS to Icons.Default.Description,
                FileCategory.APKS to Icons.Default.Android,
                FileCategory.ARCHIVES to Icons.Default.Archive,
                FileCategory.RECENT to Icons.Default.History,
                FileCategory.APPS to Icons.Default.Android,
                FileCategory.TRASH to Icons.Default.Delete
            )

            items(categories) { (category, icon) ->
                NavRowItem(
                    icon = icon,
                    label = category.title,
                    badgeCount = if (category == FileCategory.TRASH && trashCount > 0) trashCount else null,
                    onClick = { onOpenCategory(category) }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                SectionHeader("書籤")
            }

            items(bookmarks) { path ->
                NavRowItem(
                    icon = Icons.Default.Bookmark,
                    label = path.substringAfterLast('/').ifBlank { path },
                    subtitle = path,
                    onClick = { onOpenBookmark(path) }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                SectionHeader("工具箱")
                NavRowItem(
                    icon = Icons.Default.PieChart,
                    label = "DX 儲存空間分析器",
                    onClick = onOpenStorageAnalyzer
                )
                NavRowItem(
                    icon = Icons.Default.WifiTethering,
                    label = "DX 區域網路網頁分享",
                    onClick = onOpenWebShare
                )
                NavRowItem(
                    icon = Icons.Default.VisibilityOff,
                    label = "切換顯示隱藏資料夾",
                    onClick = onToggleHidden
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
    )
}

@Composable
private fun NavRowItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badgeCount != null) {
            Badge { Text(badgeCount.toString()) }
        }
    }
}
