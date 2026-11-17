package com.dx9901mk5.filemanager.ui.layout

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.vfs.FileCategory
import com.dx9901mk5.filemanager.core.vfs.StorageSpaceInfo
import com.dx9901mk5.filemanager.core.vfs.VfsMountConfig
import com.dx9901mk5.filemanager.ui.dialogs.formatFileSize
import com.dx9901mk5.filemanager.ui.gesture.CrossPaneDragController
import com.dx9901mk5.filemanager.ui.gesture.dxVerticalOverscrollBounce
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavigationPane(
    localStorageSpace: StorageSpaceInfo?,
    cloudMounts: List<VfsMountConfig>,
    bookmarks: List<String>,
    trashCount: Int,
    showHiddenFiles: Boolean = false,
    dragController: CrossPaneDragController? = null,
    onGoHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDualPane: () -> Unit,
    onOpenLocalRoot: () -> Unit,
    onOpenCloudMount: (VfsMountConfig) -> Unit,
    onRemoveCloudMount: (String) -> Unit,
    onOpenCloudWizard: () -> Unit,
    onOpenCategory: (FileCategory) -> Unit,
    onOpenBookmark: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit = {}
) {
    val localRootPath = remember { Environment.getExternalStorageDirectory().absolutePath }
    var internalExpanded by remember { mutableStateOf(false) }
    var pendingRemoveBookmark by remember { mutableStateOf<String?>(null) }

    val rootFolders = remember(internalExpanded, showHiddenFiles) {
        if (!internalExpanded) emptyList()
        else {
            File(localRootPath).listFiles()
                ?.filter { it.isDirectory && (showHiddenFiles || !it.name.startsWith(".")) }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 左上角工具列：齒輪設定、房子首頁、雙欄切換
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "設定與關於",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onGoHome) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "首頁",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onToggleDualPane) {
                    Icon(
                        Icons.Default.Splitscreen,
                        contentDescription = "切換雙欄",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .dxVerticalOverscrollBounce()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    SectionHeader("儲存裝置")
                    val isRootHovered = dragController != null &&
                        dragController.isDragging &&
                        dragController.hoveredFolderPaneIndex == 1 &&
                        dragController.hoveredFolderPath == localRootPath
                    val rootHoverScale by animateFloatAsState(
                        targetValue = if (isRootHovered) 1.05f else 1f,
                        label = "RootHoverScale"
                    )
                    Box(contentAlignment = Alignment.CenterEnd) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isRootHovered) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(rootHoverScale)
                                .then(
                                    if (isRootHovered) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                    else Modifier
                                )
                                .onGloballyPositioned { coords ->
                                    dragController?.updateFolderBounds(1, localRootPath, coords.boundsInRoot())
                                }
                                .clickable { onOpenLocalRoot() }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "內部儲存空間",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { internalExpanded = !internalExpanded },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (internalExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                            contentDescription = if (internalExpanded) "收合根目錄資料夾" else "展開根目錄資料夾",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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
                        NavMagnifierBadge(
                            visible = isRootHovered,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                }

                if (internalExpanded) {
                    items(rootFolders, key = { "root_sub_${it.absolutePath}" }) { folder ->
                        val folderPath = folder.absolutePath
                        val isHovered = dragController != null &&
                            dragController.isDragging &&
                            dragController.hoveredFolderPaneIndex == 1 &&
                            dragController.hoveredFolderPath == folderPath
                        val hoverScale by animateFloatAsState(
                            targetValue = if (isHovered) 1.07f else 1f,
                            label = "RootSubHoverScale"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp)
                                .scale(hoverScale)
                                .then(
                                    if (isHovered) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .onGloballyPositioned { coords ->
                                    dragController?.updateFolderBounds(1, folderPath, coords.boundsInRoot())
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenBookmark(folderPath) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                NavMagnifierBadge(visible = isHovered)
                            }
                        }
                    }
                }

                items(cloudMounts, key = { it.id }) { mount ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            NavRowItem(
                                icon = Icons.Default.Cloud,
                                label = mount.name,
                                subtitle = mount.protocol.displayName,
                                onClick = { onOpenCloudMount(mount) }
                            )
                        }
                        IconButton(
                            onClick = { onRemoveCloudMount(mount.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "移除掛載",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
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
                    FileCategory.MEDIA,
                    FileCategory.AUDIO,
                    FileCategory.DOCUMENTS,
                    FileCategory.APKS,
                    FileCategory.ARCHIVES,
                    FileCategory.RECENT,
                    FileCategory.APPS,
                    FileCategory.TRASH
                )

                items(categories) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCategory(category) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryCustomIcon(category)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (category == FileCategory.TRASH && trashCount > 0) {
                            Badge { Text(trashCount.toString()) }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SectionHeader("書籤（長壓可移除）")
                }

                items(bookmarks, key = { "bm_$it" }) { path ->
                    val isBmHovered = dragController != null &&
                        dragController.isDragging &&
                        dragController.hoveredFolderPaneIndex == 1 &&
                        dragController.hoveredFolderPath == path
                    val bmScale by animateFloatAsState(
                        targetValue = if (isBmHovered) 1.07f else 1f,
                        label = "BmHoverScale"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(bmScale)
                            .then(
                                if (isBmHovered) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .onGloballyPositioned { coords ->
                                dragController?.updateFolderBounds(1, path, coords.boundsInRoot())
                            }
                            .combinedClickable(
                                onClick = { onOpenBookmark(path) },
                                onLongClick = { pendingRemoveBookmark = path }
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = path.substringAfterLast('/').ifBlank { path },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            NavMagnifierBadge(visible = isBmHovered)
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    pendingRemoveBookmark?.let { targetPath ->
        val name = targetPath.substringAfterLast('/').ifBlank { targetPath }
        AlertDialog(
            onDismissRequest = { pendingRemoveBookmark = null },
            title = { Text("移除書籤") },
            text = { Text("確定要移除書籤「$name」嗎？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveBookmark(targetPath)
                        pendingRemoveBookmark = null
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveBookmark = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CategoryCustomIcon(category: FileCategory) {
    when (category) {
        FileCategory.MEDIA -> GooglePhotosPinwheelIcon(modifier = Modifier.size(20.dp))
        FileCategory.AUDIO -> Icon(
            Icons.Default.AudioFile,
            contentDescription = null,
            tint = Color(0xFFE53935),
            modifier = Modifier.size(20.dp)
        )
        FileCategory.DOCUMENTS -> Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = Color(0xFF1E88E5),
            modifier = Modifier.size(20.dp)
        )
        FileCategory.APKS -> Icon(
            Icons.Default.Android,
            contentDescription = null,
            tint = Color(0xFF43A047),
            modifier = Modifier.size(20.dp)
        )
        FileCategory.ARCHIVES -> ZipArchiveColorIcon(modifier = Modifier.size(20.dp))
        FileCategory.RECENT -> Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = Color(0xFF00ACC1),
            modifier = Modifier.size(20.dp)
        )
        FileCategory.APPS -> Icon(
            Icons.Default.Android,
            contentDescription = null,
            tint = Color(0xFF1E88E5),
            modifier = Modifier.size(20.dp)
        )
        FileCategory.TRASH -> WindowsRecycleBinIcon(modifier = Modifier.size(20.dp))
        else -> Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun GooglePhotosPinwheelIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.45f
        // 上（紅）
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r * 0.5f, cy - r),
            size = Size(r, r)
        )
        // 右（黃）
        drawArc(
            color = Color(0xFFFBBC04),
            startAngle = 270f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx, cy - r * 0.5f),
            size = Size(r, r)
        )
        // 下（綠）
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r * 0.5f, cy),
            size = Size(r, r)
        )
        // 左（藍）
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r * 0.5f),
            size = Size(r, r)
        )
    }
}

@Composable
private fun ZipArchiveColorIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // 經典 ZIP 黃色資料夾主體 + 橘棕拉鍊條
        drawRoundRect(
            color = Color(0xFFFFB300),
            topLeft = Offset(w * 0.1f, h * 0.12f),
            size = Size(w * 0.8f, h * 0.78f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
        )
        drawRect(
            color = Color(0xFF8D4E00),
            topLeft = Offset(w * 0.42f, h * 0.12f),
            size = Size(w * 0.16f, h * 0.56f)
        )
        for (i in 0..3) {
            val y = h * (0.18f + i * 0.11f)
            drawLine(
                color = Color(0xFFFFF8E1),
                start = Offset(w * 0.44f, y),
                end = Offset(w * 0.56f, y),
                strokeWidth = h * 0.055f,
                cap = StrokeCap.Round
            )
        }
        drawRoundRect(
            color = Color(0xFFFFE082),
            topLeft = Offset(w * 0.39f, h * 0.62f),
            size = Size(w * 0.22f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
    }
}

@Composable
private fun WindowsRecycleBinIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Windows 資源回收桶白綠相間風格
        val bodyPath = Path().apply {
            moveTo(w * 0.20f, h * 0.26f)
            lineTo(w * 0.80f, h * 0.26f)
            lineTo(w * 0.72f, h * 0.90f)
            lineTo(w * 0.28f, h * 0.90f)
            close()
        }
        drawPath(bodyPath, color = Color(0xFFF5F7F5))
        drawPath(bodyPath, color = Color(0xFF43A047), style = Stroke(width = w * 0.07f))
        // 綠色桶緣上蓋條
        drawRoundRect(
            color = Color(0xFF2E7D32),
            topLeft = Offset(w * 0.15f, h * 0.16f),
            size = Size(w * 0.70f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        // 桶身綠色條紋裝飾
        drawLine(
            color = Color(0xFF43A047),
            start = Offset(w * 0.40f, h * 0.38f),
            end = Offset(w * 0.42f, h * 0.78f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF43A047),
            start = Offset(w * 0.60f, h * 0.38f),
            end = Offset(w * 0.58f, h * 0.78f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun NavMagnifierBadge(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "拖曳目標資料夾",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
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
