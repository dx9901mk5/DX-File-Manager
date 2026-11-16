package com.dx9901mk5.filemanager.ui.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dx9901mk5.filemanager.core.shizuku.InstalledAppInfo
import com.dx9901mk5.filemanager.core.trash.TrashItemEntity
import com.dx9901mk5.filemanager.core.vfs.VfsFile
import com.dx9901mk5.filemanager.ui.ExplorerPaneState
import com.dx9901mk5.filemanager.ui.PaneMode
import com.dx9901mk5.filemanager.ui.dialogs.formatFileSize
import com.dx9901mk5.filemanager.ui.gesture.CrossPaneDragController
import com.dx9901mk5.filemanager.ui.gesture.RubberBandOverlay
import com.dx9901mk5.filemanager.ui.gesture.dxFileTouchGesture
import com.dx9901mk5.filemanager.ui.gesture.dxMouseHandler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileExplorerPane(
    state: ExplorerPaneState,
    isFocused: Boolean,
    dragController: CrossPaneDragController,
    installedApps: List<InstalledAppInfo>,
    includeSystemApps: Boolean,
    selectedAppPackages: Set<String>,
    trashItems: List<TrashItemEntity>,
    onFocusPane: () -> Unit,
    onCollapsePane: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onOpenFile: (VfsFile) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRubberBandSelect: (Set<String>) -> Unit,
    onShowContextMenu: (VfsFile?) -> Unit,
    onSearchChange: (String) -> Unit,
    onCtrlWheelZoom: (Float) -> Unit,
    onAddBookmark: (String) -> Unit,
    onCreateNewFolderClick: () -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onToggleSystemApps: () -> Unit,
    onExportSelectedApps: () -> Unit,
    onRestoreTrashItem: (TrashItemEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var rubberBandRect by remember { mutableStateOf<Rect?>(null) }
    var listContainerRootOffset by remember { mutableStateOf(Offset.Zero) }

    val itemLocalBounds = remember { mutableStateMapOf<String, Rect>() }
    val filesByPath = remember(state.files) { state.files.associateBy { it.path } }

    val filteredFiles = remember(state.files, state.searchQuery) {
        if (state.searchQuery.isBlank()) state.files
        else state.files.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
    }

    val groupedByMonth = remember(filteredFiles) {
        val fmt = SimpleDateFormat("yyyy年 M月", Locale.getDefault())
        filteredFiles.groupBy { fmt.format(Date(it.lastModified)) }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onFocusPane),
        color = MaterialTheme.colorScheme.surface,
        border = if (isFocused) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 頂部麵包屑與工具列
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateUp, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一層", modifier = Modifier.size(18.dp))
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.mountName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        if (state.mode == PaneMode.FILES) {
                            val parts = state.currentPath.split('/').filter { it.isNotEmpty() }
                            var acc = ""
                            parts.forEach { seg ->
                                acc += "/$seg"
                                val target = acc
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = seg,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clickable { onNavigateToPath(target) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showSearchBar = !showSearchBar }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "搜尋", modifier = Modifier.size(18.dp))
                    }
                    if (state.mode == PaneMode.FILES) {
                        IconButton(onClick = { onAddBookmark(state.currentPath) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "加入書籤", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onCollapsePane, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "摺疊此面板", modifier = Modifier.size(18.dp))
                    }
                }

                if (showSearchBar) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("搜尋目前目錄檔案...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 進入選擇狀態時，於上方區域顯示「全選」勾選選項與取消鈕
                if ((state.isMultiSelectMode || state.selectedPaths.isNotEmpty()) &&
                    (state.mode == PaneMode.FILES || state.mode == PaneMode.CATEGORY)
                ) {
                    val allSelected = filteredFiles.isNotEmpty() &&
                        filteredFiles.all { it.path in state.selectedPaths }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            .clickable {
                                onFocusPane()
                                if (allSelected) onClearSelection() else onSelectAll()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { checked ->
                                    onFocusPane()
                                    if (checked) onSelectAll() else onClearSelection()
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "全選 (${state.selectedPaths.size}/${filteredFiles.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消選取",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 主體內容區
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            listContainerRootOffset = coords.positionInRoot()
                        }
                        .dxMouseHandler(
                            onRightClick = { localPos ->
                                onFocusPane()
                                val hitPath = itemLocalBounds.entries.firstOrNull { (_, r) -> r.contains(localPos) }?.key
                                onShowContextMenu(hitPath?.let { filesByPath[it] })
                            },
                            onRubberBandStart = { onFocusPane() },
                            onRubberBandUpdate = { rect ->
                                rubberBandRect = rect
                                val hits = itemLocalBounds.entries
                                    .filter { (_, bounds) -> rect.overlaps(bounds) }
                                    .map { it.key }
                                    .toSet()
                                onRubberBandSelect(hits)
                            },
                            onRubberBandEnd = { rubberBandRect = null },
                            onCtrlWheelZoom = onCtrlWheelZoom,
                            onMouseBack = onNavigateBack,
                            onMouseForward = onNavigateForward
                        )
                        .dxFileTouchGesture(
                            findItemAtPosition = { localPos ->
                                itemLocalBounds.entries.firstOrNull { (_, rect) -> rect.contains(localPos) }?.key
                            },
                            onLongPressStationary = { filePath, _ ->
                                onFocusPane()
                                onShowContextMenu(filesByPath[filePath])
                            },
                            onEmptySpaceLongPress = {
                                if (state.mode == PaneMode.FILES) {
                                    onFocusPane()
                                    onCreateNewFolderClick()
                                }
                            },
                            onStartDragSelection = { initialPath, localOffset ->
                                onFocusPane()
                                val file = filesByPath[initialPath] ?: return@dxFileTouchGesture
                                dragController.startDrag(
                                    paneIndex = state.paneIndex,
                                    initialFile = file,
                                    rootOffset = listContainerRootOffset + localOffset
                                )
                            },
                            onSecondFingerTapItem = { tappedPath, localOffset ->
                                val file = filesByPath[tappedPath] ?: return@dxFileTouchGesture
                                dragController.addSecondFingerFile(
                                    file = file,
                                    tapRootOffset = listContainerRootOffset + localOffset
                                )
                            },
                            onPrimaryFingerDrag = { localOffset, hoveredPath ->
                                val hoveredFolder = hoveredPath?.takeIf { filesByPath[it]?.isDirectory == true }
                                dragController.updateDrag(
                                    rootOffset = listContainerRootOffset + localOffset,
                                    hoveredFolder = hoveredFolder
                                )
                            },
                            onDropComplete = { _, targetFolderPath ->
                                val targetDir = if (targetFolderPath != null && filesByPath[targetFolderPath]?.isDirectory == true) {
                                    targetFolderPath
                                } else {
                                    state.currentPath
                                }
                                dragController.finishDrag(
                                    targetPaneIndex = state.paneIndex,
                                    targetMountId = state.mountId,
                                    targetDirPath = targetDir
                                )
                            }
                        )
                ) {
                    when {
                        state.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        state.errorMessage != null -> {
                            Text(
                                text = "載入錯誤：${state.errorMessage}",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center).padding(16.dp)
                            )
                        }
                        state.mode == PaneMode.APPS -> {
                            AppManagerList(
                                apps = installedApps,
                                includeSystemApps = includeSystemApps,
                                selectedPackages = selectedAppPackages,
                                onToggleSystemApps = onToggleSystemApps,
                                onToggleSelect = onToggleAppSelection,
                                onExportSelected = onExportSelectedApps
                            )
                        }
                        state.mode == PaneMode.TRASH -> {
                            TrashBinList(
                                items = trashItems,
                                onRestore = onRestoreTrashItem,
                                onEmptyTrash = onEmptyTrash
                            )
                        }
                        filteredFiles.isEmpty() -> {
                            Text(
                                text = "此資料夾沒有檔案",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                groupedByMonth.forEach { (monthLabel, monthFiles) ->
                                    item(key = "header_$monthLabel") {
                                        Text(
                                            text = monthLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                    items(monthFiles, key = { it.path }) { file ->
                                        val isSelected = file.path in state.selectedPaths
                                        val isHoveredTarget = dragController.isDragging &&
                                            file.isDirectory &&
                                            dragController.hoveredFolderPath == file.path
                                        val hoverScale by animateFloatAsState(
                                            targetValue = if (isHoveredTarget) 1.15f else 1f,
                                            label = "FolderHoverScale"
                                        )

                                        FileRowItem(
                                            file = file,
                                            isSelected = isSelected,
                                            isMultiSelectMode = state.isMultiSelectMode,
                                            isHoveredTarget = isHoveredTarget,
                                            iconScale = state.iconScale,
                                            modifier = Modifier
                                                .scale(hoverScale)
                                                .onGloballyPositioned { coords ->
                                                    val b = coords.boundsInParent()
                                                    itemLocalBounds[file.path] = b
                                                }
                                                .clickable {
                                                    onFocusPane()
                                                    if (state.isMultiSelectMode) {
                                                        onToggleSelect(file.path)
                                                    } else if (file.isDirectory) {
                                                        onNavigateToPath(file.path)
                                                    } else {
                                                        onOpenFile(file)
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 滑鼠拉框選取視覺層
                    RubberBandOverlay(rubberBandRect)
                }
            }

            if (state.mode == PaneMode.FILES) {
                FloatingActionButton(
                    onClick = {
                        onFocusPane()
                        onCreateNewFolderClick()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新增資料夾")
                }
            }
        }
    }
}

@Composable
private fun FileRowItem(
    file: VfsFile,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isHoveredTarget: Boolean,
    iconScale: Float,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isHoveredTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .then(
                if (isHoveredTarget) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode || isSelected) {
            Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((24 * iconScale).dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (file.isDirectory) "資料夾" else formatFileSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppManagerList(
    apps: List<InstalledAppInfo>,
    includeSystemApps: Boolean,
    selectedPackages: Set<String>,
    onToggleSystemApps: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onExportSelected: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = includeSystemApps,
                onClick = onToggleSystemApps,
                label = { Text("顯示系統應用") }
            )
            Button(
                enabled = selectedPackages.isNotEmpty(),
                onClick = onExportSelected
            ) {
                Text("匯出 APK (${selectedPackages.size})")
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                val checked = app.packageName in selectedPackages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSelect(app.packageName) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggleSelect(app.packageName) })
                    Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${app.packageName} • v${app.versionName} • ${formatFileSize(app.apkSizeBytes)}${if (app.hasSplitApks) " (Split)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashBinList(
    items: List<TrashItemEntity>,
    onRestore: (TrashItemEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("垃圾桶項目（超過 30 天自動清除）", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(enabled = items.isNotEmpty(), onClick = onEmptyTrash) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空")
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.originalName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "原路徑：${item.originalPath} • ${formatFileSize(item.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRestore(item) }) {
                        Icon(Icons.Default.RestoreFromTrash, contentDescription = "還原", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
